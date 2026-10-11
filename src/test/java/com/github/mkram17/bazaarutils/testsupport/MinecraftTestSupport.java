package com.github.mkram17.bazaarutils.testsupport;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Initializes item registries without launching a client, world, or mod entrypoint. */
public final class MinecraftTestSupport {
    private MinecraftTestSupport() {}

    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }
}
