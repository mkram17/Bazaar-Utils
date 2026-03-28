package com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

public record Bookmark(String name, ItemStack itemStack, String productID) {
    public static final Codec<Bookmark> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(Bookmark::name),
            ItemStack.CODEC.fieldOf("itemStack").forGetter(Bookmark::itemStack),
            Codec.STRING.fieldOf("productID").forGetter(Bookmark::productID)
    ).apply(instance, Bookmark::new));
}