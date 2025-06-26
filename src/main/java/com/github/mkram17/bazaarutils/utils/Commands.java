package com.github.mkram17.bazaarutils.utils;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.features.CustomOrder;
import com.github.mkram17.bazaarutils.features.restrictsell.RestrictSell;
import com.github.mkram17.bazaarutils.features.restrictsell.RestrictSellControl;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.URISyntaxException;

import static com.github.mkram17.bazaarutils.config.BUConfig.HANDLER;
import static com.github.mkram17.bazaarutils.config.BUConfig.openGUI;

public class Commands {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        // Main command: /bazaarutils
        LiteralArgumentBuilder<FabricClientCommandSource> bazaarutils = ClientCommandManager.literal("bazaarutils").executes(context -> {
            openGUI();
            return 1;
        });
        // Subcommand: /bazaarutils remove <index>
//        bazaarutils.then(ClientCommandManager.literal("remove")
//                        .then(ClientCommandManager.argument("index", IntegerArgumentType.integer())
//                                .executes(Commands::executeRemove)
//                        )
//        );
        bazaarutils.then(ClientCommandManager.literal("help")
                .executes((context) -> {
                            Util.notifyAll(Util.HELP_MESSAGE);
                            return 1;
                        }
                ));

//        bazaarutils.then(ClientCommandManager.literal("info")
//                        .then((ClientCommandManager.argument("index", IntegerArgumentType.integer())
//                                        .executes(Commands::executeInfo)
//                                )
//                        )
//                );

        bazaarutils.then(ClientCommandManager.literal("tax")
                .then((ClientCommandManager.argument("amount", DoubleArgumentType.doubleArg(1, 1.25))
                                .executes((context) -> {
                                    BUConfig.get().bzTax = DoubleArgumentType.getDouble(context, "amount") / 100;
                                    return 1;
                                })
                        )
                )
        );
        bazaarutils.then(ClientCommandManager.literal("discord")
                .executes((context) -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.send(() -> {
                        context.getSource().getClient().setScreen(new ConfirmLinkScreen((confirmed) -> {
                            if (confirmed) {
                                    try {
                                        net.minecraft.util.Util.getOperatingSystem().open(new URI(Util.DISCORD_LINK));
                                    } catch (URISyntaxException e) {
                                        throw new RuntimeException(e);
                                    }
                            }
                            MinecraftClient.getInstance().setScreen(null);
                        }, Util.DISCORD_LINK, true));
                    });
                    return 1;
                })
        );
        bazaarutils.then(ClientCommandManager.literal("developer")
                .executes((context) -> {
                    BUConfig.get().developerMode = !BUConfig.get().developerMode;
                    HANDLER.save();
                    Util.notifyAll(BUConfig.get().developerMode ? "Developer mode enabled. Developer commands are now available!" : "Developer mode disabled. Developer commands are now restricted.");
                    return 1;
                })
        );

        bazaarutils.then(ClientCommandManager.literal("customorder")
                .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.argument("order amount", IntegerArgumentType.integer(1, 71680))
                                .then(ClientCommandManager.argument("slot number", IntegerArgumentType.integer(1, 36))
                                        .executes(context -> {
                                            int orderAmount = IntegerArgumentType.getInteger(context, "order amount");
                                            int slotNumber = IntegerArgumentType.getInteger(context, "slot number");

                                            if (orderAmount < 1 || orderAmount > 71679) {
                                                context.getSource().sendError(Text.literal("Order amount must be 1-71,679"));
                                                return 0;
                                            }

                                            if (slotNumber < 1 || slotNumber > 36) {
                                                context.getSource().sendError(Text.literal("Slot number must be 1-36"));
                                                return 0;
                                            }
                                            CustomOrder orderToAdd = new CustomOrder(
                                                    true,
                                                    orderAmount,
                                                    slotNumber - 1,
                                                    CustomOrder.getNextColoredPane()
                                            );

                                            BUConfig.get().customOrders.add(orderToAdd);
                                            Util.notifyAll("Added order for " + orderToAdd.getOrderAmount() + " in slot number " + slotNumber);
                                            HANDLER.save();
                                            return 1;
                                        })
                                )
                        )
                )
        );
        bazaarutils.then(ClientCommandManager.literal("customorder")
                .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("order number", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    int orderNum = IntegerArgumentType.getInteger(context, "order number") - 1;
                                    if (BUConfig.get().customOrders.size() < orderNum) {
                                        Util.notifyAll("There is no Custom Order #" + orderNum + ". The Custom Order # is based on the order they are displayed in the config.");
                                        return 0;
                                    }
                                    CustomOrder customOrder = BUConfig.get().customOrders.get(orderNum);
                                    if (customOrder.getOrderAmount() != 71680) {
                                        Util.notifyAll("Removed Custom Order for " + BUConfig.get().customOrders.get(orderNum).getOrderAmount());
                                        BUConfig.get().customOrders.get(orderNum).remove();
                                    } else {
                                        Util.notifyAll("Cannot remove Max Buy Order.");
                                        return 0;
                                    }
                                    HANDLER.save();
                                    return 1;
                                })
                        )
                )
        );
        bazaarutils.then(ClientCommandManager.literal("rule")
                .then(ClientCommandManager.literal("add")
                        // Volume branch
                        .then(ClientCommandManager.literal("volume")
                                .then(ClientCommandManager.argument("limit", DoubleArgumentType.doubleArg(0.1))
                                        .executes(context -> {
                                            double limit = DoubleArgumentType.getDouble(context, "limit");
                                            BUConfig.get().restrictSell.addRule(RestrictSell.restrictBy.VOLUME, limit);
                                            HANDLER.save();
                                            Util.notifyAll("Added rule: VOLUME" + ": " + limit);
                                            return 1;
                                        })
                                )
                        )
                        // Price branch
                        .then(ClientCommandManager.literal("price")
                                .then(ClientCommandManager.argument("limit", DoubleArgumentType.doubleArg(0.1))
                                        .executes(context -> {
                                            double limit = DoubleArgumentType.getDouble(context, "limit");
                                            BUConfig.get().restrictSell.addRule(RestrictSell.restrictBy.PRICE, limit);
                                            HANDLER.save();
                                            Util.notifyAll("Added rule: PRICE" + ": " + limit);
                                            return 1;
                                        })
                                )
                        )
                        // Name branch
                        .then(ClientCommandManager.literal("name")
                                .then(ClientCommandManager.argument("itemName", StringArgumentType.string())
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "itemName");
                                            BUConfig.get().restrictSell.addRule(RestrictSell.restrictBy.NAME, name);
                                            HANDLER.save();
                                            Util.notifyAll("Added rule: NAME" + ": " + name);
                                            return 1;
                                        })
                                )
                        )
                )
        );
        bazaarutils.then(ClientCommandManager.literal("rule")
                .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("rule number", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    int restrictNum = IntegerArgumentType.getInteger(context, "rule number") - 1;
                                    RestrictSellControl rule = BUConfig.get().restrictSell.getControls().get(restrictNum);
                                    if (rule == null)
                                        context.getSource().sendError(Text.literal("Invalid rule number. Check the order in /bu"));
                                    if (rule.getRule() != null) {
                                        Util.notifyAll(rule.getRule() == RestrictSell.restrictBy.NAME ? "Removed rule: NAME: " + rule.getName() : "Removed rule: " + rule.getRule() + ": " + rule.getAmount());
                                    }
                                    BUConfig.get().restrictSell.getControls().remove(restrictNum);
                                    HANDLER.save();
                                    return 1;
                                })
                        )
                )
        );
        
        bazaarutils.then(ClientCommandManager.literal("remove")
                .then(ClientCommandManager.argument("index", IntegerArgumentType.integer())
                        .executes(Commands::executeRemove)
                )
        );
        bazaarutils.then(ClientCommandManager.literal("info")
                .then((ClientCommandManager.argument("index", IntegerArgumentType.integer())
                                .executes(Commands::executeInfo)
                        )
                )
        );
        bazaarutils.then(ClientCommandManager.literal("outdated")
                .executes((source) -> {
                    if (!BUConfig.get().developerMode) {
                        source.getSource().sendError(Text.literal("This is a developer command. Enable developer mode with '/bu developer' to use it."));
                        return 0;
                    }
                    for (OrderData item : OrderData.getOutdatedItems()) {
                        Util.notifyAll(item.getName() + " is outdated. Market Price: " + item.getPriceInfo().getMarketPrice() + " Order Price: " + item.getPriceInfo().getPrice());
                    }
                    return 1;
                })
        );
        bazaarutils.then(ClientCommandManager.literal("list")
                .executes(context -> {
                    if (!BUConfig.get().developerMode) {
                        context.getSource().sendError(Text.literal("This is a developer command. Enable developer mode with '/bu developer' to use it."));
                        return 0;
                    }
                    Util.notifyAll(OrderData.getVariables(OrderData::getName).toString());
                    return 1;
                })
        );

        CommandNode<FabricClientCommandSource> bazaarutilsNode = dispatcher.register(bazaarutils);
        dispatcher.register(
                ClientCommandManager.literal("bu")
                        .executes(context -> {
                            // Forward execution to the main command's handler
                            return bazaarutils.getCommand().run(context);
                        })
                        .redirect(bazaarutilsNode) // Inherit subcommands
        );
    }

    private static int executeRemove(CommandContext<FabricClientCommandSource> context) {
        if (!BUConfig.get().developerMode) {
            context.getSource().sendError(Text.literal("This is a developer command. Enable developer mode with '/bu developer' to use it."));
            return 0;
        }
        int index = IntegerArgumentType.getInteger(context, "index");
        String itemInfo = BUConfig.get().watchedOrders.get(index).getGeneralInfo();
        BUConfig.get().watchedOrders.remove(index);  // Changed to directly use config.watchedItems.remove()
        Util.notifyAll("Removed " + itemInfo, Util.notificationTypes.COMMAND);
        return 1;
    }

    private static int executeInfo(CommandContext<FabricClientCommandSource> context) {
        if (!BUConfig.get().developerMode) {
            context.getSource().sendError(Text.literal("This is a developer command. Enable developer mode with '/bu developer' to use it."));
            return 0;
        }
        int index = IntegerArgumentType.getInteger(context, "index");
        Util.notifyAll(BUConfig.get().watchedOrders.get(index).getGeneralInfo());
        return 1;
    }
}