(defproject event-data-artifact-manager "0.1.0-SNAPSHOT"
  :description "Tool for managing the Artifact Registry for Event Data."
  :url "http://eventdata.crossref.org"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [event-data-common "0.1.60"]
                 [yogthos/config "0.8"]
                 [clj-time "0.12.2"]]
  :main ^:skip-aot event-data-artifact-manager.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
