package land.webgui;

import land.webgui.compat.Compat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import land.webgui.server.EntityBinding;
import land.webgui.server.WebviewServerConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Collection;

public final class WebviewCommands {
    private WebviewCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("webgui")
                        .requires(Compat.opRequirement())
                        .then(Commands.literal("gui")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("url", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                                                    String url = StringArgumentType.getString(ctx, "url");
                                                    for (ServerPlayer p : players) {
                                                        WebviewNetworking.openGui(p, url);
                                                    }
                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal("Web GUI → " + players.size() + " player(s)"),
                                                            true);
                                                    return players.size();
                                                }))))
                        .then(Commands.literal("hud")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("url", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                                                    String url = StringArgumentType.getString(ctx, "url");
                                                    for (ServerPlayer p : players) {
                                                        WebviewNetworking.openHud(p, url);
                                                    }
                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal("Web HUD → " + players.size() + " player(s)"),
                                                            true);
                                                    return players.size();
                                                }))))
                        .then(Commands.literal("bind")
                                .then(Commands.literal("entity")
                                        .then(Commands.argument("selector", EntityArgument.entities())
                                                .then(Commands.argument("url", StringArgumentType.string())
                                                        .executes(ctx -> bindEntities(ctx, false))
                                                        .then(Commands.argument("cancel_interaction", BoolArgumentType.bool())
                                                                .executes(ctx -> bindEntities(ctx,
                                                                        BoolArgumentType.getBool(ctx, "cancel_interaction"))))))))
                        .then(Commands.literal("unbind")
                                .then(Commands.literal("entity")
                                        .then(Commands.argument("selector", EntityArgument.entities())
                                                .executes(ctx -> {
                                                    Collection<? extends Entity> entities = EntityArgument.getEntities(ctx, "selector");
                                                    int count = 0;
                                                    for (Entity e : entities) {
                                                        if (EntityBindingStore.unbind(e.getUUID())) count++;
                                                    }
                                                    final int removed = count;
                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal("WebGUI: unbound " + removed + " entity/entities"),
                                                            true);
                                                    return removed;
                                                }))))
                        .then(Commands.literal("reload")
                                .executes(ctx -> {
                                    String msg = WebviewServerConfig.reload();
                                    EntityBindingStore.load();
                                    ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
                                    return 1;
                                })));
    }

    private static int bindEntities(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, boolean cancel)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<? extends Entity> entities = EntityArgument.getEntities(ctx, "selector");
        String url = StringArgumentType.getString(ctx, "url");
        EntityBinding binding = new EntityBinding(url, cancel);
        for (Entity e : entities) {
            EntityBindingStore.bind(e.getUUID(), binding);
        }
        final int count = entities.size();
        ctx.getSource().sendSuccess(
                () -> Component.literal("WebGUI: bound " + count + " entity/entities → " + url),
                true);
        return count;
    }
}
