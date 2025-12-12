# pittsburgh-in-progress-backend

This repository contains the backend workers that make up the Pittsburgh in Progress ingest pipeline.

## Modules

- **doc-discover-worker** – Discovers new documents and publishes discovery requests.
- **ingest-api** – Shared DTOs and Pub/Sub envelope handling used by the workers.
- **pdf-fetch-worker** – Downloads PDFs from external URLs, uploads them to GCS, and publishes `pdf-stored` events.
- **pdf-parse-worker** – Subscribes to `pdf-stored` events, parses stored PDFs (text, metadata), and publishes `project-candidate` events.

## Environment variables

Most workers require the Google Cloud project ID and topic/bucket names:

- `pip.project-id` – GCP project that hosts Pub/Sub topics.
- `pip.pdf-bucket` – GCS bucket where PDFs are stored (used by `pdf-fetch-worker`).
- `pip.pdf-stored-topic` – Pub/Sub topic for payloads emitted after a PDF is stored.
- `pip.project-candidate-topic` – Pub/Sub topic that receives parsed PDF metadata from `pdf-parse-worker`.

Configure these via Cloud Run service settings or Maven `-D` system properties when running locally.

## Testing

Run the shared API tests first, then each worker module:

```
mvn -f ingest-api/pom.xml install -Dpip.project-id=test
mvn -f pdf-fetch-worker/pom.xml test -Dpip.project-id=test
mvn -f pdf-parse-worker/pom.xml test -Dpip.project-id=test -Dpip.project-candidate-topic=pgh-project-candidate
```

The `ingest-api` build produces a local JAR consumed by the worker modules during compile/test runs.
