package io.github.railgun19457.astrbotadapter.platform.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformCommandRegistry;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformRuntimeControl;
import net.kyori.adventure.text.Component;

import java.util.List;

final class VelocityCommandRegistry implements PlatformCommandRegistry, SimpleCommand {
    private final Object plugin;
    private final ProxyServer proxy;
    private PlatformRuntimeControl runtime;

    VelocityCommandRegistry(Object plugin, ProxyServer proxy) {
        this.plugin = plugin;
        this.proxy = proxy;
    }

    @Override
    public void registerCoreCommands(PlatformRuntimeControl runtimeControl) {
        this.runtime = runtimeControl;
        var meta = proxy.getCommandManager().metaBuilder("astrbot").aliases("abot", "astra").plugin(plugin).build();
        proxy.getCommandManager().register(meta, this);
    }

    @Override
    public void unregisterAll() {
        proxy.getCommandManager().unregister("astrbot");
        runtime = null;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (runtime == null) {
            source.sendMessage(Component.text("AstrBotAdapter is not ready"));
            return;
        }
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            source.sendMessage(Component.text("/astrbot status"));
            source.sendMessage(Component.text("/astrbot reload"));
            source.sendMessage(Component.text("/astrbot connections"));
            source.sendMessage(Component.text("/astrbot auth create [target]"));
            source.sendMessage(Component.text("/astrbot auth list"));
            source.sendMessage(Component.text("/astrbot auth revoke <pairing_id>"));
            source.sendMessage(Component.text("/astrbot auth revoke key <domain> <key_id>"));
            source.sendMessage(Component.text("/astrbot auth key regen"));
            return;
        }
        if ("status".equalsIgnoreCase(args[0])) {
            source.sendMessage(Component.text("AstrBotAdapter running=" + runtime.running() + " mode=" + runtime.modeName() + " server_id=" + runtime.serverId()));
            source.sendMessage(Component.text("Fingerprint: " + runtime.localFingerprint()));
            source.sendMessage(Component.text("Capabilities: " + String.join(", ", runtime.capabilities())));
            return;
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            if (!requireAdmin(source)) {
                return;
            }
            source.sendMessage(Component.text(runtime.reloadConfig()));
            return;
        }
        if ("connections".equalsIgnoreCase(args[0])) {
            if (!requireAdmin(source)) {
                return;
            }
            source.sendMessage(Component.text(runtime.connectionsStatus()));
            return;
        }
        if ("auth".equalsIgnoreCase(args[0])) {
            if (!requireAdmin(source)) {
                return;
            }
            handleAuth(source, args);
            return;
        }
        source.sendMessage(Component.text("Unknown subcommand"));
    }

    private void handleAuth(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(Component.text("Usage: /astrbot auth <create|list|revoke|key>"));
            return;
        }
        if ("create".equalsIgnoreCase(args[1])) {
            String target = args.length >= 3 ? args[2] : null;
            source.sendMessage(Component.text("Pairing: " + runtime.createPairingCode(target)));
        } else if ("list".equalsIgnoreCase(args[1])) {
            source.sendMessage(Component.text(runtime.listPairingCodes()));
            source.sendMessage(Component.text(runtime.listTrustedKeys()));
        } else if ("revoke".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                source.sendMessage(Component.text("Usage: /astrbot auth revoke <pairing_id> OR /astrbot auth revoke key <domain> <key_id>"));
                return;
            }
            if ("key".equalsIgnoreCase(args[2])) {
                if (args.length < 5) {
                    source.sendMessage(Component.text("Usage: /astrbot auth revoke key <domain> <key_id>"));
                    return;
                }
                source.sendMessage(Component.text(runtime.revokeTrustedKey(args[3], args[4]) ? "Trusted key revoked" : "Trusted key not found"));
                return;
            }
            source.sendMessage(Component.text(runtime.revokePairingCode(args[2]) ? "Pairing revoked" : "Pairing ID not found"));
        } else if ("key".equalsIgnoreCase(args[1])) {
            if (args.length >= 3 && "regen".equalsIgnoreCase(args[2])) {
                source.sendMessage(Component.text(runtime.regenerateLocalKey()));
                return;
            }
            source.sendMessage(Component.text("Usage: /astrbot auth key regen"));
        } else {
            source.sendMessage(Component.text("Unknown auth subcommand"));
        }
    }

    private boolean requireAdmin(CommandSource source) {
        if (source.hasPermission("astrbot.admin")) {
            return true;
        }
        source.sendMessage(Component.text("You do not have permission: astrbot.admin"));
        return false;
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            return List.of("help", "status", "reload", "connections", "auth");
        }
        if (args.length == 1) {
            return List.of("help", "status", "reload", "connections", "auth").stream().filter(value -> value.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && "auth".equalsIgnoreCase(args[0])) {
            return List.of("create", "list", "revoke", "key").stream().filter(value -> value.startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 3 && "auth".equalsIgnoreCase(args[0]) && "revoke".equalsIgnoreCase(args[1])) {
            return List.of("key").stream().filter(value -> value.startsWith(args[2].toLowerCase())).toList();
        }
        if (args.length == 3 && "auth".equalsIgnoreCase(args[0]) && "key".equalsIgnoreCase(args[1])) {
            return List.of("regen").stream().filter(value -> value.startsWith(args[2].toLowerCase())).toList();
        }
        if (args.length == 4 && "auth".equalsIgnoreCase(args[0]) && "revoke".equalsIgnoreCase(args[1]) && "key".equalsIgnoreCase(args[2])) {
            return List.of("astrbot", "velocity", "backends", "standalone").stream().filter(value -> value.startsWith(args[3].toLowerCase())).toList();
        }
        return List.of();
    }
}
