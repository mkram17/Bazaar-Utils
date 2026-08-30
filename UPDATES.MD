# Bazaar Utils v1.0.0

The first full release of Bazaar Utils. Almost every part of the mod has been rebuilt — the config, the
settings screen, the button system, order tracking, and how the mod reads item data. Everything you had
before is still here, most of it does more than it used to, and there is a lot that's new.

---

## ⚠️ Please read before updating

**Your settings will not carry over.** v1.0.0 uses a completely new config system and saves to a new
location (`config/bazaarutils/` instead of `config/bazaarutils.json`). Your old settings, custom order
buttons, sell rules, and bookmarks will not be imported — you'll start from defaults and need to set
things up again. Your old file is left untouched, so nothing is destroyed.

**The required mods have changed.** You now need these in your mods folder:

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Resourceful Config](https://modrinth.com/mod/resourceful-config) *(new — replaces YACL)*
- [owo-lib](https://modrinth.com/mod/owo-lib) *(new)*
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) *(new)*

**Yet Another Config Library (YACL) is no longer used** and can be removed if nothing else needs it.
Mod Menu is still optional.

**Minecraft 1.21.11 only.** This release targets 1.21.11. If you're on 26.1.2 or 26.2, stay on 0.6.2
for now.

**Some commands were removed.** They've been replaced by proper config screens — see *Commands* below.

---

## ✨ New features

### Price Helpers
Buttons in the **price** sign screens, not just the amount ones. Add your own buttons to the Buy Order
and Sell Offer price screens and have them fill in a market-aware price for you: **Competitive**
(just beats the current best order), **Matched** (equal to it), or **Outbid** (just under it).

### Sell-side helpers
Sell Offers now get the same treatment buying always had — you can add amount buttons *and* price
buttons to sell offers, on top of the existing Instant Buy and Buy Order amount buttons.

### Cancel & Search button
A new button on an unfilled order's page that cancels the order and immediately re-searches that item,
in one click. Off by default; turn it on under **Buttons**.

### Instant Sell Highlight
Highlights the items in your inventory that the Bazaar's Instant Sell button would actually sell, so
you can see what's about to go before you click. Configurable colour and highlight style.

### Sell Sacks protection
Inventory restrictions used to only cover **Instant Sell**. They now cover **Sell Sacks** too, and each
rule can pick which of the two it applies to.

### Bazaar chat cleanup
Removes the throwaway messages the Bazaar spams while you interact with it — "Submitting buy order...",
"Claiming order...", "Putting goods in escrow...", and so on. You pick exactly which ones get hidden.

### Richer order notifications
**Order Filled** notifications now support everything **Outbid** notifications do. For each of the two,
you independently toggle a chat message, a client sound, and automatically opening the Bazaar.

### Highlight styles
Every highlight in the mod (order status, instant sell) can now be drawn as a **Background Fill**,
a **Foreground Tint**, or a **Border Outline**, with your own colours for each order status.

### Modify Indicator
Anything Bazaar Utils adds or changes in a menu is marked with a small **₿**, so you can always tell
what's the mod and what's Hypixel. Show it as a name prefix, a name suffix, the last line of the lore,
only on modified items, or turn it off entirely.

### Auto Update
The mod can now update itself when a new version is released, matching the release channel you're
already on (alpha/beta/stable). On by default; toggle it in **Advanced Settings**.

### Translations
The mod is now fully translated into **German, Polish, French, Simplified Chinese and Russian**, and
follows your Minecraft language setting.

### Per-profile data
Your tracked orders and daily Bazaar limit are now stored per SkyBlock profile, so switching profiles
shows the right numbers instead of mixing them together. Bookmarks stay shared across profiles.

---

## 🔧 Improvements

- **Custom buy amounts are now "Input Helpers"** and are configured in the settings screen instead of
  by command. Each button gets its own item, slot, and either a fixed amount or **Max** (the largest
  amount you can afford). Max is now just a setting on any button rather than a separate feature.
- **Sell rules are now "Inventory Restrictions"**, also configured in the settings screen. Price and
  volume rules and item-name rules are managed separately, and each rule chooses which buttons it locks.
- **Bookmark widgets now show on every Bazaar screen**, not just the main page.
- **The settings screen is completely new** — categorised, searchable, with in-game pickers for choosing
  a button's item and slot instead of typing numbers, plus hover explanations on every option.
- **Bazaar Flipper is now a setting, not a tax percentage.** Pick your actual upgrade tier (Not Upgraded /
  Bazaar Flipper I / II) and the mod works out your tax and order limit from it.
- **Item data comes from the SkyBlock item repository** instead of a hand-maintained list, so new items
  work without waiting for a mod update.
- **The Bazaar limit display** shows your daily 15,000,000,000 coin limit per profile with a reset button
  in the config.
- REI, Skyblocker and Firmament compatibility handling is still in place.

---

## 🐛 Fixes

- Fixed the `StackOverflowError` crash when running alongside SkyOcean or other Skyblock-API mods. The
  item-replacement code that caused it has been removed entirely and replaced with a system that plays
  nicely with other mods.
- Fixed crashes from missing order/bidding data ("Mining is null", null bidding type) — the code paths
  that produced them no longer exist.
- Fixed "failed to parse order data from chat" errors; chat parsing has been rewritten and is far more
  tolerant of unexpected messages.
- Fixed duplicate listener registration causing features to fire twice.
- Fixed inventory item counting used by sell restrictions.
- Fixed inventory highlights not applying in some menus.

---

## ⌨️ Commands

| Command | Status |
| --- | --- |
| `/bu` / `/bazaarutils` | Opens the settings |
| `/bu help` | Lists all commands |
| `/bu discord` | Discord invite |
| `/bu updateresources` | Check for and apply item data updates |
| `/bu orders list \| info \| outdated \| remove` | **New** — inspect your tracked orders |
| `/bu tax {percentage}` | **Removed** — set your Bazaar Flipper tier in the config instead |
| `/bu customorder add\|remove` | **Removed** — use **Buttons → Input Helpers** in the config |
| `/bu rule add\|remove` | **Removed** — use **Inventory → Inventory Restrictions** in the config |

`/bu customorders` and `/bu sellrestrictions` still exist and will point you at the right config page.

---

## 🙏 Thanks

Huge thanks to **@0xar-ds** for the architectural work behind this release, to **@KdGaming0** for the
SkyOcean crash fix, and to **@Krish Shah** for cleanup contributions.

Questions or problems? Join the [Discord](https://discord.gg/xDKjvm5hQd).
