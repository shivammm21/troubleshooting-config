# Redis hostname mismatch lab

The Compose file defines a Redis service named `redis`. The Spring Boot app
is configured with `spring.data.redis.host: localhost`.

That works on the host. Inside the `api` container, `localhost` is the API
itself, so Redis connections fail.

StackPilot should cite both files and suggest changing the host to `redis`.
