package org.sylvarion.hudhelper.command;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.sylvarion.hudhelper.SylvarionHUDHelper;
import org.sylvarion.hudhelper.geo.GeographicalLocationService;

import java.util.*;

/** One command executor and tab completer share the same permission rules. */
public final class HudCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("help", "info", "reload", "region");
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final SylvarionHUDHelper plugin;
    private final GeographicalLocationService locations;

    public HudCommand(SylvarionHUDHelper plugin, GeographicalLocationService locations) {
        this.plugin = plugin;
        this.locations = locations;
    }

    private boolean allowed(CommandSender sender, String subcommand) {
        return sender.hasPermission(subcommand.equals("region") ? "sylhud.region" : "sylhud.admin");
    }

    private void send(CommandSender sender, String key, TagResolver... values) {
        sender.sendMessage(miniMessage.deserialize(plugin.settings().messages().get(key), values));
    }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (!SUBCOMMANDS.contains(sub)) sub = "help";
        if (!allowed(sender, sub)) { send(sender, "no-permission"); return true; }
        if (args.length > 1) { send(sender, "help"); return true; }
        switch (sub) {
            case "reload" -> {
                if (plugin.reloadSettings()) send(sender, "reloaded", literal("count", plugin.settings().neighborhoodCount()));
                else send(sender, "reload-failed");
            }
            case "info" -> send(sender, "info", literal("version", plugin.getPluginMeta().getVersion()));
            case "region" -> {
                if (!(sender instanceof Player player)) { send(sender, "player-only"); return true; }
                var result = locations.resolve(player, true);
                send(sender, "region-refresh");
                send(sender, "region-header", literal("world", result.world()), literal("status", result.status()),
                        literal("x", format(result.x())), literal("y", format(result.y())), literal("z", format(result.z())));
                send(sender, "region-position", literal("x", format(result.x())), literal("y", format(result.y())), literal("z", format(result.z())));
                for (var region : result.regions()) {
                    send(sender, "region-entry", literal("id", region.id()), literal("priority", region.priority()),
                            literal("configured", plugin.settings().configured(region.id())));
                }
                send(sender, "region-result", literal("selected", result.hierarchy().neighborhoodId() == null ? "(none)" : result.hierarchy().neighborhoodId()),
                        literal("display", result.hierarchy().neighborhood()));
                send(sender, "region-hierarchy", literal("area", describe(result.hierarchy().areaId(),result.hierarchy().area())),
                        literal("neighborhood", describe(result.hierarchy().neighborhoodId(),result.hierarchy().neighborhood())));
                var street = result.selectedStreet();
                send(sender, "region-street", literal("street", street == null ? "(none) -> " + result.streetName() : describe(street.id(),result.streetName())),
                        literal("method", street == null ? "fallback" : street.method()),
                        literal("distance", street == null || !Double.isFinite(street.distance()) ? "n/a" : format(street.distance())),
                        literal("building", street == null || street.building() == null ? "(none)" : street.building()));
            }
            default -> send(sender, "help");
        }
        return true;
    }

    private TagResolver literal(String key, Object value) { return Placeholder.unparsed(key, String.valueOf(value)); }
    private String format(double value) { return String.format(Locale.ROOT,"%.2f",value); }
    private String describe(String id,String name) { return (id == null ? "(none)" : id) + " -> " + name; }

    @Override public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                        @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream().filter(sub -> allowed(sender, sub) && sub.startsWith(prefix)).toList();
    }
}
