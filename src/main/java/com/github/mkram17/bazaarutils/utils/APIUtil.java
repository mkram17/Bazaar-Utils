package com.github.mkram17.bazaarutils.utils;

import net.hypixel.api.HypixelAPI;
import net.hypixel.api.apache.ApacheHttpClient;

import java.util.UUID;

public class APIUtil {

    public static String getApiKey() {
        String apiKey = System.getenv("HYPIXEL_API_KEY");
        if (apiKey != null) {
            return apiKey;
        }
        // This is a fake api key. The Hypixel API requires an api key to initialize, but it does not use it unless you make personal API requests, which BU does not do.
        return "11111111-2222-3333-4444-555555555555";
    }

    public static final HypixelAPI API;
    public static final UUID uuid;

    static {
        uuid = UUID.fromString(getApiKey());
        API = new HypixelAPI(new ApacheHttpClient(uuid));
    }
}
