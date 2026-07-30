package com.github.mkram17.bazaarutils.utils.web;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds this repo to {@code contract/wire-format.json}.
 *
 * <p>The website declares the same enum names, string caps, order ceiling and link-code
 * normalization as Zod schemas, and neither side can import the other. The JSON file is the
 * written-down copy both are checked against; the website has an equivalent suite
 * ({@code lib/minecraft/contract.test.ts}) pointed at its own copy of the same file.</p>
 *
 * <p><strong>A failure here is not a bug in the test.</strong> It means the protocol changed, and
 * the change has to land on the website and in both copies of the JSON as well.</p>
 *
 * <p>Deliberately loads almost nothing. The enums import no Minecraft classes, the two caps are
 * compile-time constants that javac inlines, and {@code normalizeLinkCode}'s class initialises
 * only strings — so this runs without a game, a mixin environment or any fixture.</p>
 */
class WireFormatContractTest {
    /** Set by the {@code test} task, because the working directory is not the repo root. */
    private static final String CONTRACT_PATH_PROPERTY = "bazaarutils.contractFile";

    private static JsonObject orderSync;
    private static JsonObject linkCode;

    @BeforeAll
    static void loadContract() throws IOException {
        String path = System.getProperty(CONTRACT_PATH_PROPERTY);

        assertNotNull(
                path,
                "System property " + CONTRACT_PATH_PROPERTY + " is not set. The test task in "
                        + "build.gradle.kts is what supplies it; running this test outside Gradle "
                        + "needs it passed with -D."
        );

        Path file = Path.of(path);

        assertTrue(Files.isRegularFile(file), "Contract file not found at " + file);

        JsonObject root = new Gson().fromJson(
                Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);

        orderSync = root.getAsJsonObject("orderSync");
        linkCode = root.getAsJsonObject("linkCode");

        assertNotNull(orderSync, "contract file has no orderSync object");
        assertNotNull(linkCode, "contract file has no linkCode object");
    }

    private static List<String> strings(JsonObject owner, String field) {
        JsonArray array = owner.getAsJsonArray(field);

        assertNotNull(array, "contract file has no " + field);

        List<String> values = new ArrayList<>(array.size());
        for (JsonElement element : array) values.add(element.getAsString());

        return values;
    }

    private static List<String> names(Enum<?>[] constants) {
        return Arrays.stream(constants).map(Enum::name).toList();
    }

    @Test
    @DisplayName("TransactionType.Side names match the contract")
    void sidesMatch() {
        // Gson serializes name(), which is what makes this the wire value. Side also has a
        // toString() rendering "Buy"/"Sell" for chat — switching WebJson to that would break
        // every sync with a 400 and nothing here would notice, so the names are what is pinned.
        assertEquals(strings(orderSync, "sides"), names(TransactionType.Side.values()));
    }

    @Test
    @DisplayName("OrderStatus names match the contract")
    void statusesMatch() {
        assertEquals(strings(orderSync, "statuses"), names(OrderStatus.values()));
    }

    @Test
    @DisplayName("PricingPosition names match the contract")
    void pricingPositionsMatch() {
        assertEquals(strings(orderSync, "pricingPositions"), names(PricingPosition.values()));
    }

    @Test
    @DisplayName("The order ceiling matches the contract")
    void orderCeilingMatches() {
        assertEquals(
                orderSync.get("maxOrdersPerSync").getAsInt(),
                BazaarUtilsApi.MAX_ORDERS_PER_SYNC
        );
    }

    @Test
    @DisplayName("The string cap matches the contract")
    void stringCapMatches() {
        // One constant guards both fields on this side; the website caps them separately, so
        // check against both rather than picking one.
        assertEquals(
                orderSync.get("maxProductIdLength").getAsInt(),
                OrderSnapshot.MAX_STRING_LENGTH,
                "productId cap"
        );
        assertEquals(
                orderSync.get("maxItemNameLength").getAsInt(),
                OrderSnapshot.MAX_STRING_LENGTH,
                "itemName cap"
        );
    }

    @Test
    @DisplayName("normalizeLinkCode agrees with the website on every listed case")
    void normalizationMatches() {
        // The one that fails silently. The normalized code IS the serverId nonce the website
        // hands to Mojang's hasJoined, so a divergence raises no error on either side — it just
        // makes every link attempt fail verification while both implementations look correct.
        JsonArray cases = linkCode.getAsJsonArray("normalization");

        assertNotNull(cases, "contract file has no linkCode.normalization");
        assertTrue(cases.size() > 0, "no normalization cases to check");

        for (JsonElement element : cases) {
            JsonObject entry = element.getAsJsonObject();
            String input = entry.get("input").getAsString();

            assertEquals(
                    entry.get("output").getAsString(),
                    BazaarUtilsApi.normalizeLinkCode(input),
                    () -> "normalizing " + quoted(input)
            );
        }
    }

    @Test
    @DisplayName("A generated code survives normalization unchanged")
    void generatedCodesAreAlreadyNormal() {
        // The website generates codes from this alphabet at this length. Normalizing one must be
        // a no-op, or a freshly issued code would not match itself.
        String alphabet = linkCode.get("alphabet").getAsString();
        int length = linkCode.get("length").getAsInt();

        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) code.append(alphabet.charAt(i % alphabet.length()));

        assertEquals(code.toString(), BazaarUtilsApi.normalizeLinkCode(code.toString()));
    }

    @Test
    @DisplayName("The code alphabet excludes characters players misread")
    void alphabetIsUnambiguous() {
        String alphabet = linkCode.get("alphabet").getAsString();

        for (char ambiguous : "01OIL".toCharArray()) {
            assertEquals(
                    -1,
                    alphabet.indexOf(ambiguous),
                    "alphabet contains " + ambiguous + ", which players confuse when typing a code"
            );
        }
    }

    private static String quoted(String value) {
        return "\"" + value + "\"";
    }
}
