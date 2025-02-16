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
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static com.github.mkram17.bazaarutils.config.BUConfig.HANDLER;
import static com.github.mkram17.bazaarutils.config.BUConfig.openGUI;

public class Commands {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        // Main command: /bazaarutils
        dispatcher.register(ClientCommandManager.literal("bazaarutils")
                .executes(context -> {
                    BUConfig.get().openGUI();
                    return 1;
                })
        );

        // Alias: /bu
        dispatcher.register(ClientCommandManager.literal("bu")
                .executes(context -> {
                    openGUI();
                    return 1;
                })
        );

        // Subcommand: /bazaarutils remove <index>
        dispatcher.register(ClientCommandManager.literal("bazaarutils")
                .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("index", IntegerArgumentType.integer())
                                .executes(Commands::executeRemove)
                        )
                )
        );
        dispatcher.register(ClientCommandManager.literal("bazaarutils")
                .then(ClientCommandManager.literal("help")
                        .executes((context) -> {
                                    Util.notifyAll(Util.HELPMESSAGE);
                                    return 1;
                                }
                        )
                ));

        // Subcommand: /bazaarutils info <index>
        dispatcher.register(ClientCommandManager.literal("bazaarutils")
                .then(ClientCommandManager.literal("info")
                        .then((ClientCommandManager.argument("index", IntegerArgumentType.integer())
                                        .executes(Commands::executeInfo)
                                )
                        )
                ));

        // Also register subcommands for the alias
        dispatcher.register(ClientCommandManager.literal("bu")
                .then(ClientCommandManager.literal("remove")
                        .then((ClientCommandManager.argument("index", IntegerArgumentType.integer())
                                        .executes(Commands::executeRemove)
                                )
                        )
                ));

        dispatcher.register(ClientCommandManager.literal("bu")
                .then(ClientCommandManager.literal("info")
                        .then((ClientCommandManager.argument("index", IntegerArgumentType.integer())
                                        .executes(Commands::executeInfo)
                                )
                        )
                ));
        dispatcher.register(ClientCommandManager.literal("bu")
                .then(ClientCommandManager.literal("tax")
                        .then((ClientCommandManager.argument("amount", DoubleArgumentType.doubleArg())
                                        .executes((context) ->{
                                            BUConfig.get().bzTax = DoubleArgumentType.getDouble(context, "amount");
                                            return 1;
                                        })
                                )
                        )
                ));
        dispatcher.register(ClientCommandManager.literal("bu")
                .then(ClientCommandManager.literal("developer")
                        .executes((context) ->{
                            BUConfig.get().developerMode = !BUConfig.get().developerMode;
                            return 1;
                        })
                ));

        dispatcher.register(ClientCommandManager.literal("bu")
                .then(ClientCommandManager.literal("customorder")
                        .then(ClientCommandManager.argument("order amount", IntegerArgumentType.integer(1, 71680))
                                .then(ClientCommandManager.argument("slot number", IntegerArgumentType.integer(0, 35))
                                        .executes(context -> {
                                            int orderAmount = IntegerArgumentType.getInteger(context, "order amount");
                                            int slotNumber = IntegerArgumentType.getInteger(context, "slot number");

                                            if (orderAmount < 1 || orderAmount > 71679) {
                                                context.getSource().sendError(Text.literal("Order amount must be 1-71,679"));
                                                return 0;
                                            }

                                            if (slotNumber < 0 || slotNumber > 35) {
                                                context.getSource().sendError(Text.literal("Slot number must be 0-35"));
                                                return 0;
                                            }

                                            BUConfig.get().customOrders.add(new CustomOrder(new CustomOrderSettings(
                                                    true,
                                                    orderAmount,
                                                    slotNumber+1,
                                                    CustomOrder.COLORMAP.get(BUConfig.get().customOrders.size())
                                            )));

                                            return 1;
                                        })
                                )
                        )
                )
        );
        dispatcher.register(ClientCommandManager.literal("bu")
                .then(ClientCommandManager.literal("restrict")
                        .then(ClientCommandManager.literal("add")
                            .then(ClientCommandManager.argument("volume or price?", StringArgumentType.string())
                                .then(ClientCommandManager.argument("limit", DoubleArgumentType.doubleArg(.1))
                                        .executes(context -> {
                                            String restrictionString = StringArgumentType.getString(context,"volume or price?");
                                            double limit = DoubleArgumentType.getDouble(context, "limit");

                                            if (!restrictionString.equals("volume") && !restrictionString.equals("price")) {
                                                context.getSource().sendError(Text.literal("Restriction type must be \"volume\" or \"price\""));
                                                return 0;
                                            }
                                            BUConfig.get().restrictSell.addRestriction(RestrictSell.restrictBy.valueOf(restrictionString.toUpperCase()), limit);
                                            Util.notifyAll("Added restriction: " + restrictionString.toUpperCase() + ": " + limit);
                                            return 1;
                                        })
                                )
                        )
                        )
                )
        );
        dispatcher.register(ClientCommandManager.literal("bu")
                .then(ClientCommandManager.literal("restrict")
                        .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("restriction number", IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            int restrictNum = IntegerArgumentType.getInteger(context, "restriction number")-1;
                                            RestrictSellControl restriction = BUConfig.get().restrictSell.getControls().get(restrictNum);
                                            Util.notifyAll("Removed restriction: " + restriction.getRestriction().toString() + ": " + restriction.getAmount());
                                            BUConfig.get().restrictSell.getControls().remove(restrictNum);
                                            HANDLER.save();
                                            return 1;
                                        })
                                )
                        )
                )
        );
        dispatcher.register(ClientCommandManager.literal("bu")
                .then(ClientCommandManager.literal("customorder")
                        .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("order number", IntegerArgumentType.integer())
                                        .executes(context -> {
                                            int orderNum = IntegerArgumentType.getInteger(context, "order number");
                                            CustomOrder customOrder = BUConfig.get().customOrders.get(orderNum);
                                            if(customOrder.getSettings().getOrderAmount() != 71680) {
                                                Util.notifyAll("Removed Custom Order for " + BUConfig.get().customOrders.get(orderNum).getSettings().getOrderAmount());
                                                BUConfig.get().customOrders.remove(orderNum);
                                            } else{
                                                Util.notifyAll("Cannot remove Max Buy Order.");
                                            }
                                            HANDLER.save();
                                            return 1;
                                        })
                                )
                        )
                )
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