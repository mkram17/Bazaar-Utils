# How to Contribute

In the pull request description, label what you did in the pr with feature, change, bug fix, etc. Proposed changes should be made in their own branch.
All new classes should be written in Java if possible; there are no plans to use Kotlin in the near future. Follow the formatting conventions below. Make sure to describe your changes in the UPDATES.md file if they are relevant to the user.

If you are working with the event system — subscribing to events, adding a module, or defining a new event — read [EVENTS_AND_HANDLERS.md](EVENTS_AND_HANDLERS.md) first for an overview of the SkyblockAPI event bus, the module annotation pipeline, and event predicates.

### Getting Started & Building
1. Fork and clone the repository.
2. Open the project in your IDE of choice (IntelliJ IDEA is recommended).
3. To build the project, run:
   ```bash
   ./gradlew build
   ```

If you are unsure of what to do, please see [SkyHanni's contributing guide](https://github.com/hannibal002/SkyHanni/blob/beta/CONTRIBUTING.md), or if you have a more specific question, you can ask in the [Discord server](https://discord.gg/xDKjvm5hQd).

### Formatting

Java formatting is defined by the pinned formatter configuration in
`formatting/build.gradle.kts`: four-space indentation and a 120-column layout.
`.editorconfig` supplies matching editor defaults, including UTF-8 and LF line endings;
`.gitattributes` also keeps Java checkouts on LF (Windows batch files retain CRLF).
The formatter covers handwritten main and test Java
sources plus `buildSrc` Java sources; generated source and build outputs are excluded.

With Java 25, run these commands from the repository root:

```bash
git fetch origin v1.0.0
./gradlew -p formatting spotlessCheck
./gradlew -p formatting spotlessApply
```

On Windows, use `.\gradlew.bat -p formatting spotlessCheck` and
`.\gradlew.bat -p formatting spotlessApply`.
`spotlessCheck` reports differences without editing files; `spotlessApply` fixes them.
Review and commit the resulting formatting changes with your work.

Only files changed since the merge base with `origin/v1.0.0` are formatted, including
staged, unstaged, and new files. An entire changed file is formatted, not just the edited
lines. Untouched files keep their existing formatting, so there is no repository-wide
cleanup. Keep the baseline up to date with `git fetch origin v1.0.0`. For a different
target branch, fetch it and pass `-PformattingBase=origin/your-target-branch` to either task.

Formatting uses a separate Gradle build so its plugins and checks are independent of
the mod's `build`, `check`, and unit tests. Pull requests get an advisory
formatting check against their target branch; failures appear in its log and summary but
do not fail the build workflow. No complexity, method-length, or other code-quality rules
are configured.

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
