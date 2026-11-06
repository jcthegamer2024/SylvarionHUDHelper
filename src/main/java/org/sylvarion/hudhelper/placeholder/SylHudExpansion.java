package org.sylvarion.hudhelper.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sylvarion.hudhelper.SylvarionHUDHelper;
import org.sylvarion.hudhelper.geo.GeographicalLocationService;

public final class SylHudExpansion extends PlaceholderExpansion {
    private final SylvarionHUDHelper plugin;
    private final GeographicalLocationService locations;

    public SylHudExpansion(SylvarionHUDHelper plugin, GeographicalLocationService locations) {
        this.plugin = plugin;
        this.locations = locations;
    }

    @Override public @NotNull String getIdentifier() { return "sylhud"; }
    @Override public @NotNull String getAuthor() { return "Sylvarion"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        var location = locations.snapshot(player);
        return switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "area" -> location.hierarchy().area();
            case "neighborhood" -> location.hierarchy().neighborhood();
            case "street" -> location.streetName();
            default -> null;
        };
    }
}
