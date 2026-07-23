# Bazaar Utils

[![Discord](https://img.shields.io/discord/1337164726802579597?style=flat&label=discord&color=red)](https://discord.gg/xDKjvm5hQd)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/c4u7nzUZ?label=modrinth%20downloads&color=red)](https://modrinth.com/mod/bazaar-utils)
[![GitHub Release](https://img.shields.io/github/v/release/mkram17/Bazaar-Utils?style=flat&logoColor=green&color=red)](https://github.com/mkram17/Bazaar-Utils/releases)
![GitHub License](https://img.shields.io/badge/license-CC--BY--NC--4.0-red?style=flat)


A Fabric client-side QoL mod for the Hypixel SkyBlock Bazaar.

## Features

### Buttons & Input Helpers

- **Amount Helpers**: Add custom quick-fill buttons to the Instant Buy, Buy Order, and Sell Offer amount screens for your preferred amounts.
- **Price Helpers**: Add custom quick-fill buttons to the Buy Order and Sell Offer price screens with market-aware pricing (e.g. competitive pricing that slightly beats the current best order).
- **Flip Helper**: A one-click price helper in the flip GUI to quickly flip an order at market-adjusted pricing.
- **Cancel Order & Search**: Adds a button to an unfilled order's settings page that cancels it and re-searches the item in one click.
- **Open Settings / Open Orders Buttons**: Quick-access widget buttons on Bazaar screens to open the mod's settings or jump straight to your orders page (Open Orders requires an active Booster Cookie).

### Bookmarks

- **Bookmarks**: Save favorite Bazaar items and quickly search them again from the main Bazaar screen via a dedicated bookmark button and search widget. Bookmarks can be reset from the config.

### Inventory

- **Restriction Rules**: Lock the **Instant Sell** and **Sell Sacks** buttons behind multi-click confirmation (configurable click count) using numeric rules (e.g. total price or item count) and item-name rules, to prevent accidental market actions.
- **Instant Sell Highlight**: Highlights the items in your inventory that would be sold by the Bazaar's Instant Sell button, with a configurable color.
- **Order Status Highlight**: Colors your Bazaar orders by status — competitive (best offer on the market), matched (equal to market price), or outbid (below market price) — with configurable colors.

### Overlays

- **Price Charts**: Adds a tooltip hint to Bazaar items; `Ctrl + Shift + Click` an item to open its market charts on skyblock.finance. Can optionally show on items outside the Bazaar too.
- **Bazaar Limits Visualizer**: Shows the status of your daily Bazaar limit (15 billion coins of orders/offers per profile per day) directly on Bazaar screens, with a reset button in the config.

### Notifications

- **Order Outbid Notifications**: Get notified when one of your orders or offers is outbid.
- **Order Filled Notifications**: Get notified when one of your orders or offers is filled.
- Each notification type can independently emit a chat message, play a client sound, and/or automatically open the Bazaar.

### Chat

- **Bazaar Message Cleanup**: Remove the transient chat messages the Bazaar sends while interacting with it (e.g. "Submitting buy order...", "Claiming order...", "Putting goods in escrow..."), with a configurable list of which messages to hide.
- **Stash Message Remover**: Optionally suppress the stash reminder chat messages.

### Keybinds

- **Stash Helper**: Press a keybind (default `V`) to close the current GUI and pick up your stash.

### Other

- **Auto Update**: Automatically updates the mod when a new version is found (toggleable in Advanced Settings).
- **Bazaar Flipper Setting**: Set your Bazaar Flipper account upgrade tier in the config so tax calculations and order limits are accurate.
- **Mod Menu Integration**: Config accessible through Mod Menu, with mod-provided config widgets.
- **Developer Mode**: Optional developer tools, debug messages, and error-notification toggles.

## Compatibility

- **Minecraft**: `1.21.11` (current target)
- **Java**: `21+`
- **Fabric Loader**: `0.16.9+`
- **Current mod version**: `1.0.0`

## Using Bazaar Utils

- Open the config with `/bazaarutils` or `/bu`.
- If your Bazaar Flipper account upgrade is not level 1, set your tier in the config or tax calculations and order limits won't be accurate.

### Other Commands

- `/bu help` lists all available commands
- `/bu discord` opens a link to join the Discord server
- `/bu updateresources` checks for and applies resource updates

## Dependencies

### Required in your mods folder

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Resourceful Config](https://modrinth.com/mod/resourceful-config)
- [owo-lib](https://modrinth.com/mod/owo-lib)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)

### Optional

- [Mod Menu](https://modrinth.com/mod/modmenu)

### Bundled with Bazaar Utils

- Orbit Event System
- Mixin Constraints
- Hypixel API Transport/Core
- Gson Extras
- SkyBlock API
- libautoupdate
