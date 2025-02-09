package com.github.mkram17.bazaarutils.features;

import com.github.mkram17.bazaarutils.Utils.GUIUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import lombok.Getter;
import lombok.Setter;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class StashHelper {
    @Getter @Setter
    private boolean enabled;
    private boolean keybindWasHeld = false;
    private int ticksBetweenPresses = 0;
    private int taskTicks = 0;
    private boolean shouldSend = false;
    public KeyBinding stashKeybind;

    public void registerKeybind() {
        stashKeybind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Pick Up Stash",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "Bazaar Utils"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(!enabled)
                return;
            if((stashKeybind.wasPressed() || stashKeybind.isPressed()) &&
                    hasCtrlDown() && hasShiftDown() &&
                    !keybindWasHeld && ticksBetweenPresses>9) {

                GUIUtils.closeGui();
                GUIUtils.sendCommand("pickupstash");

                keybindWasHeld = true;
                ticksBetweenPresses = 0;
            } else {
                ticksBetweenPresses++;
                keybindWasHeld = false;
            }
        });
    }
    private boolean hasCtrlDown() {
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        return InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_CONTROL);
    }

    private boolean hasShiftDown() {
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        return InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_SHIFT);
    }
    public Option<Boolean> createOption() {
        return Option.<Boolean>createBuilder()
                .name(Text.literal("Stash Helper"))
                .description(OptionDescription.of(Text.literal("Ctrl + Shift + V to close bazaar and then run /pickupstash")))
                .binding(true,
                        this::isEnabled,
                        this::setEnabled)
                .controller(BUConfig::createBooleanController)
                .build();
    }
}
