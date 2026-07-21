package yourstageskybox.command;

import yourstageskybox.YourStageSkyboxLocale;
import yourstageskybox.network.NetworkHandler;
import yourstageskybox.skybox.SkyboxManager;
import yourstageskybox.skybox.SkyboxState;
import yourstageskybox.skybox.TransitionState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.*;

public class CommandYourStageSkybox extends CommandBase {

    private static final String NAME = "yourstageskybox";
    private static final List<String> ALIASES = Collections.singletonList("yss");

    @Override
    public String getName() { return NAME; }

    @Override
    public String getUsage(ICommandSender sender) {
        return YourStageSkyboxLocale.translate("yourstageskybox.command.usage");
    }

    @Override
    public List<String> getAliases() { return ALIASES; }

    @Override
    public int getRequiredPermissionLevel() { return 2; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length < 1)
            throw new WrongUsageException(YourStageSkyboxLocale.translate("yourstageskybox.command.usage"));
        switch (args[0].toLowerCase()) {
            case "set":      executeSet(server, sender, args); break;
            case "clear":    executeClear(server, sender, args); break;
            case "alpha":    executeAlpha(server, sender, args); break;
            case "duration": executeDuration(server, sender, args); break;
            case "info":     executeInfo(sender, args); break;
            case "list":     executeList(sender); break;
            case "reload":   executeReload(sender); break;
            default: throw new WrongUsageException(YourStageSkyboxLocale.translate("yourstageskybox.command.usage"));
        }
    }

    // === set ===

    private void executeSet(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length < 2)
            throw new WrongUsageException(YourStageSkyboxLocale.translate("yourstageskybox.command.set.usage"));

        String skyboxName = args[1];
        Float alpha = null; Long durationMs = null; String playerSpec = null; Integer dim = null;
        boolean sawPlayer = false;

        for (int i = 2; i < args.length; i++) {
            String token = args[i]; if (token.isEmpty()) continue;
            if (!sawPlayer && isFloat(token)) {
                float val = Float.parseFloat(token);
                if (alpha == null) alpha = clamp(val);
                else if (durationMs == null) durationMs = (long)(Math.max(0.1f, val) * 1000L);
                else dim = (int) val;
            } else if (!sawPlayer && (token.startsWith("@") || isPlayerName(server, token))) {
                playerSpec = token; sawPlayer = true;
            } else if (sawPlayer && isFloat(token)) {
                dim = (int) Float.parseFloat(token);
            }
        }
        if (alpha == null) alpha = 1.0f;
        if (durationMs == null) durationMs = -1L;
        if (dim == null) dim = 0;

        List<EntityPlayerMP> targets = resolvePlayers(server, sender, playerSpec);
        for (EntityPlayerMP p : targets) {
            SkyboxState st = new SkyboxState(skyboxName, alpha);
            if (durationMs > 0) st.durationMs = durationMs;
            SkyboxManager.setActiveSkyboxForPlayer(p.getUniqueID(), dim, st);
            NetworkHandler.sendSkyboxSyncToPlayer(p, dim, st);
        }

        String extra = alpha < 0.999f ? String.format(" alpha=%.2f", alpha) : "";
        if (durationMs > 0) extra += String.format(" dur=%.1fs", durationMs / 1000f);
        msg(sender, "yourstageskybox.command.set.success", skyboxName + extra, describeTargets(targets), dim);
    }

    // === clear ===

    private void executeClear(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        String playerSpec = (args.length > 1) ? args[1] : null;
        int dim = (args.length > 2 && isFloat(args[2])) ? parseInt(args[2]) : 0;
        List<EntityPlayerMP> targets = resolvePlayers(server, sender, playerSpec);
        for (EntityPlayerMP p : targets) {
            SkyboxManager.clearActiveSkyboxForPlayer(p.getUniqueID(), dim);
            NetworkHandler.sendSkyboxClearToPlayer(p, dim);
        }
        msg(sender, "yourstageskybox.command.clear.success", describeTargets(targets), dim);
    }

    // === alpha ===

    private void executeAlpha(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length < 2)
            throw new WrongUsageException(YourStageSkyboxLocale.translate("yourstageskybox.command.alpha.usage"));
        float alpha = clamp((float) parseDouble(args[1]));
        String playerSpec = (args.length > 2) ? args[2] : null;
        int dim = (args.length > 3 && isFloat(args[3])) ? parseInt(args[3]) : 0;
        List<EntityPlayerMP> targets = resolvePlayers(server, sender, playerSpec);
        for (EntityPlayerMP p : targets) {
            SkyboxManager.setSkyboxAlphaForPlayer(p.getUniqueID(), dim, alpha);
            SkyboxState st = SkyboxManager.getSkyboxStateForPlayer(p.getUniqueID(), dim);
            if (st != null) NetworkHandler.sendSkyboxSyncToPlayer(p, dim, st);
        }
        msg(sender, "yourstageskybox.command.alpha.success", alpha, describeTargets(targets), dim);
    }

    // === duration ===

    private void executeDuration(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length < 2)
            throw new WrongUsageException(YourStageSkyboxLocale.translate("yourstageskybox.command.duration.usage"));
        float durSec = (float) Math.max(0.1, parseDouble(args[1]));
        long durationMs = (long)(durSec * 1000L);
        String playerSpec = (args.length > 2) ? args[2] : null;
        int dim = (args.length > 3 && isFloat(args[3])) ? parseInt(args[3]) : 0;
        List<EntityPlayerMP> targets = resolvePlayers(server, sender, playerSpec);
        for (EntityPlayerMP p : targets) {
            SkyboxState st = SkyboxManager.getSkyboxStateForPlayer(p.getUniqueID(), dim);
            if (st != null) {
                st.durationMs = durationMs;
                SkyboxManager.setActiveSkyboxForPlayer(p.getUniqueID(), dim, st);
                NetworkHandler.sendSkyboxSyncToPlayer(p, dim, st);
            }
        }
        msg(sender, "yourstageskybox.command.duration.success", durSec, describeTargets(targets), dim);
    }

    // === info ===

    private void executeInfo(ICommandSender sender, String[] args) throws CommandException {
        int dim = (args.length > 1 && isFloat(args[1])) ? parseInt(args[1]) : 0;
        if (sender.getEntityWorld().isRemote) {
            SkyboxState st = SkyboxManager.getSkyboxState(dim);
            TransitionState tr = SkyboxManager.getTransition(dim);
            if (st == null || st.isVanilla()) {
                sender.sendMessage(new TextComponentString(
                        YourStageSkyboxLocale.format("yourstageskybox.command.info.vanilla", dim)));
            } else {
                sender.sendMessage(new TextComponentString(
                        YourStageSkyboxLocale.format("yourstageskybox.command.info.format", dim, st.toString())));
            }
            if (tr != null) {
                sender.sendMessage(new TextComponentString(
                        YourStageSkyboxLocale.format("yourstageskybox.command.info.transition", tr.toString())));
            }
        } else {
            sender.sendMessage(new TextComponentString(
                    YourStageSkyboxLocale.format("yourstageskybox.command.info.server", dim)));
        }
    }

    // === list ===

    private void executeList(ICommandSender sender) {
        Set<String> skyboxes = SkyboxManager.getRegisteredSkyboxes();
        if (skyboxes.isEmpty()) {
            sender.sendMessage(new TextComponentString(YourStageSkyboxLocale.translate("yourstageskybox.command.list.empty")));
            sender.sendMessage(new TextComponentString(YourStageSkyboxLocale.translate("yourstageskybox.command.list.hint")));
            return;
        }
        String activeInDim = null;
        if (sender instanceof net.minecraft.entity.Entity)
            activeInDim = SkyboxManager.getActiveSkybox(
                    ((net.minecraft.entity.Entity) sender).world.provider.getDimension());
        sender.sendMessage(new TextComponentString(
                YourStageSkyboxLocale.format("yourstageskybox.command.list.header", skyboxes.size())));
        for (String name : skyboxes) {
            String marker = name.equals(activeInDim) ? YourStageSkyboxLocale.translate("yourstageskybox.command.list.active") : "";
            sender.sendMessage(new TextComponentString("  §f- " + name + marker));
        }
    }

    // === reload ===

    private void executeReload(ICommandSender sender) {
        SkyboxManager.reloadSkyboxes();
        sender.sendMessage(new TextComponentString(YourStageSkyboxLocale.translate("yourstageskybox.command.reload.success")));
    }

    // === helpers ===

    private static void msg(ICommandSender sender, String key, Object... args) {
        sender.sendMessage(new TextComponentString(YourStageSkyboxLocale.format(key, args)));
    }

    private static boolean isFloat(String s) {
        try { Float.parseFloat(s); return true; } catch (NumberFormatException e) { return false; }
    }

    private static float clamp(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }

    private static boolean isPlayerName(MinecraftServer server, String token) {
        if (token == null || token.isEmpty() || isFloat(token)) return false;
        for (EntityPlayerMP p : server.getPlayerList().getPlayers())
            if (p.getName().equalsIgnoreCase(token)) return true;
        return false;
    }

    private List<EntityPlayerMP> resolvePlayers(MinecraftServer server, ICommandSender sender, String spec)
            throws CommandException {
        if (spec == null || spec.isEmpty())
            return new ArrayList<>(server.getPlayerList().getPlayers());
        if (!spec.startsWith("@") && !isFloat(spec)) {
            for (EntityPlayerMP p : server.getPlayerList().getPlayers())
                if (p.getName().equalsIgnoreCase(spec)) return Collections.singletonList(p);
            throw new WrongUsageException(
                    YourStageSkyboxLocale.format("yourstageskybox.command.error.player_offline", spec));
        }
        return CommandBase.getPlayers(server, sender, spec);
    }

    private String describeTargets(List<EntityPlayerMP> targets) {
        if (targets.isEmpty()) return YourStageSkyboxLocale.translate("yourstageskybox.command.target.none");
        List<String> names = new ArrayList<>();
        for (int i = 0; i < Math.min(targets.size(), 3); i++) names.add(targets.get(i).getName());
        String result = String.join(", ", names);
        if (targets.size() > 3)
            result += YourStageSkyboxLocale.format("yourstageskybox.command.target.more", targets.size());
        return result;
    }

    // === tab ===

    private static final String[] SUBCOMMANDS = {"set", "clear", "alpha", "duration", "info", "list", "reload"};
    private static final String[] ALPHA_VALUES = {"1.0", "0.75", "0.5", "0.25", "0.0"};
    private static final String[] DURATION_VALUES = {"2.0", "0.5", "1.0", "3.0", "5.0"};
    private static final String[] DIM_VALUES = {"0", "-1", "1"};

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                           String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1)
            return getListOfStringsMatchingLastWord(args, SUBCOMMANDS);
        String cmd = args[0].toLowerCase();
        // subcommand arg-2
        if (args.length == 2) {
            switch (cmd) {
                case "set":  return getListOfStringsMatchingLastWord(args, SkyboxManager.getRegisteredSkyboxes());
                case "alpha": return getListOfStringsMatchingLastWord(args, ALPHA_VALUES);
                case "duration": return getListOfStringsMatchingLastWord(args, DURATION_VALUES);
                case "info": case "clear": return getListOfStringsMatchingLastWord(args, DIM_VALUES);
            }
        }
        // subcommand arg-3
        if (args.length == 3) {
            switch (cmd) {
                case "set": return getListOfStringsMatchingLastWord(args, ALPHA_VALUES);
                case "alpha": case "duration":
                    return getListOfStringsMatchingLastWord(args, playerNames(server, "@a", "@p", "@r"));
                case "clear": return getListOfStringsMatchingLastWord(args, DIM_VALUES);
            }
        }
        // subcommand arg-4
        if (args.length == 4) {
            switch (cmd) {
                case "set": return getListOfStringsMatchingLastWord(args, DURATION_VALUES);
                case "alpha": case "duration": return getListOfStringsMatchingLastWord(args, DIM_VALUES);
            }
        }
        // subcommand arg-5
        if (args.length == 5) {
            if ("set".equals(cmd))
                return getListOfStringsMatchingLastWord(args, playerNames(server, "@a", "@p", "@r"));
        }
        // subcommand arg-6
        if (args.length == 6) {
            if ("set".equals(cmd))
                return getListOfStringsMatchingLastWord(args, DIM_VALUES);
        }
        return Collections.emptyList();
    }

    private static String[] playerNames(MinecraftServer server, String... selectors) {
        List<String> names = new ArrayList<>();
        Collections.addAll(names, selectors);
        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) names.add(p.getName());
        return names.toArray(new String[0]);
    }
}
