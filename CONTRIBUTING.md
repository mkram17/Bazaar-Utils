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

### Pointing the mod at a local website

Website sync and `/bu link` talk to `https://bazaarutils.dev` by default. To work against a local
website instead, put a `.env` at the repo root:

```
BAZAARUTILS_API_URL=http://localhost:3000
```

The Gradle run configs load it into the dev client's environment. `.env` is gitignored, and if you
already have IDE run configs you will need to re-sync Gradle so they are regenerated with the
variable. Note that the file only works because Gradle loads it — `System.getenv` does not read
`.env` on its own, so this has no effect on a jar launched from the Minecraft Launcher. For that,
pass `-Dbazaarutils.apiUrl=http://localhost:3000` as a JVM argument instead.

### Changing what the mod sends to the website

The order-sync and link payloads are duplicated by necessity: this repo declares them as Java
records and constants, the [website](https://github.com/mkram17/Bazaar-Utils-Website) as Zod
schemas, and neither can import the other. `contract/wire-format.json` is the written-down copy of
the values that must agree — enum names, string caps, the order ceiling, and link-code
normalization. The same file lives in the website repo, where a test holds its schemas to it.

If you change any of them, update the JSON in both repos and the website schema in the same
change. `normalizeLinkCode` deserves particular care: the normalized code *is* the `serverId`
nonce the website passes to Mojang's `hasJoined`, so a divergence raises no error — it silently
turns every link attempt into a failed verification.

If you are unsure of what to do, please see [SkyHanni's contributing guide](https://github.com/hannibal002/SkyHanni/blob/beta/CONTRIBUTING.md), or if you have a more specific question, you can ask in the [Discord server](https://discord.gg/xDKjvm5hQd).
