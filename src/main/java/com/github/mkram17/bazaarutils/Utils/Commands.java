package com.github.mkram17.bazaarutils.Utils;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.features.customorder.CustomOrder;
import com.github.mkram17.bazaarutils.features.customorder.CustomOrderSettings;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static com.github.mkram17.bazaarutils.config.BUConfig.*;

public class Commands {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        // Main command: /bazaarutils
        dispatcher.register(ClientCommandManager.literal("bazaarutils")
                .executes(context -> {
                    openGUI();
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
                .then(ClientCommandManager.literal("customorder")
                        .then(ClientCommandManager.argument("order amount", IntegerArgumentType.integer(1, 71679))
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

                                            BUConfig.customOrders.add(new CustomOrder(new CustomOrderSettings(
                                                    true,
                                                    orderAmount,
                                                    slotNumber,
                                                    CustomOrder.COLORMAP.get(BUConfig.customOrders.size())
                                            )));

                                            return 1;
                                        })
                                )
                        )
                )
        );
        dispatcher.register(ClientCommandManager.literal("bu")
                .then(ClientCommandManager.literal("customorder")
                        .then((ClientCommandManager.literal("remove")
                                        .then((ClientCommandManager.argument("order number", IntegerArgumentType.integer())
                                                .executes((context) -> {
                                                            BUConfig.customOrders.remove(IntegerArgumentType.getInteger(context, "order number"));
                                                            Util.notifyAll("customOrders is now: " + customOrders);
                                                            return 1;
                                                        }
                                                ))
                                        )
                                )
                        )
                )
        );
        dispatcher.register(ClientCommandManager.literal("bu")
                .then(ClientCommandManager.literal("customorder")
                        .then((ClientCommandManager.literal("max")
                                        .executes((context) -> {
                                            customOrders.add(maxBuyOrder);
                                            return 1;
                                        })
                                )
                        )
                )
        );
    }
    private static int executeRemove(CommandContext<FabricClientCommandSource> context) {
        int index = IntegerArgumentType.getInteger(context, "index");
        String itemInfo = watchedItems.get(index).getGeneralInfo();
        watchedItems.remove(index);  // Changed to directly use watchedItems.remove()
        Util.notifyAll("Removed " + itemInfo, Util.notificationTypes.COMMAND);
        return 1;
    }

    private static int executeInfo(CommandContext<FabricClientCommandSource> context) {
        int index = IntegerArgumentType.getInteger(context, "index");
        Util.notifyAll(watchedItems.get(index).getGeneralInfo());
        return 1;
    }
}