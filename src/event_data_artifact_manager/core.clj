(ns event-data-artifact-manager.core
  (:require [config.core :refer [env]]
            [clojure.data.json :as json]
            [event-data-common.storage.s3 :as s3]
            [event-data-common.storage.store :as store]
            [clj-time.core :as clj-time]
            [clj-time.coerce :as clj-time-coerce])
  (:import [java.io File]
           [com.amazonaws.services.cloudfront AmazonCloudFrontClient]
           [com.amazonaws.services.cloudfront.model CreateInvalidationRequest Paths InvalidationBatch]
           [com.amazonaws.auth BasicAWSCredentials]
           [java.net URLEncoder])
  (:gen-class))

(def connection
  (delay
    (s3/build (:s3-key env) (:s3-secret env) (:s3-region-name env) (:s3-bucket-name env))))

(def prefix "a/")

(defn generate-date-stamp
  []
  (str (clj-time-coerce/to-long (clj-time/now))))

(defn list-artifacts
  "List all artifacts. 
   Return list of artifact names."
  []
  (let [artifact-names (s3/list-with-delimiter @connection prefix "/")]
    ; Drop leading prefix and trailing slash.
    (map #(.substring % (.length prefix) (dec (.length %))) artifact-names)))

(defn upload-artifact
  "Upload an artifact with a version timestamp."
  [artifact-name filename]
  (let [date-stamp (generate-date-stamp)
        registry-path (str "a/" artifact-name "/versions/" date-stamp)
        client (:client @connection)
        f (new File filename)]

    (when-not (.exists f)
      (println "Error file " filename " doesn't exist!")
      (System/exit 1))

    (println "Uploading to" registry-path)
    (.putObject client (:s3-bucket-name env) registry-path f)
    (println "Done uploading artifact.")))

(defn list-versions-for-artifact
  "Return all version labels of artifact, in order, oldes first."
  [artifact-name]
  (let [artifact-base-key (str prefix artifact-name "/versions/")
        artifact-keys (store/keys-matching-prefix @connection artifact-base-key)
        versions (map #(.substring % (.length artifact-base-key)) artifact-keys)]
    versions))

(defn latest-version-for-artifact
  "Return the latest version label for the artifact."
  [artifact-name]
  ; This is done by iterating all the way to end.
  ; This won't be too expensive because the page-size is 1,000.
  (last (list-versions-for-artifact artifact-name)))

(defn latest-versions-of-all-artifacts
  "Return map of artifact id to latest version label for all artifacts."
  []
  (into {} (map #(vector % (latest-version-for-artifact %)) (list-artifacts))))

(defn invalidate-cloudfront
  "Invalidate indexes in Cloudfront Distribution"
  []
  (println "Sending Invalidation...")
  (let [artifact-index (str "/" prefix "artifacts.json")
        version-indexes (map #(str "/" prefix % "/versions.json") (list-artifacts))

        ; all-paths (map #(URLEncoder/encode % "UTF-8") (concat [artifact-index] version-indexes))
        all-paths (concat [artifact-index] version-indexes)

        client (new AmazonCloudFrontClient (new BasicAWSCredentials (:s3-key env) (:s3-secret env)))
        paths (-> (new Paths) (.withItems all-paths) (.withQuantity (count all-paths)))
        batch (new InvalidationBatch paths (generate-date-stamp))
        request (new CreateInvalidationRequest (:cloudfront-distribution-id env) batch)
        invalidation (.createInvalidation client request)]  
  (println "Done sending Invalidation...")))

(defn main-update-artifact-index
  "Update the artifact list index."
  []
  (println "Updating artifact index...")
  (let [artifacts-versions (latest-versions-of-all-artifacts)
        structure (into {} (map (fn [[artifact version]]
                                 [artifact
                                  {:name artifact
                                   :current-version version
                                   :versions-link (str (:public-base env) "/" prefix artifact "/versions.json")
                                   :current-version-link (str (:public-base env) "/" prefix artifact "/versions/" version)}]) artifacts-versions))]
    
    (store/set-string @connection (str prefix "artifacts.json") (json/write-str structure))

    (doseq [[artifact _] artifacts-versions]
      (let [versions (list-versions-for-artifact artifact)
            structure (map #(hash-map
                              :version %
                              :version-link (str (:public-base env) "/" prefix artifact "/versions/" %))
                            versions)
            k (str prefix artifact "/versions.json")]

        (store/set-string @connection k (json/write-str structure)))))
  (println "Done updating artifact index...")
  (invalidate-cloudfront)) 

(defn main-list-artifacts
  []
  (let [artifacts (list-artifacts)]
    (doseq [artifact artifacts]
      (println artifact))))

(defn main-upload-artifact
  [artifact-name filename]
  (upload-artifact artifact-name filename)
  (main-update-artifact-index))

(defn main-list-artifact-versions
  [artifact-name]
  (doseq [version (list-versions-for-artifact artifact-name)]
    (println version)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [command (first args)]
    (condp = command
      "list" (main-list-artifacts)
      "versions" (main-list-artifact-versions (nth args 1))
      "upload" (main-upload-artifact (nth args 1) (nth args 2))
      "update" (main-update-artifact-index)
      "invalidate" (invalidate-cloudfront)
      (println "Unrecognised command"))))
