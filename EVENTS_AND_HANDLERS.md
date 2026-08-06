# Events and Handlers

Architecture notes for the Bazaar Utils event system, aimed at contributors extending the mod.

Per-event field lists and usage examples live in the javadoc on each event class, next to the code they describe. This document only covers the concepts you cannot get from reading a single class. For per-event detail, follow the links in the [event index](#event-index).

## Overview

There is a single event bus: SkyblockAPI's `EventBus`. `BazaarUtils` grabs it once at startup —

```java
public static EventBus EVENT_BUS = SkyBlockAPI.getEventBus();
```

— so the mod's own events and SkyblockAPI's events flow through the same bus and share one priority ordering. A single listener can subscribe to a mod event (`ContainerLoadedEvent`) and a SkyblockAPI event (`ChatReceivedEvent.Post`) with the same `@Subscription` annotation.

## Writing a listener

A listener is a class that:

1. extends [`BUListener`](src/main/java/com/github/mkram17/bazaarutils/events/BUListener.java),
2. is annotated `@Module` (or `@PreInitModule` / `@LateInitModule`), and
3. has one or more methods annotated `@Subscription`.

`BUListener`'s constructor calls `subscribe()` (which is `final`), so a listener registers itself the moment it is constructed — you never call `EVENT_BUS.register` yourself. The `@Module`-family annotation is what causes the class to be constructed in the first place; see [the module pipeline](#the-module-annotation-pipeline).

A real example, abbreviated from [`events/bazaar/BazaarChatHandler`](src/main/java/com/github/mkram17/bazaarutils/events/bazaar/BazaarChatHandler.java):

```java
@Module
public final class BazaarChatHandler extends BUListener {
    @Subscription(priority = Priority.FIRST)
    private void onChat(ChatReceivedEvent.Post event) {
        // parse the message, then post a BazaarChatEvent
    }
}
```

`BUListener` also exposes a `protected void registerFabricEvents()` no-op hook, for the rare listener that additionally needs a raw Fabric callback registered at subscribe time. Override it if you need one; nothing in the tree currently does.

## The module annotation pipeline

You never maintain a list of modules by hand. Each `@Module`-family annotation is itself meta-annotated [`@AutoCollect("<name>")`](src/main/java/com/github/mkram17/bazaarutils/utils/annotations/autoregistration/AutoCollect.java):

| Annotation | Collected into | Initialized |
|---|---|---|
| `@Module` | `BazaarUtilsModules` | `onInitializeClient()` |
| `@PreInitModule` | `BazaarUtilsPreInitModules` | static init (earliest) |
| `@LateInitModule` | `BazaarUtilsLateInitModules` | after the repo is ready (`onRepoReady()`) |
| `@Command` | `BazaarUtilsCommands` | `onInitializeClient()` |

At build time, [`buildSrc/.../ModuleRegistryGeneratingTask`](buildSrc/src/main/java/com/github/mkram17/bazaarutils/build/ModuleRegistryGeneratingTask.java) scans the source tree for these annotations and generates `com.github.mkram17.bazaarutils.generated.BazaarUtils{Modules,PreInitModules,LateInitModules,Commands}`. Each generated class holds:

- a `public static` field per entry (e.g. `BazaarUtilsModules.OrderStatusHighlight`),
- a `collected` list of every entry, and
- an `init()` that constructs each entry once (guarded so it cannot run twice).

**Adding a new module is just annotating the class** — the generator does the rest. The generated per-entry field is also how non-listener code reaches a module singleton: for example `MixinAbstractContainerScreen` calls `BazaarUtilsModules.OrderStatusHighlight.isEnabled()`.

## Predicates

Instead of opening every handler with `if (!isEnabled() || !inCorrectScreen(event)) return;`, guards are expressed as annotations and evaluated by the bus before the method runs:

- [`@OnlyWhenEnabled`](src/main/java/com/github/mkram17/bazaarutils/events/predicates/OnlyWhenEnabled.java) — runs only when the listener's `ToggleableFeature.isEnabled()` is true.
- [`@OnlyBazaarScreen(...)`](src/main/java/com/github/mkram17/bazaarutils/events/predicates/OnlyBazaarScreen.java) — runs only on matching bazaar screens: an explicit `value` whitelist, `any = true`, or `useConstraintsInterface = true` to delegate to the instance's `ScreenConstrained`. Omitting all three — a bare `@OnlyBazaarScreen`, or one supplying only `except` — matches any bazaar screen.

The mod also leans on SkyblockAPI's own predicate annotations, including `@OnlyOnSkyBlock`, `@OnRepoStatus(repoStatus = ...)`, `@TimePassed(duration = "5s")`, and `@IgnoreFiller`. Note one caveat: `@IgnoreFiller` only filters `InventoryChangeEvent` — on any other event type it is a no-op, so it does not do anything on, say, a `SlotClickEvent`.

Two things worth understanding:

- **Predicates are built once, at registration time** — not per event. A provider's `getPredicate(Method)` runs when the listener subscribes and returns a function that is then invoked for each event.
- **`getPredicate` receives only a `java.lang.reflect.Method`** — it has no reference to the instance being registered. That is why [`RegistrationScope`](src/main/java/com/github/mkram17/bazaarutils/events/RegistrationScope.java) exists: `BUListener.subscribe()` wraps `EVENT_BUS.register(this)` in `RegistrationScope.wrap(this, …)`, seeding a `ThreadLocal` with the instance so providers like `ToggleableFeaturePredicateProvider` and `BazaarScreenEventPredicateProvider` can resolve `this` while building the predicate. This matters for abstract bases with multiple concrete subclasses (e.g. `RestrictionHelper`), where the declaring class alone would be ambiguous.

Providers are registered as services in `src/main/resources/META-INF/services/tech.thatgravyboat.skyblockapi.api.events.base.EventPredicateProvider`.

## Priorities and `inherited = true`

Two easy things to get backwards:

- **Priority is inverted from what you may expect: lower value runs first.** [`Priority.FIRST`](src/main/java/com/github/mkram17/bazaarutils/utils/Priority.java) is `Integer.MIN_VALUE` and runs before everything; `Priority.LAST` is `Integer.MAX_VALUE` and runs last. Prefer the named tiers (`FIRST`, `HIGHEST`, `HIGH`, `NORMAL`, `LOW`, `LOWEST`, `LAST`) over raw integers. This matches SkyblockAPI, and is the opposite of Meteor's Orbit bus that the mod used previously.
- **A `@Subscription` method declared on an abstract base class needs `inherited = true`.** The bus only registers subscription methods declared directly on the instance's own class unless the annotation opts in. `RestrictionHelper` (the base of `InstantSellRestrictions` / `SellSacksRestrictions`) uses `@Subscription(inherited = true)` for exactly this reason.

## Defining a new event

1. Extend `tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent`, or `CancellableSkyBlockEvent` if it needs to be cancellable (call `event.cancel()` from a handler; `post` returns `true` when the event ended up cancelled).
2. Post it with `new MyEvent(...).post(EVENT_BUS)` — `post` is a method on `SkyBlockEvent`.
3. By convention, the code that fires an event lives beside it: Minecraft-facing events under `events/minecraft` (posted from a mixin or a small handler), bazaar events under `events/bazaar` (posted from `BazaarChatHandler` or the order utilities).

All current mod events extend `SkyBlockEvent`; `SlotInteractionEvent` is the only cancellable one.

## Event index

Field lists and usage examples are in each class's javadoc — follow the link.

| Event | Package | Purpose | Posted from |
|---|---|---|---|
| [`ContainerLoadedEvent`](src/main/java/com/github/mkram17/bazaarutils/events/minecraft/ContainerLoadedEvent.java) | `events.minecraft` | A chest GUI has fully loaded (every slot populated, past Hypixel's "Loading…" state). | `ContainerLoadedHandler` |
| [`ReplaceItemEvent`](src/main/java/com/github/mkram17/bazaarutils/events/minecraft/ReplaceItemEvent.java) | `events.minecraft` | An item in a `SimpleContainer` is about to be replaced; handlers may swap the shown stack. | `MixinSimpleContainer` |
| [`ScreenChangeEvent`](src/main/java/com/github/mkram17/bazaarutils/events/minecraft/ScreenChangeEvent.java) (`.Pre` / `.Post`) | `events.minecraft` | The current screen is changing. | `MinecraftMixin` |
| [`SignOpenEvent`](src/main/java/com/github/mkram17/bazaarutils/events/minecraft/SignOpenEvent.java) | `events.minecraft` | A sign-edit screen opened. | `MixinSignEditScreen` |
| [`SlotInteractionEvent`](src/main/java/com/github/mkram17/bazaarutils/events/minecraft/SlotInteractionEvent.java) | `events.minecraft` | Cancellable; every slot interaction — mouse and keyboard — on a container screen. | `MixinAbstractContainerScreen` |
| [`BazaarChatEvent<T>`](src/main/java/com/github/mkram17/bazaarutils/events/bazaar/BazaarChatEvent.java) | `events.bazaar` | A parsed bazaar chat action (order created / filled / claimed / flipped / insta-sell / insta-buy). | `BazaarChatHandler` |
| [`BazaarDataUpdateEvent`](src/main/java/com/github/mkram17/bazaarutils/events/bazaar/BazaarDataUpdateEvent.java) | `events.bazaar` | Fresh bazaar market data arrived from the Hypixel API. | `BazaarDataManager` |
| [`UserOrdersChangeEvent`](src/main/java/com/github/mkram17/bazaarutils/events/bazaar/UserOrdersChangeEvent.java) | `events.bazaar` | The tracked user-orders list changed (add / remove). | `OrderUtil`, `Order` |

Listeners that fire or consume these (they are handlers, not events): `ContainerLoadedHandler` posts `ContainerLoadedEvent`; `BazaarChatHandler` parses chat and *posts* `BazaarChatEvent`; `BazaarChatEventHandler` *consumes* `BazaarChatEvent`.
