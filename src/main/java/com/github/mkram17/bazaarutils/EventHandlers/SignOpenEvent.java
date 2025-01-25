package com.github.mkram17.bazaarutils.EventHandlers;

import com.github.mkram17.bazaarutils.Utils.Util;
import com.github.mkram17.bazaarutils.mixin.AccessorGuiEditSign;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.concurrent.CompletableFuture;

public class SignOpenEvent extends Event{
    private GuiScreen guiScreen;


    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event){
        SignOpenEvent eventToPost = new SignOpenEvent();
        CompletableFuture.runAsync(() ->{
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if(event.gui instanceof AccessorGuiEditSign){
                eventToPost.guiScreen = event.gui;
                MinecraftForge.EVENT_BUS.post(eventToPost);
                Util.notifyAll("Sign opened, event posted!", Util.notificationTypes.GUI);
            }
        });

    }

    public GuiScreen getGuiScreen(){
        return guiScreen;
    }
}
