package com.github.mkram17.bazaarutils.data.integrations;

import com.github.mkram17.bazaarutils.generated.BazaarUtilsIntegrations;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.annotations.modules.BazaarIntegration;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.google.common.base.Preconditions;
import com.google.common.collect.MutableClassToInstanceMap;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Central registry for all {@link BazaarIntegration}-annotated classes.
 *
 * <h2>Registration</h2>
 * Collected at startup via {@code BazaarUtilsIntegrations.collected} (annotation processor
 * output). Each annotated class is registered under its {@link BazaarIntegration#id()}.
 * Multiple classes sharing an id contribute to the same capability map — one instance
 * per {@link BazaarIntegrationCapability} sub-interface per id, enforced by
 * {@link com.google.common.base.Preconditions} at registration time.
 *
 * <h2>Capability resolution</h2>
 * {@link #resolveCapabilities} walks only the direct interfaces of the registered class,
 * not the full hierarchy. This is intentional — sub-interfaces of a capability (e.g.
 * {@code BazaarActivityIntegration.StoragePrunable}) are registered under their own key,
 * not under {@code BazaarActivityIntegration} or {@code BazaarIntegrationCapability}.
 * Callers must query the exact capability interface they want.
 *
 * <h2>Lookup</h2>
 * {@link #get} returns a typed {@link Optional} — no casting at call sites.
 * {@link #notify} dispatches to all registered instances of a capability across every id.
 * {@link #idsWith} returns ids that have at least one component implementing the given capability.
 */
@Module
public final class BazaarIntegrationRegistry {
    private static final BazaarLogger LOG = BazaarLogger.of(BazaarIntegrationRegistry.class);

    /**
     * id → capability map.
     * One instance per capability interface per id, enforced at registration.
     * Backed by Guava's {@link MutableClassToInstanceMap} — typed lookup, no casting.
     */
    private static final Map<String, MutableClassToInstanceMap<Object>> REGISTRY = new LinkedHashMap<>();

    public BazaarIntegrationRegistry() {
        BazaarUtilsIntegrations.collected.forEach(BazaarIntegrationRegistry::register);

        REGISTRY.forEach((id, caps) ->
                LOG.debug("Integration '{}' — capabilities: {}", id,
                        caps.keySet().stream()
                                .map(Class::getSimpleName)
                                .collect(Collectors.joining(", "))));

        LOG.info("BazaarIntegrationRegistry initialised — {} id(s)", REGISTRY.size());
    }

    public static void register(Object instance) {
        var ann = instance.getClass().getAnnotation(BazaarIntegration.class);
        if (ann == null) return;

        Preconditions.checkArgument(!ann.id().isBlank(),
                "@BazaarIntegration id must not be blank: %s",
                instance.getClass().getName());
        Preconditions.checkArgument(instance instanceof BazaarIntegrationCapability,
                "%s is annotated @BazaarIntegration but implements no BazaarIntegrationCapability",
                instance.getClass().getName());

        var map = REGISTRY.computeIfAbsent(ann.id(), id -> MutableClassToInstanceMap.create());

        for (var capability : resolveCapabilities(instance.getClass())) {
            Preconditions.checkState(!map.containsKey(capability),
                    "Integration '%s' already has a %s — one implementation per capability per id",
                    ann.id(), capability.getSimpleName());

            map.put(capability, instance);
        }
    }

    /**
     * Walks the direct interfaces of {@code clazz} and returns those that extend
     * {@link BazaarIntegrationCapability}. Does not recurse into superinterfaces —
     * register under the most specific capability interface you implement.
     */
    @SuppressWarnings("unchecked")
    private static List<Class<? extends BazaarIntegrationCapability>> resolveCapabilities(Class<?> clazz) {
        var result = new ArrayList<Class<? extends BazaarIntegrationCapability>>();

        for (var capability : clazz.getInterfaces()) {
            if (BazaarIntegrationCapability.class.isAssignableFrom(capability)) {
                result.add((Class<? extends BazaarIntegrationCapability>) capability);
            }
        }

        return result;
    }

    /**
     * Returns the instance registered for {@code id} under {@code capability}.
     * Empty if no component for that id implements the requested capability.
     */
    public static <T extends BazaarIntegrationCapability> Optional<T> get(String id, Class<T> capability) {
        var map = REGISTRY.get(id);

        return map == null ? Optional.empty() : Optional.ofNullable(map.getInstance(capability));
    }

    /**
     * Dispatches {@code action} to every instance registered under {@code capability},
     * across all ids.
     */
    public static <T> void notify(Class<T> capability, Consumer<T> action) {
        REGISTRY.values().stream()
                .map(map -> map.getInstance(capability))
                .filter(Objects::nonNull)
                .forEach(action);
    }

    /** Ids that have an instance registered for {@code capability}. */
    public static Set<String> idsWith(Class<?> capability) {
        return REGISTRY.entrySet().stream()
                .filter(e -> e.getValue().containsKey(capability))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static Set<String> allIds() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }
}