# Kafka bootstrap mismatch lab

Compose advertises a `kafka` service. The Spring app bootstraps
`localhost:9092`, which is unreachable from the `api` container.
