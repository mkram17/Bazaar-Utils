package com.github.mkram17.bazaarutils.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;


//used for GUIUtils setSignText()
@Mixin(AbstractSignEditScreen.class)
public interface AccessorSignEditScreen {

    // Expose the private setCurrentRowMessage method
    @Invoker("setMessage")
    void callSetMessage(String message);

    // Accessors for currentRow (private field)
    @Accessor("line")
    int getLine();

    @Accessor("line")
    void setLine(int row);
}
