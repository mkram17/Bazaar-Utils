package com.github.mkram17.bazaarutils.mixin;

import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;


//used for GUIUtils setSignText()
@Mixin(AbstractSignEditScreen.class)
public interface AccessorSignEditScreen {
    @Accessor("blockEntity")
    SignBlockEntity getBlockEntity();

    @Accessor("text")
    void setText(SignText newText);

    // Expose the private setCurrentRowMessage method
    @Invoker("setCurrentRowMessage")
    public abstract void setCurrentRowMessage(String message);

    // Accessors for currentRow (private field)
    @Accessor("currentRow")
    public abstract int getCurrentRow();

    @Accessor("currentRow")
    public abstract void setCurrentRow(int row);
}
