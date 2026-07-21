package yourstageskybox.command;

import yourstageskybox.YourStageSkyboxLocale;
import yourstageskybox.network.NetworkHandler;
import yourstageskybox.skybox.SkyboxManager;
import yourstageskybox.skybox.SkyboxState;
import yourstageskybox.skybox.TransitionState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * /yourstageskybox 命令 —— 1.20.1 Brigadier 版本。别名：/yss
 */
public class CommandYourStageSkybox {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("yourstageskybox")
                .requires(src -> src.hasPermission(2))
                .then(setCommand())
                .then(clearCommand())
                .then(alphaCommand())
                .then(durationCommand())
                .then(infoCommand())
                .then(listCommand())
                .then(reloadCommand());

        LiteralCommandNode<CommandSourceStack> node = dispatcher.register(root);
        dispatcher.register(Commands.literal("yss")
                .requires(src -> src.hasPermission(2))
                .redirect(node));
    }

    // ==================== set ====================

    private static LiteralArgumentBuilder<CommandSourceStack> setCommand() {
        return Commands.literal("set")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> executeSet(ctx, StringArgumentType.getString(ctx, "name"),
                                1.0f, 2000L, null, 0))
                        .then(Commands.argument("alpha", DoubleArgumentType.doubleArg(0))
                                .executes(ctx -> executeSet(ctx, StringArgumentType.getString(ctx, "name"),
                                        (float) DoubleArgumentType.getDouble(ctx, "alpha"),
                                        2000L, null, 0))
                                .then(Commands.argument("duration", DoubleArgumentType.doubleArg(0))
                                        .executes(ctx -> executeSet(ctx, StringArgumentType.getString(ctx, "name"),
                                                (float) DoubleArgumentType.getDouble(ctx, "alpha"),
                                                (long) (DoubleArgumentType.getDouble(ctx, "duration") * 1000),
                                                null, 0))
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .executes(ctx -> executeSetFull(ctx)))
                                        .then(Commands.argument("dim", IntegerArgumentType.integer())
                                                .executes(ctx -> executeSet(ctx, StringArgumentType.getString(ctx, "name"),
                                                        (float) DoubleArgumentType.getDouble(ctx, "alpha"),
                                                        (long) (DoubleArgumentType.getDouble(ctx, "duration") * 1000),
                                                        null, IntegerArgumentType.getInteger(ctx, "dim")))))));
    }

    private static int executeSet(CommandContext<CommandSourceStack> ctx, String name, float alpha,
                                   long durMs, Collection<ServerPlayer> players, int dim)
            throws CommandSyntaxException {
        if (players == null)
            players = ctx.getSource().getServer().getPlayerList().getPlayers();
        float clampedA = Math.max(0, Math.min(1, alpha));
        if (durMs < 0) durMs = 2000L;

        for (ServerPlayer p : players) {
            SkyboxState st = new SkyboxState(name, clampedA, durMs);
            SkyboxManager.setActiveSkyboxForPlayer(p.getUUID(), dim, st);
            NetworkHandler.sendSkyboxSyncToPlayer(p, dim, st);
        }

        String extra = clampedA < 0.999f ? String.format(" alpha=%.2f", clampedA) : "";
        if (durMs > 0 && durMs != 2000L) extra += String.format(" dur=%.1fs", durMs / 1000f);
        msg(ctx, "yourstageskybox.command.set.success", name + extra, describePlayers(players), dim);
        return 1;
    }

    private static int executeSetFull(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        float alpha = (float) DoubleArgumentType.getDouble(ctx, "alpha");
        long durMs = (long) (DoubleArgumentType.getDouble(ctx, "duration") * 1000);
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "player");
        int dim = IntegerArgumentType.getInteger(ctx, "dim");
        return executeSet(ctx, name, alpha, durMs, players, dim);
    }

    // ==================== clear ====================

    private static LiteralArgumentBuilder<CommandSourceStack> clearCommand() {
        return Commands.literal("clear")
                .executes(ctx -> executeClear(ctx, null, 0))
                .then(Commands.argument("player", EntityArgument.players())
                        .executes(ctx -> executeClear(ctx, EntityArgument.getPlayers(ctx, "player"), 0))
                        .then(Commands.argument("dim", IntegerArgumentType.integer())
                                .executes(ctx -> executeClear(ctx,
                                        EntityArgument.getPlayers(ctx, "player"),
                                        IntegerArgumentType.getInteger(ctx, "dim")))));
    }

    private static int executeClear(CommandContext<CommandSourceStack> ctx,
                                     Collection<ServerPlayer> players, int dim)
            throws CommandSyntaxException {
        if (players == null) players = ctx.getSource().getServer().getPlayerList().getPlayers();
        for (ServerPlayer p : players) {
            SkyboxManager.clearActiveSkyboxForPlayer(p.getUUID(), dim);
            NetworkHandler.sendSkyboxClearToPlayer(p, dim);
        }
        msg(ctx, "yourstageskybox.command.clear.success", describePlayers(players), dim);
        return 1;
    }

    // ==================== alpha ====================

    private static LiteralArgumentBuilder<CommandSourceStack> alphaCommand() {
        return Commands.literal("alpha")
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 1))
                        .executes(ctx -> executeAlpha(ctx,
                                (float) DoubleArgumentType.getDouble(ctx, "value"), null, 0))
                        .then(Commands.argument("player", EntityArgument.players())
                                .executes(ctx -> executeAlpha(ctx,
                                        (float) DoubleArgumentType.getDouble(ctx, "value"),
                                        EntityArgument.getPlayers(ctx, "player"), 0))
                                .then(Commands.argument("dim", IntegerArgumentType.integer())
                                        .executes(ctx -> executeAlpha(ctx,
                                                (float) DoubleArgumentType.getDouble(ctx, "value"),
                                                EntityArgument.getPlayers(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "dim"))))));
    }

    private static int executeAlpha(CommandContext<CommandSourceStack> ctx, float alpha,
                                     Collection<ServerPlayer> players, int dim)
            throws CommandSyntaxException {
        if (players == null) players = ctx.getSource().getServer().getPlayerList().getPlayers();
        for (ServerPlayer p : players) {
            SkyboxManager.setSkyboxAlphaForPlayer(p.getUUID(), dim, alpha);
            SkyboxState st = SkyboxManager.getSkyboxStateForPlayer(p.getUUID(), dim);
            if (st != null) NetworkHandler.sendSkyboxSyncToPlayer(p, dim, st);
        }
        msg(ctx, "yourstageskybox.command.alpha.success", alpha, describePlayers(players), dim);
        return 1;
    }

    // ==================== duration ====================

    private static LiteralArgumentBuilder<CommandSourceStack> durationCommand() {
        return Commands.literal("duration")
                .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0.1))
                        .executes(ctx -> executeDuration(ctx,
                                DoubleArgumentType.getDouble(ctx, "seconds"), null, 0))
                        .then(Commands.argument("player", EntityArgument.players())
                                .executes(ctx -> executeDuration(ctx,
                                        DoubleArgumentType.getDouble(ctx, "seconds"),
                                        EntityArgument.getPlayers(ctx, "player"), 0))
                                .then(Commands.argument("dim", IntegerArgumentType.integer())
                                        .executes(ctx -> executeDuration(ctx,
                                                DoubleArgumentType.getDouble(ctx, "seconds"),
                                                EntityArgument.getPlayers(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "dim"))))));
    }

    private static int executeDuration(CommandContext<CommandSourceStack> ctx, double durSec,
                                        Collection<ServerPlayer> players, int dim)
            throws CommandSyntaxException {
        long durationMs = (long) (durSec * 1000L);
        if (players == null) players = ctx.getSource().getServer().getPlayerList().getPlayers();
        for (ServerPlayer p : players) {
            SkyboxState st = SkyboxManager.getSkyboxStateForPlayer(p.getUUID(), dim);
            if (st != null) {
                st.durationMs = durationMs;
                NetworkHandler.sendSkyboxSyncToPlayer(p, dim, st);
            }
        }
        msg(ctx, "yourstageskybox.command.duration.success", durSec, describePlayers(players), dim);
        return 1;
    }

    // ==================== info / list / reload ====================

    private static LiteralArgumentBuilder<CommandSourceStack> infoCommand() {
        return Commands.literal("info")
                .executes(ctx -> executeInfo(ctx, 0))
                .then(Commands.argument("dim", IntegerArgumentType.integer())
                        .executes(ctx -> executeInfo(ctx, IntegerArgumentType.getInteger(ctx, "dim"))));
    }

    private static int executeInfo(CommandContext<CommandSourceStack> ctx, int dim) {
        if (ctx.getSource().getLevel().isClientSide()) {
            SkyboxState st = SkyboxManager.getSkyboxState(dim);
            TransitionState tr = SkyboxManager.getTransition(dim);
            if (st == null || st.isVanilla()) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        YourStageSkyboxLocale.format("yourstageskybox.command.info.vanilla", dim)), false);
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        YourStageSkyboxLocale.format("yourstageskybox.command.info.format", dim, st.toString())), false);
            }
            if (tr != null) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        YourStageSkyboxLocale.format("yourstageskybox.command.info.transition", tr.toString())), false);
            }
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    YourStageSkyboxLocale.format("yourstageskybox.command.info.server", dim)), false);
        }
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> listCommand() {
        return Commands.literal("list").executes(ctx -> {
            Set<String> skyboxes = SkyboxManager.getRegisteredSkyboxes();
            if (skyboxes.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        YourStageSkyboxLocale.translate("yourstageskybox.command.list.empty")), false);
                ctx.getSource().sendSuccess(() -> Component.literal(
                        YourStageSkyboxLocale.translate("yourstageskybox.command.list.hint")), false);
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        YourStageSkyboxLocale.format("yourstageskybox.command.list.header", skyboxes.size())), false);
                for (String name : skyboxes)
                    ctx.getSource().sendSuccess(() -> Component.literal("  §f- " + name), false);
            }
            return 1;
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> reloadCommand() {
        return Commands.literal("reload").executes(ctx -> {
            SkyboxManager.reloadSkyboxes();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    YourStageSkyboxLocale.translate("yourstageskybox.command.reload.success")), true);
            return 1;
        });
    }

    private static void msg(CommandContext<CommandSourceStack> ctx, String key, Object... args) {
        ctx.getSource().sendSuccess(() -> Component.literal(YourStageSkyboxLocale.format(key, args)), true);
    }

    private static String describePlayers(Collection<ServerPlayer> targets) {
        if (targets.isEmpty()) return YourStageSkyboxLocale.translate("yourstageskybox.command.target.none");
        List<String> names = new ArrayList<>();
        int i = 0;
        for (ServerPlayer p : targets) {
            if (i >= 3) break;
            names.add(p.getName().getString());
            i++;
        }
        String result = String.join(", ", names);
        if (targets.size() > 3)
            result += YourStageSkyboxLocale.format("yourstageskybox.command.target.more", targets.size());
        return result;
    }
}
