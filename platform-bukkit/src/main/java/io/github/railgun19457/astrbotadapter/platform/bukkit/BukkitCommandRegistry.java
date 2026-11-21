package io.github.railgun19457.astrbotadapter.platform.bukkit;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformCommandRegistry;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformRuntimeControl;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class BukkitCommandRegistry implements PlatformCommandRegistry, CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private PlatformRuntimeControl runtime;

    public BukkitCommandRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void registerCoreCommands(PlatformRuntimeControl runtimeControl) {
        this.runtime = runtimeControl;
        PluginCommand command = plugin.getCommand("astrbot");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    @Override
    public void unregisterAll() {
        this.runtime = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (runtime == null) {
            sender.sendMessage("AstrBotAdapter is not ready");
            return true;
        }
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sender.sendMessage("/" + label + " status");
            sender.sendMessage("/" + label + " reload");
            sender.sendMessage("/" + label + " connections");
            sender.sendMessage("/" + label + " auth create [target]");
            sender.sendMessage("/" + label + " auth list");
            sender.sendMessage("/" + label + " auth revoke <pairing_id>");
            sender.sendMessage("/" + label + " auth revoke key <domain> <key_id>");
            sender.sendMessage("/" + label + " auth key regen");
            return true;
        }
        if ("status".equalsIgnoreCase(args[0])) {
            sender.sendMessage("AstrBotAdapter running=" + runtime.running() + " mode=" + runtime.modeName() + " server_id=" + runtime.serverId());
            sender.sendMessage("Fingerprint: " + runtime.localFingerprint());
            sender.sendMessage("Capabilities: " + String.join(", ", runtime.capabilities()));
            return true;
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            if (!requireAdmin(sender)) {
                return true;
            }
            sender.sendMessage(runtime.reloadConfig());
            return true;
        }
        if ("connections".equalsIgnoreCase(args[0])) {
            if (!requireAdmin(sender)) {
                return true;
            }
            sender.sendMessage(runtime.connectionsStatus());
            return true;
        }
        if ("auth".equalsIgnoreCase(args[0])) {
            if (!requireAdmin(sender)) {
                return true;
            }
            return handleAuth(sender, args);
        }
        sender.sendMessage("Unknown subcommand. Use /" + label + " help");
        return true;
    }

    private boolean handleAuth(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /astrbot auth <create|list|revoke|key>");
            return true;
        }
        if ("create".equalsIgnoreCase(args[1])) {
            String target = args.length >= 3 ? args[2] : null;
            sender.sendMessage("Pairing: " + runtime.createPairingCode(target));
            return true;
        }
        if ("list".equalsIgnoreCase(args[1])) {
            sender.sendMessage(runtime.listPairingCodes());
            sender.sendMessage(runtime.listTrustedKeys());
            return true;
        }
        if ("revoke".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                sender.sendMessage("Usage: /astrbot auth revoke <pairing_id> OR /astrbot auth revoke key <domain> <key_id>");
                return true;
            }
            if ("key".equalsIgnoreCase(args[2])) {
                if (args.length < 5) {
                    sender.sendMessage("Usage: /astrbot auth revoke key <domain> <key_id>");
                    return true;
                }
                sender.sendMessage(runtime.revokeTrustedKey(args[3], args[4]) ? "Trusted key revoked" : "Trusted key not found");
                return true;
            }
            sender.sendMessage(runtime.revokePairingCode(args[2]) ? "Pairing revoked" : "Pairing ID not found");
            return true;
        }
        if ("key".equalsIgnoreCase(args[1])) {
            if (args.length >= 3 && "regen".equalsIgnoreCase(args[2])) {
                sender.sendMessage(runtime.regenerateLocalKey());
                return true;
            }
            sender.sendMessage("Usage: /astrbot auth key regen");
            return true;
        }
        sender.sendMessage("Unknown auth subcommand");
        return true;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("astrbot.admin")) {
            return true;
        }
        sender.sendMessage("You do not have permission: astrbot.admin");
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("help", "status", "reload", "connections", "auth"), args[0]);
        }
        if (args.length == 2 && "auth".equalsIgnoreCase(args[0])) {
            return filter(List.of("create", "list", "revoke", "key"), args[1]);
        }
        if (args.length == 3 && "auth".equalsIgnoreCase(args[0]) && "revoke".equalsIgnoreCase(args[1])) {
            return filter(List.of("key"), args[2]);
        }
        if (args.length == 3 && "auth".equalsIgnoreCase(args[0]) && "key".equalsIgnoreCase(args[1])) {
            return filter(List.of("regen"), args[2]);
        }
        if (args.length == 4 && "auth".equalsIgnoreCase(args[0]) && "revoke".equalsIgnoreCase(args[1]) && "key".equalsIgnoreCase(args[2])) {
            return filter(List.of("astrbot", "velocity", "backends", "standalone"), args[3]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.startsWith(prefix.toLowerCase())) {
                result.add(value);
            }
        }
        return result;
    }
}
