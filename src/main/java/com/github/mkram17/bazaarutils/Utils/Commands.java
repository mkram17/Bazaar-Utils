package com.github.mkram17.bazaarutils.Utils;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.features.customorder.CustomOrder;
import com.github.mkram17.bazaarutils.features.customorder.CustomOrderSettings;
import com.github.mkram17.bazaarutils.features.restrictsell.RestrictSell;
import com.github.mkram17.bazaarutils.features.restrictsell.RestrictSellControl;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

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
                                    Util.notifyAll(Util.HELPMESSAGE);
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
                        .then((ClientCommandManager.argument("amount", DoubleArgumentType.doubleArg())
                                        .executes((context) ->{
                                            BUConfig.get().bzTax = DoubleArgumentType.getDouble(context, "amount");
                                            return 1;
                                        })
                                )
                        )
                );
        bazaarutils.then(ClientCommandManager.literal("developer")
                        .executes((context) ->{
                            BUConfig.get().developerMode = !BUConfig.get().developerMode;
                            Util.notifyAll(BUConfig.get().developerMode ? "Developer mode enabled. You must restart for some changes to take effect." : "Developer mode disabled. You must restart for some changes to take effect.");
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
                                            CustomOrder orderToAdd = new CustomOrder(new CustomOrderSettings(
                                                    true,
                                                    orderAmount,
                                                    slotNumber - 1,
                                                    CustomOrder.getNextColoredPane()
                                            ));

                                            BUConfig.get().customOrders.add(orderToAdd);
                                            Util.notifyAll("Added order for " + orderToAdd.getSettings().getOrderAmount() + " in slot number " + slotNumber);
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
                                            int orderNum = IntegerArgumentType.getInteger(context, "order number");
                                            if(BUConfig.get().customOrders.size() < orderNum){
                                                Util.notifyAll("There is no Custom Order #" + orderNum + ". The Custom Order # is based on the order they are displayed in the config.");
                                                return 0;
                                            }
                                            CustomOrder customOrder = BUConfig.get().customOrders.get(orderNum-1);
                                            if(customOrder.getSettings().getOrderAmount() != 71680) {
                                                Util.notifyAll("Removed Custom Order for " + BUConfig.get().customOrders.get(orderNum-1).getSettings().getOrderAmount());
                                                BUConfig.get().customOrders.remove(orderNum);
                                            } else{
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
                                            int restrictNum = IntegerArgumentType.getInteger(context, "rule number")-1;
                                            RestrictSellControl rule = BUConfig.get().restrictSell.getControls().get(restrictNum);
                                            if(rule == null)
                                                context.getSource().sendError(Text.literal("Invalid rule number. Check the order in /bu"));
                                            if(rule.getRule() != null) {
                                                Util.notifyAll(rule.getRule() == RestrictSell.restrictBy.NAME ? "Removed rule: NAME: " + rule.getName() : "Removed rule: " + rule.getRule() + ": " + rule.getAmount());
                                            }
                                            BUConfig.get().restrictSell.getControls().remove(restrictNum);
                                            HANDLER.save();
                                            return 1;
                                        })
                                )
                        )
        );
        if(BUConfig.get().developerMode){
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
        }
//
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
        int index = IntegerArgumentType.getInteger(context, "index");
        String itemInfo = BUConfig.get().watchedItems.get(index).getGeneralInfo();
        BUConfig.get().watchedItems.remove(index);  // Changed to directly use config.watchedItems.remove()
        Util.notifyAll("Removed " + itemInfo, Util.notificationTypes.COMMAND);
        return 1;
    }

    private static int executeInfo(CommandContext<FabricClientCommandSource> context) {
        int index = IntegerArgumentType.getInteger(context, "index");
        Util.notifyAll(BUConfig.get().watchedItems.get(index).getGeneralInfo());
        return 1;
    }
}