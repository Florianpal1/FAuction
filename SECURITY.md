# Security Policy

## Supported versions

Only the latest release line receives security fixes. Always check the
[Releases page](https://github.com/Florianpal1/FAuction/releases/latest) before reporting.

| Version | Supported          |
|---------|--------------------|
| 2.0.x   | :white_check_mark: |
| < 2.0   | :x:                |

## Reporting a vulnerability

**Please do not open a public issue for security vulnerabilities.**

FAuction handles player items and server economy. A publicly disclosed duplication or
economy exploit can be abused on every server running the plugin within minutes, long
before a fix is released and installed.

Instead, report it privately through
[GitHub's private vulnerability reporting](https://github.com/Florianpal1/FAuction/security/advisories/new).
If you cannot use that form, contact the maintainer privately on
[Discord](https://discord.com/invite/dEvR34ZzYQ) and ask for a private channel — do not
post details in a public channel.

Please include, as far as you can:

- The FAuction version and the server version (`/version`).
- Reproduction steps, or the packet/command sequence involved.
- The impact: item duplication, money creation, bypassing a permission, data loss, ...
- Whether the exploit needs a specific configuration (currency mode, database type,
  `duplication-hashcode-control`, `securityForSpammingPacket`, ...).

You will get an acknowledgement as soon as possible. Once a fix is released, you will be
credited in the release notes and the advisory unless you prefer to stay anonymous.

## In scope

- Item duplication or item loss caused by the plugin.
- Creating, deleting, or transferring money outside of a legitimate transaction.
- Bypassing a `fauction.*` permission, the price limits, the listing limits, or the blacklist.
- SQL injection or any unsafe handling of player-controlled input.
- Crashing or freezing the server through auction GUIs, commands, or packet abuse.
- Leaking data belonging to other players.

## Out of scope

- Server or plugin misconfiguration (for example granting `fauction.admin` to everyone,
  or a permission plugin misconfigured to give `op` defaults to all players).
- Vulnerabilities in the server software or in third-party plugins (Vault, PlaceholderAPI,
  LuckPerms, economy plugins). Report those to their respective maintainers.
- Issues that only occur on unsupported, modified, or cracked server builds.
- Bugs with no security impact — use the
  [bug report template](https://github.com/Florianpal1/FAuction/issues/new?template=bug_report.yml).
