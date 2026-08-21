# Contributing to FAuction

Thanks for your interest in improving FAuction! This document covers everything you need
to build the plugin, test your change, and get it merged.

- **Bug?** Use the [bug report template](https://github.com/Florianpal1/FAuction/issues/new?template=bug_report.yml).
- **Idea?** Use the [feature request template](https://github.com/Florianpal1/FAuction/issues/new?template=feature_request.yml).
- **Question?** Use [Discussions](https://github.com/Florianpal1/FAuction/discussions) or [Discord](https://discord.com/invite/dEvR34ZzYQ).
- **Security vulnerability?** Do not open an issue — follow the [security policy](SECURITY.md).

For anything larger than a bug fix, please open an issue first so we can agree on the
approach before you write code.

---

## Building

**Prerequisites:** Java 21+ and Maven 3.8+.

```bash
git clone https://github.com/Florianpal1/FAuction.git
cd FAuction
mvn clean package
```

The shaded plugin JAR lands in `target/fauction-<version>.jar`. Drop it into the
`plugins/` folder of a local Paper server to test it in game.

Run the test suite on its own with:

```bash
mvn test
```

Tests use JUnit and Mockito (Mockito runs as a Java agent, configured in `pom.xml` —
run tests through Maven rather than invoking JUnit directly).

---

## Project layout

| Path                              | Contents                                                     |
|-----------------------------------|--------------------------------------------------------------|
| `src/main/java/.../commands`      | ACF command classes (`/ah` and its subcommands)              |
| `src/main/java/.../configurations`| Typed wrappers around the YAML configuration files           |
| `src/main/java/.../managers`      | Configuration, GUI, and database managers                    |
| `src/main/java/.../objects`       | Domain objects (auctions, expired items, history entries)    |
| `src/main/java/.../schedules`     | Scheduled tasks (cache refresh, expiration, currency checks) |
| `src/main/java/.../utils`         | Formatting and helper utilities                              |
| `src/main/resources`              | `plugin.yml`, default configs, GUI files, language files     |
| `src/test/java`                   | Unit tests (extend `FAuctionTestBase` where useful)          |

---

## Making a change

### Code style

Match the surrounding code — there is no enforced formatter. In particular:

- Java 21, 4-space indentation.
- Lombok is available (`provided` scope); prefer it over hand-written boilerplate when the
  neighbouring class already uses it.
- Keep database access inside the existing repository/manager layer instead of running
  queries from commands or GUI code.
- Never send raw player input into a query string; use the existing parameterised paths.

### Configuration changes

The config files are versioned with [boosted-yaml](https://github.com/dejvokep/boosted-yaml)
`BasicVersioning` on the `version` key. If you add, rename, or remove an option in
`config.yml` (or any versioned file), **increment its `version` key** so existing servers
get their file updated automatically on the next start. Also update the corresponding
section of the [README](README.md) and the [Wiki](https://github.com/Florianpal1/FAuction/wiki).

### Messages and translations

FAuction ships with `lang_en.yml`, `lang_fr.yml`, `lang_ru.yml`, and `lang_zhcn.yml`
(selected by the `lang` key in `config.yml`).

- When you add a new message key, add it to **all four** language files. If you cannot
  translate it, copy the English string — a missing key is worse than an untranslated one.
- New translations are very welcome: copy `lang_en.yml` to `lang_<code>.yml`, translate the
  values, and mention the language in your pull request.

### Permissions

Every new permission must be declared in `plugin.yml` with an explicit `default`, attached
to the right parent (`fauction.user` or `fauction.admin`) when it belongs to one, and
documented in the README permissions table.

---

## Pull requests

1. Fork the repository and create a branch off `master`.
2. Keep the pull request focused on one change — separate refactors from behaviour changes.
3. Make sure `mvn clean package` passes (this runs the tests).
4. Add or update tests when you change logic that can be unit tested.
5. Fill in the pull request template, including the server version you tested on.
6. CI must be green before a review.

Commit messages are free-form, but a short imperative summary helps
(`Fix expired items not returning to the inbox`).

---

## Releases

Maintainer notes: releases are cut by pushing a `v<version>` tag matching the `pom.xml`
version. The `Release` workflow checks that the tag and pom agree, builds the plugin,
publishes it to GitHub Packages, and attaches the JAR to the GitHub Release. Release notes
are generated from pull request labels (see `.github/release.yml`), so label your PRs.
