# Bazaar Utils

[![Discord](https://img.shields.io/discord/1337164726802579597?style=flat&label=discord&color=red)](https://discord.gg/xDKjvm5hQd)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/c4u7nzUZ?label=modrinth%20downloads&color=red)](https://modrinth.com/mod/bazaar-utils)
[![GitHub Release](https://img.shields.io/github/v/release/mkram17/Bazaar-Utils?style=flat&logoColor=green&color=red)](https://github.com/mkram17/Bazaar-Utils/releases)
![GitHub License](https://img.shields.io/badge/license-CC--BY--NC--4.0-red?style=flat)


A Fabric client-side QoL mod for the Hypixel Bazaar.

## Features

- **Custom Orders**: Create quick-buy buttons in Buy Order and Insta Buy menus for your preferred amounts.
- **Insta Sell Rules**: Prevent accidental insta-sells with multi-click confirmation rules based on item name, volume, or price.
- **Flip Helper**: Adds a one-click helper button in the flip GUI to quickly flip at market-adjusted pricing.
- **Outdated Order Notifications**: Get chat alerts when an order is undercut, with clickable navigation back to the Bazaar.
- **Stash Helper**: Use a keybind (default `Alt + V`) to close GUIs and collect your stash.
- **Disable Stash Messages**: Optionally suppress stash reminder chat messages.
- **Bookmarks**: Save and quickly search favorite Bazaar items from the main Bazaar screen.
- **Price Charts**: Use `Ctrl + Shift + Click` on Bazaar items to open the item chart page.
- **Recent Fixes**: Includes fixes for order-data chat parsing issues (see `UPDATES.MD`).

## Compatibility

- **Minecraft**: `1.21.11` (current target)
- **Java**: `21`
- **Fabric Loader**: `0.17.2+`
- **Current mod version**: `0.6.2-beta.1`

## Using Bazaar Utils

- Open config with `/bazaarutils` or `/bu`.
- If your Bazaar Flipper account upgrade is not level 1, set your tax in the config or the mod won't work properly.

### Other Commands

- `/bu help`
- `/bu discord` opens a link to join the Discord server

## Dependencies

### Required in your mods folder

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Resourceful Config](https://modrinth.com/mod/resourceful-config)
- [owo-lib](https://modrinth.com/mod/owo-lib)

### Optional

- [Mod Menu](https://modrinth.com/mod/modmenu)

### Bundled with Bazaar Utils

- Orbit Event System
- Mixin Constraints
- Hypixel API Transport/Core
- Gson Extras
- owo Sentinel