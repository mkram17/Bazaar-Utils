package com.github.mkram17.bazaarutils.features.notification;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Discord webhook payload for {@code POST /webhooks/{id}/{token}}.
 *
 * <p>Standard path: build an {@link Embed} via {@link Embed#builder}, then wrap with
 * {@link #embedOnly}. The embed's {@code description} carries the notification text;
 * {@code content} (top-level) is omitted so only the rich embed is shown.
 *
 * @see <a href="https://docs.discord.com/developers/resources/webhook">Discord Webhook Docs</a>
 */
public record DiscordPayload(Optional<String> content, String username, List<Embed> embeds) {

    /**
     * An inline or full-width key-value row rendered within an embed.
     * Discord fits up to 3 inline fields per row; wide fields start a new full-width row.
     */
    public record EmbedField(String name, String value, boolean inline) {
        public static final Codec<EmbedField> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(EmbedField::name),
                Codec.STRING.fieldOf("value").forGetter(EmbedField::value),
                Codec.BOOL.fieldOf("inline").forGetter(EmbedField::inline)
        ).apply(instance, EmbedField::new));

        public static EmbedField empty(boolean inline) {
            return new EmbedField("\u200b", "\u200b", inline);
        }

        /** Compact — shares a row with up to 2 neighbours. */
        public static EmbedField inline(String name, String value) {
            return new EmbedField(name, value, true);
        }

        /** Full-width — always starts a new row. */
        public static EmbedField wide(String name, String value) {
            return new EmbedField(name, value, false);
        }
    }

    /** Small text rendered below the embed body. */
    public record EmbedFooter(String text) {
        static final Codec<EmbedFooter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("text").forGetter(EmbedFooter::text)
        ).apply(instance, EmbedFooter::new));
    }

    /**
     * Rich embed with colour bar, description, structured fields, timestamp, and footer.
     *
     * <p>Always built via {@link Builder}:
     * <pre>{@code
     * Embed embed = Embed.builder("✅ Filled!", GREEN)
     *     .description("Nether Wart @ 5.2/unit [buy] — filled! (71,680x)")
     *     .field("Product", "Nether Wart")
     *     .field("Price",   "5.2/unit")
     *     .field("Side",    "Buy Order")
     *     .wideField("Amount", "71,680×")
     *     .build();
     * }</pre>
     *
     * {@link Builder#build()} seals the embed with the current UTC timestamp and mod-name footer.
     *
     * @see <a href="https://docs.discord.com/developers/resources/message#embed-object">Discord Embed Object</a>
     */
    public record Embed(
            String title,
            String description,
            int color,
            List<EmbedField> fields,
            Optional<String> timestamp,
            Optional<EmbedFooter> footer
    ) {
        public static final Codec<Embed> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("title").forGetter(Embed::title),
                Codec.STRING.fieldOf("description").forGetter(Embed::description),
                Codec.INT.fieldOf("color").forGetter(Embed::color),
                EmbedField.CODEC.listOf().optionalFieldOf("fields", List.of()).forGetter(Embed::fields),
                Codec.STRING.optionalFieldOf("timestamp").forGetter(Embed::timestamp),
                EmbedFooter.CODEC.optionalFieldOf("footer").forGetter(Embed::footer)
        ).apply(instance, Embed::new));

        public static Builder builder(String title, int color) {
            return new Builder(title, color);
        }

        public static final class Builder {
            private final String title;
            private final int color;
            private String description = "";
            private final List<EmbedField> fields = new ArrayList<>();

            private Builder(String title, int color) { this.title = title; this.color = color; }

            public Builder description(String d) {
                description = d; return this;
            }

            public Builder field(EmbedField field) {
                fields.add(field);

                return this;
            }

            public Builder field(String name, String value) {
                fields.add(EmbedField.inline(name, value));

                return this;
            }

            public Builder wideField(String name, String value) {
                fields.add(EmbedField.wide(name, value));

                return this;
            }

            /** Seals with a UTC timestamp and {@link BazaarUtils#MOD_NAME} footer. */
            public Embed build() {
                return new Embed(title, description, color, List.copyOf(fields),
                        Optional.of(Instant.now().toString()),
                        Optional.of(new EmbedFooter(BazaarUtils.MOD_NAME)));
            }
        }
    }

    public static final Codec<DiscordPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("content").forGetter(DiscordPayload::content),
            Codec.STRING.fieldOf("username").forGetter(DiscordPayload::username),
            Embed.CODEC.listOf().optionalFieldOf("embeds", List.of()).forGetter(DiscordPayload::embeds)
    ).apply(instance, DiscordPayload::new));

    /** Plain text only — no embed. */
    public static DiscordPayload simple(String text) {
        return new DiscordPayload(Optional.of(text), BazaarUtils.MOD_NAME, List.of());
    }

    /** Text above a single embed. */
    public static DiscordPayload withEmbed(String text, Embed embed) {
        return new DiscordPayload(Optional.of(text), BazaarUtils.MOD_NAME, List.of(embed));
    }

    /** Embed only — no top-level content text. Standard path for order notifications. */
    public static DiscordPayload embedOnly(Embed embed) {
        return new DiscordPayload(Optional.empty(), BazaarUtils.MOD_NAME, List.of(embed));
    }

    public String toJson() {
        return CODEC.encodeStart(JsonOps.INSTANCE, this)
                .getOrThrow()
                .toString();
    }
}