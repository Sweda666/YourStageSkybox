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
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;

import java.util.*;

/**
 * /yourstageskybox 命令 —— 1.16.5 Brigadier 版本。
 * 别名：/yss
 */
public class CommandYourStageSkybox {

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        LiteralArgumentBuilder<CommandSource> root = Commands.literal("yourstageskybox")
                .requires(src -> src.hasPermission(2))
                .then(setCommand())
                .then(clearCommand())
                .then(alphaCommand())
                .then(durationCommand())
                .then(infoCommand())
                .then(listCommand())
                .then(reloadCommand());

        LiteralCommandNode<CommandSource> node = dispatcher.register(root);
        // 别名 /yss
        dispatcher.register(Commands.literal("yss")
                .requires(src -> src.hasPermission(2))
                .redirect(node));
    }

    // ==================== set ====================

    private static LiteralArgumentBuilder<CommandSource> setCommand() {
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
                                                .executes(ctx -> executeSet(ctx, StringArgumentType.getString(ctx, "name"),
                                                        (float) DoubleArgumentType.getDouble(ctx, "alpha"),
                                                        (long) (DoubleArgumentType.getDouble(ctx, "duration") * 1000),
                                                        EntityArgument.getPlayers(ctx, "player"), 0))
                                                .then(Commands.argument("dim", IntegerArgumentType.integer())
                                                        .executes(ctx -> executeSetFull(ctx)))
                                        )
                                )
                        )
                );
    }

    private static int executeSet(CommandContext<CommandSource> ctx, String name, float alpha,
                                   long durationMs, Collection<ServerPlayerEntity> players, int dim)
            throws CommandSyntaxException {
        if (players == null) {
            players = ctx.getSource().getServer().getPlayerList().getPlayers();
        }
        for (ServerPlayerEntity p : players) {
            SkyboxState st = new SkyboxState(name, alpha);
            if (durationMs > 0) st.durationMs = durationMs;
            SkyboxManager.setActiveSkyboxForPlayer(p.getUUID(), dim, st);
            NetworkHandler.sendSkyboxSyncToPlayer(p, dim, st);
        }
        String extra = alpha < 0.999f ? String.format(" alpha=%.2f", alpha) : "";
        if (durationMs > 0) extra += String.format(" dur=%.1fs", durationMs / 1000f);
        msg(ctx, "yourstageskybox.command.set.success", name + extra, describePlayers(players), dim);
        return 1;
    }

    private static int executeSetFull(CommandContext<CommandSource> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        float alpha = (float) DoubleArgumentType.getDouble(ctx, "alpha");
        long durationMs = (long) (DoubleArgumentType.getDouble(ctx, "duration") * 1000);
        Collection<ServerPlayerEntity> players = EntityArgument.getPlayers(ctx, "player");
        int dim = IntegerArgumentType.getInteger(ctx, "dim");
        return executeSet(ctx, name, alpha, durationMs, players, dim);
    }

    // ==================== clear ====================

    private static LiteralArgumentBuilder<CommandSource> clearCommand() {
        return Commands.literal("clear")
                .executes(ctx -> executeClear(ctx, null, 0))
                .then(Commands.argument("player", EntityArgument.players())
                        .executes(ctx -> executeClear(ctx, EntityArgument.getPlayers(ctx, "player"), 0))
                        .then(Commands.argument("dim", IntegerArgumentType.integer())
                                .executes(ctx -> executeClear(ctx,
                                        EntityArgument.getPlayers(ctx, "player"),
                                        IntegerArgumentType.getInteger(ctx, "dim")))));
    }

    private static int executeClear(CommandContext<CommandSource> ctx,
                                     Collection<ServerPlayerEntity> players, int dim)
            throws CommandSyntaxException {
        if (players == null) {
            players = ctx.getSource().getServer().getPlayerList().getPlayers();
        }
        for (ServerPlayerEntity p : players) {
            SkyboxManager.clearActiveSkyboxForPlayer(p.getUUID(), dim);
            NetworkHandler.sendSkyboxClearToPlayer(p, dim);
        }
        msg(ctx, "yourstageskybox.command.clear.success", describePlayers(players), dim);
        return 1;
    }

    // ==================== alpha ====================

    private static LiteralArgumentBuilder<CommandSource> alphaCommand() {
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

    private static int executeAlpha(CommandContext<CommandSource> ctx, float alpha,
                                     Collection<ServerPlayerEntity> players, int dim)
            throws CommandSyntaxException {
        if (players == null) {
            players = ctx.getSource().getServer().getPlayerList().getPlayers();
        }
        for (ServerPlayerEntity p : players) {
            SkyboxManager.setSkyboxAlphaForPlayer(p.getUUID(), dim, alpha);
            SkyboxState st = SkyboxManager.getSkyboxStateForPlayer(p.getUUID(), dim);
            if (st != null) NetworkHandler.sendSkyboxSyncToPlayer(p, dim, st);
        }
        msg(ctx, "yourstageskybox.command.alpha.success", alpha, describePlayers(players), dim);
        return 1;
    }

    // ==================== duration ====================

    private static LiteralArgumentBuilder<CommandSource> durationCommand() {
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

    private static int executeDuration(CommandContext<CommandSource> ctx, double durSec,
                                        Collection<ServerPlayerEntity> players, int dim)
            throws CommandSyntaxException {
        long durationMs = (long) (durSec * 1000L);
        if (players == null) {
            players = ctx.getSource().getServer().getPlayerList().getPlayers();
        }
        for (ServerPlayerEntity p : players) {
            SkyboxState st = SkyboxManager.getSkyboxStateForPlayer(p.getUUID(), dim);
            if (st != null) {
                st.durationMs = durationMs;
                SkyboxManager.setActiveSkyboxForPlayer(p.getUUID(), dim, st);
                NetworkHandler.sendSkyboxSyncToPlayer(p, dim, st);
            }
        }
        msg(ctx, "yourstageskybox.command.duration.success", durSec, describePlayers(players), dim);
        return 1;
    }

    // ==================== info ====================

    private static LiteralArgumentBuilder<CommandSource> infoCommand() {
        return Commands.literal("info")
                .executes(ctx -> executeInfo(ctx, 0))
                .then(Commands.argument("dim", IntegerArgumentType.integer())
                        .executes(ctx -> executeInfo(ctx, IntegerArgumentType.getInteger(ctx, "dim"))));
    }

    private static int executeInfo(CommandContext<CommandSource> ctx, int dim) {
        // 客户端查询
        if (ctx.getSource().getLevel().isClientSide()) {
            SkyboxState st = SkyboxManager.getSkyboxState(dim);
            TransitionState tr = SkyboxManager.getTransition(dim);
            if (st == null || st.isVanilla()) {
                ctx.getSource().sendSuccess(
                        new StringTextComponent(YourStageSkyboxLocale.format(
                                "yourstageskybox.command.info.vanilla", dim)), false);
            } else {
                ctx.getSource().sendSuccess(
                        new StringTextComponent(YourStageSkyboxLocale.format(
                                "yourstageskybox.command.info.format", dim, st.toString())), false);
            }
            if (tr != null) {
                ctx.getSource().sendSuccess(
                        new StringTextComponent(YourStageSkyboxLocale.format(
                                "yourstageskybox.command.info.transition", tr.toString())), false);
            }
        } else {
            ctx.getSource().sendSuccess(
                    new StringTextComponent(YourStageSkyboxLocale.format(
                            "yourstageskybox.command.info.server", dim)), false);
        }
        return 1;
    }

    // ==================== list ====================

    private static LiteralArgumentBuilder<CommandSource> listCommand() {
        return Commands.literal("list")
                .executes(CommandYourStageSkybox::executeList);
    }

    private static int executeList(CommandContext<CommandSource> ctx) {
        Set<String> skyboxes = SkyboxManager.getRegisteredSkyboxes();
        if (skyboxes.isEmpty()) {
            ctx.getSource().sendSuccess(
                    new StringTextComponent(YourStageSkyboxLocale.translate(
                            "yourstageskybox.command.list.empty")), false);
            ctx.getSource().sendSuccess(
                    new StringTextComponent(YourStageSkyboxLocale.translate(
                            "yourstageskybox.command.list.hint")), false);
            return 1;
        }
        ctx.getSource().sendSuccess(
                new StringTextComponent(YourStageSkyboxLocale.format(
                        "yourstageskybox.command.list.header", skyboxes.size())), false);
        for (String name : skyboxes) {
            ctx.getSource().sendSuccess(
                    new StringTextComponent("  §f- " + name), false);
        }
        return 1;
    }

    // ==================== reload ====================

    private static LiteralArgumentBuilder<CommandSource> reloadCommand() {
        return Commands.literal("reload")
                .executes(ctx -> {
                    SkyboxManager.reloadSkyboxes();
                    ctx.getSource().sendSuccess(
                            new StringTextComponent(YourStageSkyboxLocale.translate(
                                    "yourstageskybox.command.reload.success")), true);
                    return 1;
                });
    }

    // ==================== 工具 ====================

    private static void msg(CommandContext<CommandSource> ctx, String key, Object... args) {
        ctx.getSource().sendSuccess(
                new StringTextComponent(YourStageSkyboxLocale.format(key, args)), true);
    }

    private static String describePlayers(Collection<ServerPlayerEntity> targets) {
        if (targets.isEmpty()) return YourStageSkyboxLocale.translate("yourstageskybox.command.target.none");
        List<String> names = new ArrayList<>();
        int i = 0;
        for (ServerPlayerEntity p : targets) {
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
