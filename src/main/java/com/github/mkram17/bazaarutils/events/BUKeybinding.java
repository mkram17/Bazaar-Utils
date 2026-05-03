package com.github.mkram17.bazaarutils.events;

import net.minecraft.client.KeyMapping;

/**
 * Base class for features driven by a {@link KeyMapping}.
 * Registration is the responsibility of {@code KeybindConfig};
 * subclasses only define behavior.
 */
public abstract class BUKeybinding extends BUListener {

    protected final KeyMapping keyMapping;

    protected BUKeybinding(KeyMapping keyMapping) {
        this.keyMapping = keyMapping;
    }

    protected abstract void registerFabricEvents();

    /** Returns the human-readable bound key string, e.g. {@code "key.keyboard.v"}. */
    public String getBoundKey() {
        return keyMapping.saveString();
    }
}