package com.github.mkram17.bazaarutils.features;

import com.github.mkram17.bazaarutils.data.BazaarData;
import com.github.mkram17.bazaarutils.events.ScreenChangeEvent;
import com.github.mkram17.bazaarutils.misc.orderinfo.PriceInfoContainer;
import com.github.mkram17.bazaarutils.utils.Util;
import dev.isxander.yacl3.api.Option;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.ChatFormatting;
import net.minecraft.world.scores.*;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MaxBuyOrder extends CustomOrder {
    private static final Pattern PURSE_PATTERN = Pattern.compile("(Purse|Piggy): (?<purse>[0-9,.]+)");
    private static double purse;

    public MaxBuyOrder(boolean enabled) {
        super(enabled);
    }

    @Override
    public void subscribe() {
        super.subscribe();
    }

    @EventHandler
    public void onScreenChange(ScreenChangeEvent event){
        if(event.getNewScreen() == null || event.getOldScreen() == null)
            return;
        try {
            if(!inCorrectScreen(event)) {
                return;
            }
            ItemStack itemStack = getItemStack(event.getOldScreen());
            if(itemStack == null)
                return;

            Minecraft client = Minecraft.getInstance();
            updatePurse(client);

            String name = itemStack.getCustomName().getString();
            Optional<String> productID = BazaarData.findProductIdOptional(name);

            if(productID.isEmpty()) return;

            double cost = BazaarData.findItemPrice(productID.get(), PriceInfoContainer.PriceType.INSTASELL) + .1;//.1 is for lowest competitive price

            int amountCanBuy = (int) (Math.floor(purse / cost));
            super.setOrderAmount(Math.min(amountCanBuy, 71680));

        } catch (Exception e) {
            Util.notifyError("Could not parse coins from scoreboard text", e);
        }
    }

    private static boolean inCorrectScreen(ScreenChangeEvent event){
        return (event.getNewScreen().getTitle().getString().contains("How many do you want?") || event.getNewScreen().getTitle().getString().contains("➜ Insta"))
                && event.getNewScreen() instanceof ContainerScreen;
    }

    private static ItemStack getItemStack(Screen previousScreen) {

        if(!(previousScreen instanceof ContainerScreen containerScreen))
            return null;

        ItemStack itemStack = containerScreen.getMenu().getContainer().getItem(13);
        if (itemStack.isEmpty()) {
            Util.notifyError("Could not find item in previous container.", new Throwable());
            return null;
        }
        return itemStack;
    }

    private static void updatePurse(Minecraft client) {
        ClientLevel world = client.level;
        if (world == null) return;

        Scoreboard scoreboard = world.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);

        if (objective == null) return;

        ObjectArrayList<String> stringLines = new ObjectArrayList<>();
        for (ScoreHolder scoreHolder : scoreboard.getTrackedPlayers()) {
            if (scoreboard.listPlayerScores(scoreHolder).containsKey(objective)) {
                PlayerTeam team = scoreboard.getPlayersTeam(scoreHolder.getScoreboardName());
                if (team != null) {
                    String line = team.getPlayerPrefix().getString() + team.getPlayerSuffix().getString();
                    if (!line.trim().isEmpty()) {
                        stringLines.add(ChatFormatting.stripFormatting(line));
                    }
                }
            }
        }
        purse = getPurse(stringLines);
    }

    private static double getPurse(List<String> scoreboardLines) {
        for (String line : scoreboardLines) {
            if (line.contains("Purse:") || line.contains("Piggy:")) {
                Matcher matcher = PURSE_PATTERN.matcher(line);
                if (matcher.find()) {
                    try {
                        return Double.parseDouble(matcher.group("purse").replace(",", ""));
                    } catch (NumberFormatException e) {
                        Util.notifyError("Failed to parse purse from scoreboard", e);
                    }
                }
            }
        }
        return -1d;
    }

    @Override
    public Option<Boolean> createOption() {
        return super.createBooleanOption(
                "Buy Max Button",
                "Buy order button for the maximum amount of an item you can buy with the coins in your purse.",
                this::isEnabled,
                this::setEnabled
        );
    }
}
