package com.github.mkram17.bazaarutils.features;

import com.github.mkram17.bazaarutils.Utils.GUIUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import committee.nova.mkb.api.IKeyBinding;
import committee.nova.mkb.keybinding.KeyConflictContext;
import committee.nova.mkb.keybinding.KeyModifier;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import lombok.Getter;
import lombok.Setter;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

//TODO fix keybind working :////
public class StashHelper {
    @Getter @Setter
    private boolean enabled;
    private boolean keybindWasHeld = false;
    private int ticksBetweenPresses = 0;
    private int taskTicks = 0;
    private boolean shouldSend = false;
    public IKeyBinding stashExtended;

    public void registerKeybind() {
        KeyBinding stashKeybind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Pick Up Stash",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "Bazaar Utils"
        ));


        stashExtended = (IKeyBinding) stashKeybind;
        stashExtended.setKeyModifierAndCode(KeyModifier.ALT, InputUtil.fromKeyCode(GLFW.GLFW_KEY_V, 47));
        stashExtended.setKeyConflictContext(KeyConflictContext.UNIVERSAL);


        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(!enabled)
                return;
            if(!stashExtended.getKeyBinding().isPressed() || !stashExtended.getKeyBinding().wasPressed())
                return;
            if(!keybindWasHeld && ticksBetweenPresses>9) {
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
