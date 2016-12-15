# Event Data Artifact Manager

Management for the Artifact Registry. A lightweight tool for uploading and updating the Artifact Registry.

The Artifact Registry answers questions like:

 - list all the artifacts
 - what is the most recent version of this artifact
 - list all versions of this artifact

The Artifact Registry is a static directory structure of files, and in principle can be served from anywhere, though it takes advantage of the S3 API. It is currently stored and served from AWS S3.

This tool provides:

 - add artifact version (with automatic timestamp)
 - re-create indexes

## Structure of Artifact Registry

The Artifact Registry working data is all stored on S3. 

Directory structure

    /a                  
      artifacts.json          List of all artifacts and their latest versions.
      /«artifact-id»     
        /versions.json        List versions of Artifact.
        /versions/       
          «version-id»   Get version of Artifact.

The following files are automatically generated when a re-index operation is triggered:

 - `/a/artifacts`
 - `/a/versions`
 - `/a/current`

These are derived by issuing 'list' operations on the S3 API.

## Commands

Set the config environment variables before running.

  - `lein run list` - list all artifact names
  - `lein run versions «artifact name»` - list all version labels of the named artifact, latest last
  - `lein run upload «artifact name» «path to file»` - upload a new version of an artifact (and rebuild indexes)
  - `lein run update` - rebuild all indexes (including sending invalidation to CloudFront cache)
  - `lein run invalidate` - force invalidation of CloudFront cache.

The `upload` command is, by any measure, the best command. It has the effect of uploading the latest version of an artifact ('creating' it if new), and rebuilding indexes and invalidating CloudFront.

## Configuration

| Environment variable         | Description                         |
|------------------------------|-------------------------------------|
| `S3_KEY`                     | AWS Key Id                          |
| `S3_SECRET`                  | AWS Secret Key                      |
| `S3_BUCKET_NAME`             | AWS S3 bucket name                  |
| `S3_REGION_NAME`             | AWS S3 bucket region name           |
| `PUBLIC_BASE`                | Public URL base of server           |
| `CLOUDFRONT_DISTRIBUTION_ID` | AWS Cloudfront Distribution ID |


`PUBLIC_BASE` should be base URL from which files are served, for example `https://artifact.eventdata.crossref.org`. It is used when constructing URLs in indexes.

It is assumed that there is a CloudFront distribution sitting in front of the S3 bucket for cache and HTTPS termination.

## License

Copyright © Crossref

Distributed under the The MIT License (MIT).
