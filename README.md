# LoginRateLimiter

A Java example that enforces login throttling with a sliding-window policy.

## What this project contains

- `src/LoginAttemptService.java`: app-facing rate limiter interface.
- `src/InMemoryLoginAttemptService.java`: single-JVM implementation.
- `src/SharedLoginAttemptStore.java`: shared-store contract for distributed nodes.
- `src/InMemorySharedLoginAttemptStore.java`: in-memory shared store for local multi-node simulation.
- `src/DistributedLoginAttemptService.java`: distributed service that delegates state to a shared store.
- `src/RedisSharedLoginAttemptStore.java`: Redis-backed shared store with Lua-based atomic updates.
- `src/Main.java`: runnable demo for single-node and distributed scenarios.

## Policy

- Allow up to **5** failed attempts per user
- Count failures in a **10 minute** sliding window
- If failures exceed the threshold, block for **30 minutes**

## How it works

1. Each failure is timestamped.
2. Timestamps older than the sliding window are pruned.
3. If active failures exceed the threshold, the user is blocked.
4. A successful login clears prior failures and unblock state.

## Distributed mode notes

`DistributedLoginAttemptService` shares state via `SharedLoginAttemptStore`.

- For local simulation, use `InMemorySharedLoginAttemptStore`.
- For real multi-instance deployments, use `RedisSharedLoginAttemptStore`.
- Redis updates are atomic via a Lua script to avoid race conditions.

## Prerequisites

- Java 17+ (or the version configured in `pom.xml`)
- Maven 3.8+
- Redis (optional, only for Redis store demo)

## Run commands

### Build and run tests

```bash
mvn clean test
```

### Compile only

```bash
mvn clean compile
```

### Run the demo (`Main`)

```bash
mvn -q exec:java -Dexec.mainClass=Main
```

If `exec-maven-plugin` is not configured in your `pom.xml`, run:

```bash
mvn -q package
java -cp target/classes Main
```

### Start Redis with Docker (optional)

```bash
docker compose up -d
```

### Stop Redis

```bash
docker compose down
```





## Use this mental model:for your understanding 
isAllowed(userId) is called before password verification.
If allowed, app checks credentials.
If credentials fail -> call recordFailure(userId).
If credentials succeed -> call recordSuccess(userId).
## Extending this project

- Inject policy values (`maxFailures`, `window`, `blockDuration`) instead of fixed constants.
- Add integration tests for Redis Lua script behavior.
- Add metrics/logging around blocked attempts and resets.
