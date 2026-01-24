package io.github.railgun19457.astrbotadapter.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Astrbot 命令 Tab 补全
 */
public class AstrbotTabCompleter implements TabCompleter {

    private static final List<String> MAIN_COMMANDS = Arrays.asList(
            "help", "reload", "status", "token", "connections"
    );

    private static final List<String> TOKEN_SUBCOMMANDS = Arrays.asList(
            "show", "regen"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // 主命令补全
            String input = args[0].toLowerCase();
            completions = MAIN_COMMANDS.stream()
                    .filter(cmd -> cmd.startsWith(input))
                    .filter(cmd -> hasPermissionFor(sender, cmd))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            String mainCommand = args[0].toLowerCase();
            String input = args[1].toLowerCase();

            if (mainCommand.equals("token")) {
                // token 子命令补全
                completions = TOKEN_SUBCOMMANDS.stream()
                        .filter(cmd -> cmd.startsWith(input))
                        .collect(Collectors.toList());
            }
        }

        return completions;
    }

    /**
     * 检查是否有对应命令的权限
     */
    private boolean hasPermissionFor(CommandSender sender, String command) {
        switch (command) {
            case "help":
                return true;
            case "reload":
                return sender.hasPermission("astrbot.reload");
            case "status":
                return sender.hasPermission("astrbot.status");
            case "token":
                return sender.hasPermission("astrbot.token");
            case "connections":
                return sender.hasPermission("astrbot.connections");
            default:
                return true;
        }
    }
}
