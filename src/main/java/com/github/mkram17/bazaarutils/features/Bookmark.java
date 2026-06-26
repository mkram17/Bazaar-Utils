package com.github.mkram17.bazaarutils.features;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.events.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.events.SlotClickEvent;
import com.github.mkram17.bazaarutils.events.handlers.BUListener;
import com.github.mkram17.bazaarutils.misc.BUCompatibilityHelper;
import com.github.mkram17.bazaarutils.misc.CustomItemButton;
import com.github.mkram17.bazaarutils.misc.autoregistration.RegisterWidget;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderInfoContainer;
import com.github.mkram17.bazaarutils.misc.orderinfo.PriceInfoContainer;
import com.github.mkram17.bazaarutils.misc.widgets.ItemSlotButtonWidget;
import com.github.mkram17.bazaarutils.mixin.AccessorAbstractContainerScreen;
import com.github.mkram17.bazaarutils.utils.GUIUtils;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.ScreenInfo;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.Util; // Poprawiony import!
import lombok.Getter;
import lombok.Setter;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.WidgetSprites;
//? if < 1.21.10 {
/*import net.minecraft.client.gui.screen.Screen;
 *///?}
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

//Object is created in GUIUtils when in an item's bazaar page
public class Bookmark extends CustomItemButton implements BUListener {

    @Getter
    public final String name;
    @Getter @Setter
    public ItemStack bookmarkedItemStack;
    @Getter
    private final OrderInfoContainer orderInfo;
    private static final int SIGN_SLOT_NUMBER = 45;

    private static final Identifier BASE = Identifier.tryBuild(BazaarUtils.MODID, "widget/bookmark_widget_base");
    private static final Identifier HOVER = Identifier.tryBuild(BazaarUtils.MODID, "widget/bookmark_widget_hover");
    public static final WidgetSprites SLOT_BUTTON_TEXTURES = new WidgetSprites(BASE, HOVER);

    protected void subscribeToEventBusUnsubscriber() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> BazaarUtils.EVENT_BUS.unsubscribe(this));
    }

    public Bookmark(String name) {
        this.name = name;
        this.slotNumber = 0;

        // Zabezpieczenie przed błędem NullPointerException - inicjalizacja na żądanie
        this.bookmarkedItemStack = Bookmark.findItemStack(this.name);
        this.orderInfo = new OrderInfoContainer(name, null, null, PriceInfoContainer.PriceType.INSTABUY, null);

        this.subscribe();
    }

    @EventHandler
    protected void replaceItemEvent(ReplaceItemEvent event) {
        try {
            if (!super.shouldReplaceItem(event) || (this.bookmarkedItemStack == null && !BUConfig.get().bookmarks.contains(this)))
                return;

            if (this.replacementItem == null) {
                this.changeVisuals(Bookmark.isItemBookmarked(this.name));
            }

            event.setReplacement(this.replacementItem);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @EventHandler
    private void onBookmarkClick(SlotClickEvent event){
        if(!super.wasButtonSlotClicked(event))
            return;

        SoundUtil.playSound(BUTTON_SOUND, BUTTON_VOLUME);
        this.reverseBookmarkStatus();
        this.bookmarkedItemStack = Bookmark.findItemStack(this.name);
        Util.scheduleConfigSave();
    }

    public void onWidgetLeftClick(){
        SoundUtil.playSound(BUTTON_SOUND, BUTTON_VOLUME);
        boolean userHasSkyblockerBazaarOverlay = BUCompatibilityHelper.isSkyblockerLoaded() && BUCompatibilityHelper.isSkyblockerBazaarOverlayEnabled();

        if(userHasSkyblockerBazaarOverlay) {
            BUCompatibilityHelper.setSkyblockerBazaarOverlayValue(false);
        }

        GUIUtils.clickSlot(SIGN_SLOT_NUMBER, 0);
        GUIUtils.runOnNextSignOpen(event -> GUIUtils.setSignText(this.name, true));

        if(userHasSkyblockerBazaarOverlay) {
            Util.tickExecuteLater(10, () -> BUCompatibilityHelper.setSkyblockerBazaarOverlayValue(true));
        }
    }

    public void onWidgetShiftClick(){
        BUConfig.get().bookmarks.remove(this);
        Util.scheduleConfigSave();
    }

    private void reverseBookmarkStatus(){
        if(Bookmark.isItemBookmarked(this.name)) {
            this.changeVisuals(false);
            BUConfig.get().bookmarks.remove(this);
        } else {
            this.changeVisuals(true);
            BUConfig.get().bookmarks.add(this);
        }
        Util.scheduleConfigSave();
    }

    private void changeVisuals(boolean bookmarked){
        if(bookmarked) {
            this.replacementItem = new ItemStack(Items.GREEN_STAINED_GLASS_PANE, 1);
            this.replacementItem.set(DataComponents.CUSTOM_NAME, Component.literal("Remove " + this.name + " Bookmark"));
            this.replacementItem.set(BazaarUtils.CUSTOM_SIZE_COMPONENT, "⃠ ");
        } else {
            this.replacementItem = new ItemStack(Items.RED_STAINED_GLASS_PANE, 1);
            this.replacementItem.set(DataComponents.CUSTOM_NAME, Component.literal("Bookmark " + this.name));
            this.replacementItem.set(BazaarUtils.CUSTOM_SIZE_COMPONENT, "★");
        }
    }

    public static String findItemName(ChestLoadedEvent e){
        String nameFromContainer = findItemNameFromContainer();
        if(!OrderInfoContainer.isValidName(nameFromContainer) || nameFromContainer.length() >= 30 ) {
            return findNameFromItemStacks(e.getItemStacks(), nameFromContainer);
        }
        return nameFromContainer;
    }

    private static String findNameFromItemStacks(List<ItemStack> itemStacks, String nameFromContainer){
        for(ItemStack stack : itemStacks){
            if(stack == null) continue;
            if (!stack.isEmpty() && stack.getHoverName().getString().startsWith(nameFromContainer)) {
                return stack.getCustomName().getString();
            }
        }
        return "???";
    }

    private static String findItemNameFromContainer(){
        ScreenInfo screenInfo = ScreenInfo.getCurrentScreenInfo();
        String containerName = screenInfo.getContainerName();
        if(screenInfo.inMenu(ScreenInfo.BazaarMenuType.INSTA_BUY)) {
            return containerName.substring(0, containerName.indexOf("➜")-1);
        } else {
            return containerName.substring(containerName.indexOf("➜") + 2);
        }
    }

    private static ItemStack findItemStack(String name){
        AbstractContainerMenu handler = GUIUtils.getHandledScreen();

        if(handler == null) return null;
        for(Slot slot : handler.slots){
            ItemStack itemStack = slot.getItem();
            if(itemStack == null) continue;

            if (!itemStack.isEmpty() && itemStack.getHoverName().getString().startsWith(name)) {
                return itemStack;
            }
        }
        for(Slot slot : handler.slots){
            ItemStack itemStack = slot.getItem();

            if (!itemStack.isEmpty() && itemStack.getHoverName().getString().contains(name)) {
                return itemStack;
            }
        }
        return Items.DIAMOND.getDefaultInstance();
    }

    public static boolean isItemBookmarked(String itemName){
        return findMatchingBookmark(itemName).isPresent();
    }

    public static Optional<Bookmark> findMatchingBookmark(String itemName){
        return BUConfig.get().bookmarks.stream().filter(bookmark -> bookmark.getName().equalsIgnoreCase(itemName)).findAny();
    }

    @RegisterWidget
    public static List<ItemSlotButtonWidget> getWidgets() {
        List<ItemSlotButtonWidget> widgets = new ArrayList<>();
        ScreenInfo screenInfo = ScreenInfo.getCurrentScreenInfo();
        boolean isTargetScreen = screenInfo.inMenu(ScreenInfo.BazaarMenuType.BAZAAR_MAIN_PAGE);

        if (!(Minecraft.getInstance().screen instanceof AccessorAbstractContainerScreen screen) || !isTargetScreen)
            return Collections.emptyList();

        ItemSlotButtonWidget.ScreenWidgetDimensions dimensions = ItemSlotButtonWidget.getSafeScreenDimensions(screen, screenInfo.getContainerName());

        int buttonSize = 18;
        int spacing = 4;
        int buttonX = dimensions.x() + dimensions.backgroundWidth() + spacing;
        int currentButtonY = dimensions.y() + spacing;

        List<Bookmark> bookmarks = BUConfig.get().bookmarks;

        for (Bookmark value : bookmarks) {
            ItemStack configuredItem = value.getBookmarkedItemStack();

            final ItemStack itemForButton = (configuredItem == null) ? Items.BARRIER.getDefaultInstance() : configuredItem;
            final Bookmark bookmark = value;
            MutableComponent text = Component.literal(bookmark.getName()).withStyle(ChatFormatting.BOLD);

            OrderInfoContainer orderInfo = bookmark.getOrderInfo();
            orderInfo.updateMarketPrice();

            Style style = Style.EMPTY.withColor(ChatFormatting.GRAY).withBold(false);
            text.append(Component.literal("\nBuy: " + Util.getPrettyString(orderInfo.getMarketPrice(PriceInfoContainer.PriceType.INSTASELL)) + " coins").setStyle(style));
            text.append(Component.literal("\nSell: " + Util.getPrettyString(orderInfo.getMarketPrice(PriceInfoContainer.PriceType.INSTABUY)) + " coins").setStyle(style));

            ItemSlotButtonWidget button = new ItemSlotButtonWidget(
                    buttonX,
                    currentButtonY,
                    buttonSize, buttonSize,
                    SLOT_BUTTON_TEXTURES,
                    (btn) -> {
                        //? if > 1.21.8 {
                        if (Minecraft.getInstance().hasShiftDown()) {
                            //?} else {
                            /*if (Screen.hasShiftDown()) {
                             *///?}
                            PlayerActionUtil.notifyAll("Removed " + bookmark.getName() + " bookmark from shift-click. Open Bazaar again to display changes.");
                            bookmark.onWidgetShiftClick();
                        } else {
                            bookmark.onWidgetLeftClick();
                        }

                    },
                    itemForButton,
                    text
            );

            widgets.add(button);
            currentButtonY += buttonSize + spacing;
        }

        return widgets;
    }

    @Override
    public void subscribe() {
        BazaarUtils.EVENT_BUS.subscribe(this);
        this.subscribeToEventBusUnsubscriber();
    }
}
