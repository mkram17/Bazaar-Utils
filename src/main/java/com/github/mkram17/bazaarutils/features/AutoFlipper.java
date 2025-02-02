package com.github.mkram17.bazaarutils.features;


import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.Events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.Events.SignOpenEvent;
import com.github.mkram17.bazaarutils.Utils.GUIUtils;
import com.github.mkram17.bazaarutils.Utils.ItemData;
import com.github.mkram17.bazaarutils.Utils.Util;
import com.github.mkram17.bazaarutils.config.BUConfig;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public class AutoFlipper {
    public static double flipPrice;
    private static double orderPrice = -1;
    private static int orderVolumeFilled = -1;
    private static ItemData item;


    @EventHandler
    public void onSignOpenedEvent(SignOpenEvent e) {
        if (!BUConfig.autoFlip)
            return;

        if (item != null)
            autoAddToSign(e);
    }

    @EventHandler
    public void guiChestOpenedEvent(ChestLoadedEvent e) {
        if(BUConfig.autoFlip && BazaarUtils.gui.inFlipGui()) {
            item = getFlipItem(e);
            assert item != null : "Could not find flip item.";
            flipPrice = item.getFlipPrice();
        }
    }

    public static void autoAddToSign(SignOpenEvent e) {
        if(flipPrice != 0 && BazaarUtils.gui.wasLastChestFlip()) {
            GUIUtils.setSignText(Double.toString(Util.getPrettyNumber(flipPrice)));
            GUIUtils.closeGui();
            item.setPriceType(ItemData.priceTypes.INSTABUY);
        }
    }

    public static ItemData getFlipItem(ChestLoadedEvent e){

        for (ItemStack itemStack : e.getItemStacks()) {

            if (itemStack == null) continue;

            String displayName = itemStack.getName().getString();
            LoreComponent lore = itemStack.getComponents().get(DataComponentTypes.LORE);

            if(displayName.contains("Flip Order")) {
                getItemInfo(lore);

                //method updates item var
                if (matchFound()) {
                    return item;
                }
            }
        }
        return null;
    }

    public static void getItemInfo(LoreComponent lore) {
        //get order volume and price of item that is being flipped
        try {
            String orderPrice = lore.lines().get(3).getString();
            AutoFlipper.orderPrice = Double.parseDouble(orderPrice.substring(orderPrice.indexOf(":") + 2, orderPrice.indexOf("coins") - 1));
            String orderVolume = lore.lines().get(1).getString();
            orderVolumeFilled = Integer.parseInt(orderVolume.substring(0, orderVolume.indexOf("x")));

        } catch (Exception ex) {
            Util.notifyAll("Error while trying to find order price or volume ");
            ex.printStackTrace();
        }
    }

    public static boolean matchFound() {
        item = ItemData.findItem(null, orderPrice, orderVolumeFilled, ItemData.priceTypes.INSTASELL);
        if (item != null) {
            if (item.getStatus() == ItemData.statuses.FILLED) {
                Util.notifyAll("Found match.", Util.notificationTypes.ITEMDATA);
                return true;
            }else {
                Util.notifyAll("found match, but isnt filled", Util.notificationTypes.ERROR);
                return true;
            }
        }
        Util.notifyAll("Couldnt find a match", Util.notificationTypes.ITEMDATA);
        return false;
    }
    public static Option<Boolean> createOption() {
        return Option.<Boolean>createBuilder()
                .name(Text.literal("Enable Auto Flipper"))
                .description(OptionDescription.of(Text.literal("Button in flip order menu to undercut market prices for items.")))
                .binding(BUConfig.autoFlip,
                        BUConfig::isAutoFlip,
                        BUConfig::setAutoFlip)
                .controller(BUConfig::createBooleanController)
                .build();
    }


}