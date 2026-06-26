package com.github.mkram17.bazaarutils.utils;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.data.BazaarData;
import com.github.mkram17.bazaarutils.features.CustomOrder;
import com.github.mkram17.bazaarutils.features.OutbidOrderHandler;
import com.github.mkram17.bazaarutils.features.restrictsell.RestrictSell;
import com.github.mkram17.bazaarutils.features.restrictsell.RestrictSellControl;
import com.github.mkram17.bazaarutils.misc.orderinfo.BazaarOrder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import static com.github.mkram17.bazaarutils.config.BUConfig.openGUI;

public class BUCommands {

    private static List<LiteralArgumentBuilder<FabricClientCommandSource>> getDeveloperCommands() {
        return List.of(
                ClientCommands.literal("remove")
                        .then(ClientCommands.argument("index", IntegerArgumentType.integer())
                                .executes(BUCommands::executeRemove)
                        ),
                ClientCommands.literal("info")
                        .then((ClientCommands.argument("index", IntegerArgumentType.integer())
                                        .executes(BUCommands::executeInfo)
                                )
                        ),
                ClientCommands.literal("outdated")
                        .executes((source) -> {
                            if (!BUConfig.get().developerMode) { PlayerActionUtil.notifyAll("Developer mode is required."); return 0; }
                            for (BazaarOrder item : OutbidOrderHandler.getOutbidOrders()) {
                                PlayerActionUtil.notifyAll(item.getName() + " is outdated. Market Price: " + item.getInstaSellPrice() + " Order Price: " + item.getPricePerItem());
                            }
                            return 1;
                        }),
                ClientCommands.literal("convertname")
                        .then((ClientCommands.argument("item name", StringArgumentType.string())
                                .executes((context) -> {
                                    if (!BUConfig.get().developerMode) { PlayerActionUtil.notifyAll("Developer mode is required."); return 0; }
                                    String name = StringArgumentType.getString(context, "item name")
                                            .replaceAll("_", " ");
                                    String productID = BazaarData.findProductId(name);

                                    if(productID == null){
                                        PlayerActionUtil.notifyAll("Could not find product ID for " + name);
                                    } else {
                                        PlayerActionUtil.notifyAll(name + ": " + productID);
                                    }
                                    return 1;
                                })
                        )
                        ),
                ClientCommands.literal("list")
                        .executes(context -> {
                                    if (!BUConfig.get().developerMode) { PlayerActionUtil.notifyAll("Developer mode is required."); return 0; }
                                    PlayerActionUtil.notifyAll(BazaarOrder.getVariables(BazaarOrder::getName).toString());
                                    return 1;
                                }
                        )
        );
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {

        LiteralArgumentBuilder<FabricClientCommandSource> bazaarutils = ClientCommands.literal("bazaarutils").executes(context -> {
            openGUI();
            return 1;
        });

        bazaarutils.then(ClientCommands.literal("help")
                .executes((context) -> {
                            PlayerActionUtil.notifyAll(Util.HELP_MESSAGE);
                            return 1;
                        }
                ));

        bazaarutils.then(ClientCommands.literal("tax")
                .then((ClientCommands.argument("amount", DoubleArgumentType.doubleArg(1, 1.25))
                                .executes((context) -> {
                                    BUConfig.get().bzTax = DoubleArgumentType.getDouble(context, "amount") / 100;
                                    return 1;
                                })
                        )
                )
        );
        bazaarutils.then(ClientCommands.literal("discord")
                .executes((context) -> {
                    Minecraft client = Minecraft.getInstance();
                    client.schedule(() -> {
                        context.getSource().getClient().setScreen(new ConfirmLinkScreen((confirmed) -> {
                            if (confirmed) {
                                    try {
                                        net.minecraft.util.Util.getPlatform().openUri(new URI(Util.DISCORD_LINK));
                                    } catch (URISyntaxException e) {
                                        throw new RuntimeException(e);
                                    }
                            }
                            Minecraft.getInstance().setScreen(null);
                        }, Util.DISCORD_LINK, true));
                    });
                    return 1;
                })
        );
        bazaarutils.then(ClientCommands.literal("developer")
                .executes((context) -> {
                    BUConfig.get().developerMode = !BUConfig.get().developerMode;
                    Util.scheduleConfigSave();
                    PlayerActionUtil.notifyAll(BUConfig.get().developerMode ? "Developer mode enabled. You can now use dev commands!" : "Developer mode disabled.");
                    return 1;
                })
        );

        bazaarutils.then(ClientCommands.literal("customorder")
                .then(ClientCommands.literal("add")
                        .then(ClientCommands.argument("order amount", IntegerArgumentType.integer(1, 71680))
                                .then(ClientCommands.argument("slot number", IntegerArgumentType.integer(1, 36))
                                        .executes(context -> {
                                            int orderAmount = IntegerArgumentType.getInteger(context, "order amount");
                                            int slotNumber = IntegerArgumentType.getInteger(context, "slot number");

                                            if (orderAmount < 1 || orderAmount > 71680) {
                                                context.getSource().sendError(Component.literal("Order amount must be 1-71,680"));
                                                return 0;
                                            }

                                            if (slotNumber < 1 || slotNumber > 36) {
                                                context.getSource().sendError(Component.literal("Slot number must be 1-36"));
                                                return 0;
                                            }
                                            CustomOrder orderToAdd = new CustomOrder(
                                                    true,
                                                    orderAmount,
                                                    slotNumber - 1,
                                                    CustomOrder.getNextColoredPane()
                                            );

                                            BUConfig.get().customOrders.add(orderToAdd);
                                            PlayerActionUtil.notifyAll("Added order for " + orderToAdd.getOrderAmount() + " in slot number " + slotNumber);
                                            Util.scheduleConfigSave();
                                            return 1;
                                        })
                                )
                        )
                )
        );
        bazaarutils.then(ClientCommands.literal("customorder")
                .then(ClientCommands.literal("remove")
                        .then(ClientCommands.argument("order number", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    int orderNum = IntegerArgumentType.getInteger(context, "order number") - 1;
                                    if (BUConfig.get().customOrders.size() < orderNum) {
                                        PlayerActionUtil.notifyAll("There is no Custom Order #" + orderNum + ". The Custom Order # is based on the order they are displayed in the config.");
                                        return 0;
                                    }
                                    CustomOrder customOrder = BUConfig.get().customOrders.get(orderNum);
                                    if (customOrder.getOrderAmount() != 71680) {
                                        PlayerActionUtil.notifyAll("Removed Custom Order for " + BUConfig.get().customOrders.get(orderNum).getOrderAmount());
                                        BUConfig.get().customOrders.get(orderNum).remove();
                                    } else {
                                        PlayerActionUtil.notifyAll("Cannot remove Max Buy Order.");
                                        return 0;
                                    }
                                    Util.scheduleConfigSave();
                                    return 1;
                                })
                        )
                )
        );
        bazaarutils.then(ClientCommands.literal("rule")
                .then(ClientCommands.literal("add")
                        // Volume branch
                        .then(ClientCommands.literal("volume")
                                .then(ClientCommands.argument("limit", DoubleArgumentType.doubleArg(0.1))
                                        .executes(context -> {
                                            double limit = DoubleArgumentType.getDouble(context, "limit");
                                            BUConfig.get().restrictSell.addRule(RestrictSell.restrictBy.VOLUME, limit);
                                            Util.scheduleConfigSave();
                                            PlayerActionUtil.notifyAll("Added rule: VOLUME" + ": " + limit);
                                            return 1;
                                        })
                                )
                        )
                        // Price branch
                        .then(ClientCommands.literal("price")
                                .then(ClientCommands.argument("limit", DoubleArgumentType.doubleArg(0.1))
                                        .executes(context -> {
                                            double limit = DoubleArgumentType.getDouble(context, "limit");
                                            BUConfig.get().restrictSell.addRule(RestrictSell.restrictBy.PRICE, limit);
                                            Util.scheduleConfigSave();
                                            PlayerActionUtil.notifyAll("Added rule: PRICE" + ": " + limit);
                                            return 1;
                                        })
                                )
                        )
                        // Name branch
                        .then(ClientCommands.literal("name")
                                .then(ClientCommands.argument("itemName", StringArgumentType.string())
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "itemName");
                                            BUConfig.get().restrictSell.addRule(RestrictSell.restrictBy.NAME, name);
                                            Util.scheduleConfigSave();
                                            PlayerActionUtil.notifyAll("Added rule: NAME" + ": " + name);
                                            return 1;
                                        })
                                )
                        )
                )
        );
        bazaarutils.then(ClientCommands.literal("rule")
                .then(ClientCommands.literal("remove")
                        .then(ClientCommands.argument("rule number", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    int restrictNum = IntegerArgumentType.getInteger(context, "rule number") - 1;
                                    RestrictSellControl rule = BUConfig.get().restrictSell.getControls().get(restrictNum);
                                    if (rule == null)
                                        context.getSource().sendError(Component.literal("Invalid rule number. Check the order in /bu"));
                                    if (rule.getRule() != null) {
                                        PlayerActionUtil.notifyAll(rule.getRule() == RestrictSell.restrictBy.NAME ? "Removed rule: NAME: " + rule.getName() : "Removed rule: " + rule.getRule() + ": " + rule.getAmount());
                                    }
                                    BUConfig.get().restrictSell.getControls().remove(restrictNum);
                                    Util.scheduleConfigSave();
                                    return 1;
                                })
                        )
                )
        );
        bazaarutils.then(ClientCommands.literal("updateresources")
                .executes(context -> {
                    PlayerActionUtil.notifyAll("Checking for resource updates...");
                    ResourceManager.checkForUpdates(true);
                    return 1;
                })
        );

        for(LiteralArgumentBuilder<FabricClientCommandSource> command : getDeveloperCommands()) {
            bazaarutils.then(command);
        }

        CommandNode<FabricClientCommandSource> bazaarutilsNode = dispatcher.register(bazaarutils);
        dispatcher.register(
                ClientCommands.literal("bu")
                        .executes(context -> {
                            // Forward execution to the main command's handler
                            return bazaarutils.getCommand().run(context);
                        })
                        .redirect(bazaarutilsNode) // Inherit subcommands
        );
    }

    private static int executeRemove(CommandContext<FabricClientCommandSource> context) {
        if (!BUConfig.get().developerMode) { PlayerActionUtil.notifyAll("Developer mode is required."); return 0; }
        int index = IntegerArgumentType.getInteger(context, "index");
        BazaarOrder bazaarOrder = BUConfig.get().userOrders.get(index);
        bazaarOrder.removeFromWatchedItems();
        PlayerActionUtil.notifyAll("Removed " + bazaarOrder, Util.notificationTypes.COMMAND);
        return 1;
    }

    private static int executeInfo(CommandContext<FabricClientCommandSource> context) {
        if (!BUConfig.get().developerMode) { PlayerActionUtil.notifyAll("Developer mode is required."); return 0; }
        int index = IntegerArgumentType.getInteger(context, "index");
        PlayerActionUtil.notifyAll(BUConfig.get().userOrders.get(index).toString());
        return 1;
    }
}
