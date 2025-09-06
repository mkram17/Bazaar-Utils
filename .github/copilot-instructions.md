# Bazaar Utils - Copilot Instructions

## Repository Overview

Bazaar Utils is a client-side Quality of Life mod for Hypixel Skyblock's Bazaar system. The mod enhances the Bazaar experience with features like custom order amounts, insta-sell restrictions, flip helper, outdated order notifications, item bookmarks, stash helper, and price charts integration.

**Project Type:** Minecraft Fabric mod  
**Target Game:** Minecraft versions 1.21.1, 1.21.4, 1.21.5, 1.21.6  
**Language:** Java 21  
**Build System:** Gradle with Stonecutter for multi-version support  
**Architecture:** Event-driven with custom build-time bytecode injection  
**Repository Size:** ~150 Java files, complex build configuration  

## Build Instructions & Common Issues

### Prerequisites
- Java 21 (required)
- Internet connection for dependency resolution
- Gradle wrapper (included in repository)

### Build Process
The build system uses Stonecutter for multi-version support and includes a custom build-time injection system:

```bash
# Make gradlew executable (if needed)
chmod +x ./gradlew

# Build all versions (CURRENTLY BROKEN - see issues below)
./gradlew stonecutterBuild

# Alternative: Build specific version
cd versions/1.21.6
../../gradlew build
```

### ⚠️ Known Build Issues

**CRITICAL:** The build currently fails due to fabric-loom dependency resolution:
- **Error:** `fabric-loom:1.10-SNAPSHOT` plugin not found
- **Root cause:** SNAPSHOT dependency from fabric-loom is not available in configured repositories
- **Workaround:** When making changes, focus on code analysis and validation rather than building

**Build Dependencies That May Fail:**
- fabric-loom plugin (SNAPSHOT version)
- Stonecutter multi-version configuration
- Custom build-time injection task

**Build Sequence (When Working):**
1. `compileJava` - Compile Java sources
2. `processInitAnnotations` - Custom ASM bytecode injection task
3. `classes` - Finalize class files
4. `build` - Create mod JAR files

### Development Build Commands
```bash
# Clean build (rarely needed)
./gradlew clean

# Compile only (faster iteration)
./gradlew compileJava

# Run client for testing (when dependencies work)
./gradlew runClient
```

## Project Architecture

### Core Components

**Main Class:** `com.github.mkram17.bazaarutils.BazaarUtils`
- Entry point implementing ClientModInitializer
- Initializes event bus, config, keybinds, and features

**Event System:** Uses Meteor's Orbit event library
- Central event bus: `BazaarUtils.EVENT_BUS`
- Custom events in `events/` package
- Listeners auto-register via `BUListener.getEventListeners()`

**Custom Build-Time Injection:**
- `@RunOnInit` - Methods called during mod initialization
- `@RegisterWidget` - Methods returning UI widgets
- ASM-based injection in `buildSrc/` directory
- Scans compiled classes and injects method calls

**Configuration:** YACL (Yet Another Config Library)
- Main config: `BUConfig` class
- GUI: `BUConfigGui`
- ModMenu integration: `BUModMenu`

### Key Features (by package)
- `features/` - Bookmark, CustomOrder, FlipHelper, StashHelper, etc.
- `mixin/` - Minecraft client hooks and accessors
- `utils/` - Utility classes, commands, resource management
- `config/` - Configuration system and GUI

### Data Flow
1. Minecraft events → Mixin hooks → Custom events
2. Custom events → Feature handlers → User actions
3. Configuration changes → YACL → Persistent storage

## Project Layout

### Root Directory
```
├── .github/workflows/          # CI/CD (build.yml, updateconversions.yml)
├── buildSrc/                   # Custom Gradle build logic and ASM injection
├── src/main/java/              # Main source code
├── src/main/resources/         # Assets, configs, fabric.mod.json
├── versions/                   # Stonecutter version-specific configs
├── build.gradle.kts           # Main build configuration
├── gradle.properties          # Gradle and mod properties
├── settings.gradle.kts        # Gradle settings with Stonecutter
├── stonecutter.gradle.kts     # Multi-version configuration
└── README.MD                   # Project documentation
```

### Source Structure
```
src/main/java/com/github/mkram17/bazaarutils/
├── BazaarUtils.java           # Main mod class
├── config/                    # YACL configuration system
├── data/                      # Data classes and holders
├── events/                    # Custom event definitions
├── features/                  # Core mod features
├── misc/                      # Utilities and compatibility
│   ├── autoregistration/      # @RunOnInit/@RegisterWidget annotations
│   └── widgets/               # Custom UI widgets
├── mixin/                     # Minecraft client hooks
└── utils/                     # Utility classes and helpers
```

### Configuration Files
- `fabric.mod.json` - Fabric mod metadata and dependencies
- `bazaarutils.mixins.json` - Mixin configuration
- `gradle.properties` - Build properties and mod version
- `versions/*/gradle.properties` - Version-specific dependencies

## Dependencies & Requirements

### Required Dependencies
- **Fabric API** - Core Fabric mod support
- **YACL (Yet Another Config Library)** - Configuration GUI
- **Java 21** - Runtime requirement

### Optional Dependencies
- **Amecs Reborn** - Enhanced keybinding support
- **ModMenu** - Mod configuration menu integration

### Included Libraries
- **Orbit Event System** - Custom event handling
- **Mixin Constraints** - Mixin development utilities
- **Hypixel API Core/Transport** - Bazaar data fetching
- **Project Lombok** - Code generation (getters, constructors)
- **Gson Extras** - JSON serialization utilities

## Validation & Testing

### Code Validation
Since the build system is currently broken, focus on:
- **Static Analysis:** Check code compiles logically
- **Dependency Validation:** Ensure imports resolve
- **Annotation Usage:** Verify @RunOnInit and @RegisterWidget correctly applied
- **Mixin Validation:** Check mixin target classes exist in Minecraft

### Manual Verification Steps
```bash
# Check syntax without building
find src -name "*.java" -exec javac -cp "path/to/minecraft-deps" {} \;

# Validate Gradle configuration
./gradlew tasks --dry-run

# Check resource files
cat src/main/resources/fabric.mod.json | jq '.'
```

### CI/CD Pipeline
- **GitHub Actions:** `.github/workflows/build.yml`
- **Java 21** on Ubuntu 22.04
- **Artifact Upload:** Mod JARs from `versions/*/build/libs`
- **Auto-conversions:** Updates bazaar data via `updateconversions.yml`

## Common Pitfalls & Workarounds

### Build Issues
- **fabric-loom SNAPSHOT not found:** Known issue, focus on code analysis
- **Stonecutter version conflicts:** Ensure version is set in `stonecutter.gradle.kts`
- **Gradle daemon issues:** Use `./gradlew --stop` and retry

### Development Issues
- **Missing @RunOnInit:** Methods won't auto-initialize - add annotation
- **Mixin not applying:** Check `mixins.json` and target class names
- **Config not saving:** Use `Util.scheduleConfigSave()` after changes
- **Event not firing:** Ensure listener is registered in `BUListener.getEventListeners()`

### Multi-Version Considerations
- **Active version:** Currently set to "1.21.5" in `stonecutter.gradle.kts`
- **Version-specific code:** Use Stonecutter preprocessor comments when needed
- **Dependency versions:** Check `versions/*/gradle.properties` for version-specific deps

## Contributing Workflow

### Branch Strategy
- **Target Branch:** `modern-dev` for new features (mentioned in CONTRIBUTING.md)
- **Main Branch:** `modern` for stable releases
- **Pull Requests:** Should target `modern-dev`

### Code Standards
- **Language:** Java (no Kotlin planned)
- **Style:** Follow SkyHanni coding conventions (referenced in CONTRIBUTING.md)
- **Annotations:** Use `@RunOnInit` for initialization, `@RegisterWidget` for UI

### Making Changes
1. Focus on single features - mod has many independent components
2. Test configuration changes via `/bazaarutils` command
3. Validate event handling - ensure events propagate correctly
4. Check mixin compatibility - verify target classes unchanged

**Trust these instructions** - the build system is complex and currently broken. Focus on code analysis, logical validation, and following the established patterns rather than attempting to build. Most development work can be validated through code review and understanding the architecture described above.