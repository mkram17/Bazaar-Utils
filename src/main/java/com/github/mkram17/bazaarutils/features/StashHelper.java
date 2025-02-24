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

//TODO add option to block materials stashed away message from hypixel
public class StashHelper {
    @Getter @Setter
    private boolean enabled;
    private int ticksBetweenPresses;
    private transient KeyBinding stashKeybind;

    public StashHelper(boolean enabled) {
        this.enabled = enabled;
        ticksBetweenPresses = 0;
    }

    public IKeyBinding getStashExtended(){
        return  (IKeyBinding) stashKeybind;
    }

    public void registerKeybind() {
        stashKeybind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Pick Up Stash",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "Bazaar Utils"
        ));


        IKeyBinding stashExtended = (IKeyBinding) stashKeybind;
        stashExtended.setKeyModifierAndCode(KeyModifier.ALT, InputUtil.fromKeyCode(GLFW.GLFW_KEY_V, 47));
        stashExtended.setKeyConflictContext(KeyConflictContext.UNIVERSAL);


        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ticksBetweenPresses++;
            if(!enabled)
                return;
            if(!stashExtended.getKeyBinding().isPressed() || !stashExtended.getKeyBinding().wasPressed())
                return;
            if(ticksBetweenPresses>15) {
                GUIUtils.closeHandledScreen();
                GUIUtils.sendCommand("pickupstash");

                 ticksBetweenPresses = 0;
            }
        });
    }
    public Option<Boolean> createOption() {
        return Option.<Boolean>createBuilder()
                .name(Text.literal("Stash Helper"))
                .description(OptionDescription.of(Text.literal("Alt + V to close bazaar (if it's open) and then pick up stash")))
                .binding(true,
                        this::isEnabled,
                        this::setEnabled)
                .controller(BUConfig::createBooleanController)
                .build();
    }
}
