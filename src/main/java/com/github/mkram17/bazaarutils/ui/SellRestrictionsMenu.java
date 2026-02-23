package com.github.mkram17.bazaarutils.ui;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictsell.InstantSellRestrictions;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictsell.NumericRestrictBy;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictsell.StringRestrictBy;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictsell.controls.SellRestrictionControl;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictsell.controls.StringSellRestrictionControl;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;

public class SellRestrictionsMenu<T extends Enum<T>> extends BaseOwoScreen<FlowLayout> {
    private static final float BACKGROUND_BLUR_QUALITY = 10f;
    private static final float BACKGROUND_BLUR_SIZE = 10f;

    //TODO make this work depending on screen size so it doesnt only work for me
    private static final int MAXIMUM_ORDERS_BEFORE_SCROLL = 8;
    private T restrictionType;

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.surface(Surface.blur(BACKGROUND_BLUR_QUALITY, BACKGROUND_BLUR_SIZE))
                .verticalAlignment(VerticalAlignment.CENTER)
                .horizontalAlignment(HorizontalAlignment.CENTER);
        FlowLayout horizontalFlow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fill(100));
        horizontalFlow.child(generateNewRestrictionsParent());


        // Center: Orders (wrap in full-size container that centers its child)
        FlowLayout centered = Containers.verticalFlow(Sizing.content(), Sizing.content());
        centered.child(generateUserRestrictionsParent());

        horizontalFlow.child(centered);

        rootComponent.child(horizontalFlow);
    }

    private Component generateUserRestrictionsParent() {
        var customOrders = BUConfig.get().feature.customOrders;
        ParentComponent parent;
        if (customOrders.size() > MAXIMUM_ORDERS_BEFORE_SCROLL) {
            parent = Containers.verticalScroll(Sizing.content(), Sizing.fill(80), generateRestrictionsContainer());
        } else {
            parent = generateRestrictionsContainer();
        }
        return parent
                .padding(Insets.of(8))
                .surface(Surface.DARK_PANEL)
                .margins(Insets.top(20));
    }

    private ParentComponent generateRestrictionsContainer() {
        var sellRestrictions = InventoryConfig.SellRestrictionsRules.restrictors();
        var verticalFlow = Containers.verticalFlow(Sizing.content(), Sizing.content());


        for (SellRestrictionControl control : sellRestrictions) {
            verticalFlow.child(generateRestrictionButton(control));
        }

        return verticalFlow.padding(Insets.of(20));
    }

    private ParentComponent generateNewRestrictionsParent() {
        var horizontalFlow = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        horizontalFlow.child(Components.label(Text.literal("New Sell Restriction").formatted(Formatting.BOLD)).margins(Insets.of(10)));
        horizontalFlow.child(addRestrictionTypeDropdown());
        horizontalFlow.child(addRestrictionButton());

        return horizontalFlow
                .margins(Insets.of(20))
                .padding(Insets.of(8))
                .surface(Surface.DARK_PANEL)
                .verticalAlignment(VerticalAlignment.CENTER);
    }

    private Component addRestrictionTypeDropdown() {
        SingleOptionCheckboxDropdownComponent dropdown = new SingleOptionCheckboxDropdownComponent(Sizing.content());
        return dropdown.closeWhenNotHovered(false)
                .checkbox(Text.literal("Restrict By Name"),
                        restrictionType == StringRestrictBy.NAME,
                        button -> {
                    dropdown.disableCheckboxes();
                    restrictionType = (T) StringRestrictBy.NAME;
                })
                .checkbox(Text.literal("Restrict By Volume"),
                        restrictionType == NumericRestrictBy.VOLUME,
                        button -> {
                            dropdown.disableCheckboxes();
                            restrictionType = (T) NumericRestrictBy.VOLUME;
                        })
                .checkbox(Text.literal("Restrict By Price"),
                        restrictionType == NumericRestrictBy.PRICE,
                        button -> {
                            dropdown.disableCheckboxes();
                            restrictionType = (T) NumericRestrictBy.PRICE;
                        });
    }

    private Component addRestrictionButton() {
        return Components.button(
                        Text.literal("Add"),
                        button -> {
                            PlayerActionUtil.notifyAll("Please enter a restriction type and amount/name.");

                            SellRestrictionControl newControl = new StringSellRestrictionControl(true, "Example Restriction");
                            InstantSellRestrictions.addRule(newControl);
                            MinecraftClient.getInstance().setScreen(new SellRestrictionsMenu());
                            ConfigUtil.scheduleConfigSave();
                        })
                .margins(Insets.of(3));
    }

    private FlowLayout generateRestrictionButton(SellRestrictionControl control) {
        var horizontalFlow = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        String garbageCanEmoji = "\uD83D\uDDD1";

        horizontalFlow.child(
                Components.label(
                        Text.literal("Restrict Insta Selling: " + control.getRule())
                ).margins(Insets.of(3, 3, 3, 1))
        ).child(
                Components.button(
                        Text.literal(garbageCanEmoji),
                        button -> {
//                            dumb and not functional, we have to decide whether we still have this owo screen or not.
                            InventoryConfig.SellRestrictionsRules.restrictors().remove(control);
                            MinecraftClient.getInstance().setScreen(new CustomOrdersMenu());
                        }).margins(Insets.of(3, 3, 1, 3))
        );
        return horizontalFlow;
    }
}
