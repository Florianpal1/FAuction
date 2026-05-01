# FAuction

FAuction is a modern Minecraft auction house plugin for Paper servers. It provides an intuitive GUI, database-backed auction storage, flexible pricing, categories, and optional integrations for Vault, PlaceholderAPI, and LuckPerms.

## Features

- Auction house command alias: `/ah` or `/hdv`
- Browse auctions with a built-in GUI
- Sell items from the main hand with price validation
- Search auctions by material
- Expiration and history management
- SQLite and full SQL database support
- PlaceholderAPI support for in-game placeholders
- Optional Vault/Tokens and LuckPerms integration
- Cached auction data and scheduled background tasks
- Multi-language support and menu customization

## Requirements

- Minecraft Paper 1.13+ server
- Java 21 runtime
- `Vault` plugin for economy integration (optional)
- `PlaceholderAPI` for placeholders (optional)

## Installation

1. Build the plugin:

```bash
mvn clean package
```

2. Copy the generated JAR from `target/` into your server `plugins/` folder.
3. Start the server once to generate the config files.
4. Configure `config.yml`, `database.yml`, `categories.yml`, and language files in `plugins/FAuction/`.
5. Restart the server.

## Configuration

The plugin stores settings in the following resource files:

- `config.yml`
- `database.yml`
- `categories.yml`
- `sort.yml`
- `lang_en.yml`, `lang_fr.yml`, `lang_ru.yml`, `lang_zhcn.yml`
- GUI definitions under `gui/`

Use `config.yml` to enable features such as expiration, cache updates, and custom GUI defaults.

## Usage

Basic auction commands:

- `/ah list` - Open the auction house GUI
- `/ah search <material>` - Search auctions by item type
- `/ah sell <price>` - Sell the item in your main hand

Command alias examples:

- `/ah list`
- `/hdv list`
- `/ah sell 10.0`
- `/ah search DIAMOND_SWORD`

## Permissions

Default permission node:

- `fauction.user`

Sub-permissions include:

- `fauction.list`
- `fauction.sell`
- `fauction.expire`

## Development

This repository is built with Maven. Key project settings:

- Java 21
- Paper API
- `maven-shade-plugin` for packaging
- `co.aikar:acf-paper` for command handling
- `org.bstats:bstats-bukkit` for metrics

To build locally:

```bash
mvn clean package
```

## Support

- [Wiki](https://github.com/Florianpal1/FAuction/wiki)
- [Discord](https://discord.com/invite/dEvR34ZzYQ)
- [Spigot](https://www.spigotmc.org/resources/fauction-auction-plugin-with-full-bungeecord-support.108552/)
- [Hangar](https://hangar.papermc.io/Florianpal/FAuction)
- [Modrinth](https://modrinth.com/plugin/fauction)
- [BStats](https://bstats.org/plugin/bukkit/FAuction/24018)

## License

See `LICENCE` for license details.
