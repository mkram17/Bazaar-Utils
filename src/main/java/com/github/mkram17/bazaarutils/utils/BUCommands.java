package com.github.mkram17.bazaarutils.utils;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataManager;
import com.github.mkram17.bazaarutils.features.notification.OutbidOrderHandler;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderType;
import com.github.mkram17.bazaarutils.ui.CustomOrdersMenu;
import com.github.mkram17.bazaarutils.ui.SellRestrictionsMenu;
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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public class BUCommands {

    private static final List<LiteralArgumentBuilder<FabricClientCommandSource>> developerCommands = List.of(
            ClientCommandManager.literal("remove")
                    .then(ClientCommandManager.argument("index", IntegerArgumentType.integer())
                            .executes(BUCommands::executeRemove)
                    ),
            ClientCommandManager.literal("info")
                    .then((ClientCommandManager.argument("index", IntegerArgumentType.integer())
                                    .executes(BUCommands::executeInfo)
                            )
                    ),
            ClientCommandManager.literal("outdated")
                    .executes((source) -> {
                        for (Order item : OutbidOrderHandler.getOutbidOrders()) {
                            PlayerActionUtil.notifyAll(item.getName() + " is outdated. Market Price: " + item.getMarketPrice(OrderType.BUY) + " Order Price: " + item.getPricePerItem());
                        }
                        return 1;
                    }),
            ClientCommandManager.literal("convertname")
                    .then((ClientCommandManager.argument("item name", StringArgumentType.string())
                            .executes((context) -> {
                                String name = StringArgumentType.getString(context, "item name")
                                        .replaceAll("_", " ");
                                var productIDOpt = BazaarDataManager.findProductIdOptional(name);

                                if(productIDOpt.isPresent()){
                                    PlayerActionUtil.notifyAll(name + ": " + productIDOpt.get());
                                } else {
                                    PlayerActionUtil.notifyAll("Could not find product ID for " + name);
                                }
                                return 1;
                            })
                    )
                    ),
            ClientCommandManager.literal("list")
                    .executes(context -> {
                                PlayerActionUtil.notifyAll(Order.getVariables(Order::getName).toString());
                                return 1;
                            }
                    )
    );
    private static final LiteralArgumentBuilder<FabricClientCommandSource> bazaarutils = ClientCommandManager.literal("bazaarutils").executes(context -> {
        ConfigUtil.openGUI();
        return 1;
    });

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {




        bazaarutils.then(ClientCommandManager.literal("help")
                .executes((context) -> {
                            PlayerActionUtil.notifyAll(Util.HELP_MESSAGE);
                            return 1;
                        }
                ));

        bazaarutils.then(ClientCommandManager.literal("tax")
                .then((ClientCommandManager.argument("amount", DoubleArgumentType.doubleArg(1, 1.25))
                                .executes((context) -> {
                                    BUConfig.userBazaarTax = DoubleArgumentType.getDouble(context, "amount") / 100;
                                    return 1;
                                })
                        )
                )
        );
        bazaarutils.then(ClientCommandManager.literal("discord")
                .executes((context) -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.send(() -> context.getSource().getClient().setScreen(new ConfirmLinkScreen((confirmed) -> {
                        if (confirmed) {
                                try {
                                    net.minecraft.util.Util.getOperatingSystem().open(new URI(Util.DISCORD_LINK));
                                } catch (URISyntaxException e) {
                                    throw new RuntimeException(e);
                                }
                        }
                        MinecraftClient.getInstance().setScreen(null);
                    }, Util.DISCORD_LINK, true)));
                    return 1;
                })
        );
        bazaarutils.then(ClientCommandManager.literal("developer")
                .executes((context) -> {
                    DeveloperConfig.DEVELOPER_MODE_TOGGLE = !DeveloperConfig.DEVELOPER_MODE_TOGGLE;
                    ConfigUtil.scheduleConfigSave();
                    //TODO register new commands so they can be used without restarting
                    PlayerActionUtil.notifyAll(DeveloperConfig.DEVELOPER_MODE_TOGGLE ? "Developer mode enabled." : "Developer mode disabled. Restart for all changes to take effect");

                    if(DeveloperConfig.DEVELOPER_MODE_TOGGLE) {
                        registerDeveloperCommands(dispatcher);
                    }
                    return 1;
                })
        );
        bazaarutils.then(ClientCommandManager.literal("customorders")
                .executes(context -> {
                    var client = MinecraftClient.getInstance();
                    client.send(() -> client.setScreen(new CustomOrdersMenu()));
                    return 1;
                })
        );
        bazaarutils.then(ClientCommandManager.literal("sellrestrictions")
                .executes(context -> {
                    var client = MinecraftClient.getInstance();
                    client.send(() -> client.setScreen(new SellRestrictionsMenu()));
                    return 1;
                })
        );
        bazaarutils.then(ClientCommandManager.literal("updateresources")
                .executes(context -> {
                    PlayerActionUtil.notifyAll("Checking for resource updates...");
                    ResourceManager.checkForUpdates(true);
                    return 1;
                })
        );


        if (DeveloperConfig.DEVELOPER_MODE_TOGGLE) {
            registerDeveloperCommands(dispatcher);
        }

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

    private static void registerDeveloperCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        for(LiteralArgumentBuilder<FabricClientCommandSource> command : developerCommands) {
            bazaarutils.then(command);
        }
        dispatcher.register(bazaarutils);
    }

    private static int executeRemove(CommandContext<FabricClientCommandSource> context) {
        int index = IntegerArgumentType.getInteger(context, "index");
        Order order = BUConfig.get().general.userOrders.get(index);
        order.removeFromWatchedItems();
        PlayerActionUtil.notifyAll("Removed " + order, NotificationType.COMMAND);
        return 1;
    }

    private static int executeInfo(CommandContext<FabricClientCommandSource> context) {
        int index = IntegerArgumentType.getInteger(context, "index");
        PlayerActionUtil.notifyAll(BUConfig.get().general.userOrders.get(index).toString());
        return 1;
    }
}