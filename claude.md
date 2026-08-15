## Project context

FAuction is a Minecraft Paper plugin (auction house) built with Maven, targeting Java 21.
Key dependencies: Paper API, ACF (aikar-commands), Hibernate + HikariCP, BoostedYaml, PlaceholderAPI, Vault, LuckPerms.
Main packages under `fr.florianpal.fauction`: `commands`, `configurations`, `enums`, `events`, `gui`, `languages`, `managers`, `objects`, `placeholders`, `queries`, `schedules`, `utils`.

## Environment

- Required version: Java 21
- Build: `mvn clean install` (CI runs `mvn -B package` on every push/PR to `master`, which also runs the tests)
- Tests: `mvn test`

## Unit tests

Write a JUnit 5 test for every addition or change to business logic, concurrency-sensitive code (claims, token buckets, schedules), and command/config managers — see `ClaimManagerTest`, `TokenBucketTest` as references. GUI wiring classes are covered more sparsely by convention; match that priority rather than chasing blanket coverage.

Follow the existing conventions:
- Reuse `FAuctionTestBase` (MockBukkit `ServerMock` + Mockito mocks of `FAuction`, `AuctionQueries`, `ExpireQueries`, `DatabaseConfig`, `GlobalConfig`) instead of re-wiring mocks per test.
- Name test classes `<Class>Test`; name test methods in camelCase describing the behavior under test (e.g. `secondClaimIsRefused`).
- Add `@DisplayName` with a human-readable description on each test.
- Comment only to explain *why* a test/assertion exists, not what it does.

## Sub-agent audit

For all structural code produced, trigger three review passes:

1. Functional audit: does the code do what it is supposed to do?
2. Quality audit: readability, duplications, adherence to conventions.
3. Security review: vulnerabilities, input validation, error handling.

## Security rules

- DB credentials live in `database.yml` (loaded via BoostedYaml into `DatabaseConfig`). Never commit real production credentials there — only placeholder/example values.
- Never hard-code API tokens, passwords, or secrets in the source code.
- Never log passwords, tokens, or DB credentials. Logging player names via the plugin logger is existing, accepted practice — don't extend logging to UUIDs or raw inventory/NBT data.

## Git (personal projects only)

No commits, no pushes.