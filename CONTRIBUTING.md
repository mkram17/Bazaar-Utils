# How to Contribute

In the pull request description, label what you did in the pr with feature, change, bug fix, etc. Proposed changes should be made in their own branch.
All new classes should be written in Java if possible; there are no plans to use Kotlin in the near future. Follow coding conventions described [here](https://github.com/hannibal002/SkyHanni/blob/beta/CONTRIBUTING.md#coding-styles-and-conventions). Make sure to describe your changes in the UPDATES.md file if they are relevant to the user.

If you are working with the event system — subscribing to events, adding a module, or defining a new event — read [EVENTS_AND_HANDLERS.md](EVENTS_AND_HANDLERS.md) first for an overview of the SkyblockAPI event bus, the module annotation pipeline, and event predicates.

### Getting Started & Building
1. Fork and clone the repository.
2. Open the project in your IDE of choice (IntelliJ IDEA is recommended).
3. To build the project, run:
   ```bash
   ./gradlew build
   ```

If you are unsure of what to do, please see [SkyHanni's contributing guide](https://github.com/hannibal002/SkyHanni/blob/beta/CONTRIBUTING.md), or if you have a more specific question, you can ask in the [Discord server](https://discord.gg/xDKjvm5hQd).

### Unit tests

Run the focused suite with Java 25:

```bash
./gradlew :26.2:test
```

On Windows, use `gradlew.bat :26.2:test`. The normal `build` task also runs these tests.
The HTML report is in `versions/26.2/build/reports/tests/test/index.html`.

Tests live under `src/test/java` and cover restriction rules, safety clicks, order matching,
market price helpers, sell-lore parsing, and config patches. JUnit initializes Minecraft's
registries without opening a client or world. Mockito isolates the market API, listener
registration, screen refreshes, and notifications while the decision logic runs unchanged.
Lore fixtures use real text and data components on a mocked item stack; Minecraft 26.2
loads item defaults from data packs, which these parsing tests do not need.

Keep cases focused on meaningful behavior and boundaries. Add a representative lore fixture
when a new supported format is discovered; the existing fixtures do not detect live Hypixel
format changes.
