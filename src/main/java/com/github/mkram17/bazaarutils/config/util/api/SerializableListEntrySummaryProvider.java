package com.github.mkram17.bazaarutils.config.util.api;

import com.github.mkram17.bazaarutils.config.util.client.SerializableListScreen;
import net.minecraft.text.Text;

/**
 * Optional interface for types used inside a {@link SerializableList}.
 *
 * <p>If your entry class implements this, {@link SerializableListScreen} will call
 * {@link SerializableListEntrySummaryProvider#getSummary(int)} to produce the row label instead of the default
 * {@code "fieldId: value"} reflection fallback.
 *
 * <p>Example:
 * <pre>
 *   {@literal @}ConfigObject
 *   public class MyEntry implements ListEntrySummaryProvider {
 *       {@literal @}ConfigEntry(id = "name") public String name = "default";
 *       {@literal @}ConfigEntry(id = "value") public int value = 0;
 *
 *       {@literal @}Override
 *       public String getSummary() {
 *           return name + " (" + value + ")";
 *       }
 *   }
 * </pre>
 */
public interface SerializableListEntrySummaryProvider {

    /**
     * Returns a short human-readable summary of this entry shown as the row title.
     * Keep it concise — it shares the row with the Edit, Reset, and Remove buttons.
     */
    Text getSummary(int index);

    /**
     * Returns a description shown as the row subtitle. Defaults to empty.
     * Override to provide context or a translatable description for this entry.
     */
    default Text getDescription(int index) {
        return Text.empty();
    }
}