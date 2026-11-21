package io.github.railgun19457.astrbotadapter.platform.fabric;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformCommandRegistry;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformRuntimeControl;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class FabricCommandRegistry implements PlatformCommandRegistry {
    private static volatile PlatformRuntimeControl runtime;
    private static boolean dispatcherRegistered;

    static synchronized void registerDispatcher() {
        if (dispatcherRegistered) {
            return;
        }
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(command("astrbot"));
            dispatcher.register(command("abot"));
            dispatcher.register(command("astra"));
        });
        dispatcherRegistered = true;
    }

    @Override
    public void registerCoreCommands(PlatformRuntimeControl runtimeControl) {
        runtime = runtimeControl;
    }

    @Override
    public void unregisterAll() {
        runtime = null;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(String label) {
        return Commands.literal(label)
            .executes(context -> execute(context.getSource(), label, new String[0]))
            .then(Commands.argument("args", StringArgumentType.greedyString())
                .suggests((context, builder) -> {
                    suggest(builder.getRemaining(), builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> execute(context.getSource(), label, split(StringArgumentType.getString(context, "args")))));
    }

    private static int execute(CommandSourceStack source, String label, String[] args) {
        PlatformRuntimeControl current = runtime;
        if (current == null) {
            send(source, "AstrBotAdapter is not ready");
            return 1;
        }
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            send(source, "/" + label + " status");
            send(source, "/" + label + " reload");
            send(source, "/" + label + " connections");
            send(source, "/" + label + " auth create [target]");
            send(source, "/" + label + " auth list");
            send(source, "/" + label + " auth revoke <pairing_id>");
            send(source, "/" + label + " auth revoke key <domain> <key_id>");
            send(source, "/" + label + " auth key regen");
            return 1;
        }
        if ("status".equalsIgnoreCase(args[0])) {
            send(source, "AstrBotAdapter running=" + current.running() + " mode=" + current.modeName() + " server_id=" + current.serverId());
            send(source, "Fingerprint: " + current.localFingerprint());
            send(source, "Capabilities: " + String.join(", ", current.capabilities()));
            return 1;
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            if (!requireAdmin(source)) {
                return 0;
            }
            send(source, current.reloadConfig());
            return 1;
        }
        if ("connections".equalsIgnoreCase(args[0])) {
            if (!requireAdmin(source)) {
                return 0;
            }
            send(source, current.connectionsStatus());
            return 1;
        }
        if ("auth".equalsIgnoreCase(args[0])) {
            if (!requireAdmin(source)) {
                return 0;
            }
            return handleAuth(source, current, args);
        }
        send(source, "Unknown subcommand. Use /" + label + " help");
        return 0;
    }

    private static int handleAuth(CommandSourceStack source, PlatformRuntimeControl current, String[] args) {
        if (args.length < 2) {
            send(source, "Usage: /astrbot auth <create|list|revoke|key>");
            return 0;
        }
        if ("create".equalsIgnoreCase(args[1])) {
            String target = args.length >= 3 ? args[2] : null;
            send(source, "Pairing: " + current.createPairingCode(target));
            return 1;
        }
        if ("list".equalsIgnoreCase(args[1])) {
            send(source, current.listPairingCodes());
            send(source, current.listTrustedKeys());
            return 1;
        }
        if ("revoke".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                send(source, "Usage: /astrbot auth revoke <pairing_id> OR /astrbot auth revoke key <domain> <key_id>");
                return 0;
            }
            if ("key".equalsIgnoreCase(args[2])) {
                if (args.length < 5) {
                    send(source, "Usage: /astrbot auth revoke key <domain> <key_id>");
                    return 0;
                }
                send(source, current.revokeTrustedKey(args[3], args[4]) ? "Trusted key revoked" : "Trusted key not found");
                return 1;
            }
            send(source, current.revokePairingCode(args[2]) ? "Pairing revoked" : "Pairing ID not found");
            return 1;
        }
        if ("key".equalsIgnoreCase(args[1])) {
            if (args.length >= 3 && "regen".equalsIgnoreCase(args[2])) {
                send(source, current.regenerateLocalKey());
                return 1;
            }
            send(source, "Usage: /astrbot auth key regen");
            return 0;
        }
        send(source, "Unknown auth subcommand");
        return 0;
    }

    private static boolean requireAdmin(CommandSourceStack source) {
        if (source.permissions().hasPermission(Permissions.COMMANDS_OWNER)) {
            return true;
        }
        source.sendFailure(Component.literal("You do not have permission: astrbot.admin"));
        return false;
    }

    private static String[] split(String value) {
        if (value == null || value.isBlank()) {
            return new String[0];
        }
        return value.trim().split("\\s+");
    }

    private static void suggest(String remaining, SuggestionConsumer consumer) {
        String normalized = remaining.toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        if (!normalized.contains(" ")) {
            suggestions.addAll(List.of("help", "status", "reload", "connections", "auth"));
        } else if (normalized.startsWith("auth ")) {
            suggestions.addAll(List.of("auth create", "auth list", "auth revoke", "auth key", "auth revoke key", "auth key regen"));
        }
        for (String suggestion : suggestions) {
            if (suggestion.startsWith(normalized)) {
                consumer.suggest(suggestion);
            }
        }
    }

    private static void send(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }

    @FunctionalInterface
    private interface SuggestionConsumer {
        void suggest(String suggestion);
    }
}
