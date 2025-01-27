package com.github.mkram17.bazaarutils.data;

import net.hypixel.api.HypixelAPI;
import net.hypixel.api.apache.ApacheHttpClient;
import net.hypixel.api.reply.AbstractReply;

import java.util.UUID;
import java.util.function.BiConsumer;

public class APIUtils {

    private static String getApiKey() {
        String apiKey = System.getenv("HYPIXEL_API_KEY");
        if (apiKey != null) {
            return apiKey;
        }

        return System.getProperty("apiKey", "64bd424e-ccb0-42ed-8b66-6e42a135afb4"); // arbitrary key, replace with your own to test or use the property
    }

    public static final HypixelAPI API;

    static {
        API = new HypixelAPI(new ApacheHttpClient(UUID.fromString(getApiKey())));
    }

    public static <T extends AbstractReply> BiConsumer<T, Throwable> getTestConsumer() {
        return (result, throwable) -> {
            if (throwable != null) {
                throwable.printStackTrace();
                System.exit(0);
                return;
            }

//            System.out.println(result);

            System.exit(0);
        };
    }
}
