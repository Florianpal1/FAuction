<!--
Thanks for contributing to FAuction!
Please fill in the sections below and delete the ones that do not apply.
Security fix? Do not describe the exploit here — see SECURITY.md first.
-->

## Description

<!-- What does this pull request change, and why? -->

## Related issue

<!-- e.g. Closes #123 / Part of #123. Put "none" if there is no issue. -->

## Type of change

- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change (config, permission, API, or database change servers must act on)
- [ ] Translation
- [ ] Documentation
- [ ] Refactor / maintenance

## How it was tested

<!-- Describe what you actually ran. Automated tests alone are fine for pure logic changes. -->

| | |
|---|---|
| Server version | <!-- e.g. Paper 1.21.4 --> |
| Java version | <!-- e.g. 21 --> |
| Database | <!-- SQLite / MySQL / MariaDB / PostgreSQL --> |
| Currency mode | <!-- Vault / Experience / Level --> |

## Checklist

- [ ] `mvn clean package` passes locally (tests included)
- [ ] I added or updated tests for the logic I changed, where it is testable
- [ ] New message keys were added to **all** language files (`lang_en`, `lang_fr`, `lang_ru`, `lang_zhcn`)
- [ ] New permissions are declared in `plugin.yml` with an explicit default and attached to the right parent
- [ ] I bumped the `version` key of any configuration file whose structure I changed
- [ ] The README (and Wiki, if relevant) is up to date with this change
- [ ] This change does not introduce a way to duplicate items or money
