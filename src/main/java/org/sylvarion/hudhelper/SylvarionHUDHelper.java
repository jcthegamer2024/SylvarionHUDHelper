package org.sylvarion.hudhelper;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.sylvarion.hudhelper.command.HudCommand;
import org.sylvarion.hudhelper.config.HudSettings;
import org.sylvarion.hudhelper.geo.GeographicalLocationService;
import org.sylvarion.hudhelper.placeholder.SylHudExpansion;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.logging.Level;

public final class SylvarionHUDHelper extends JavaPlugin {
    private volatile HudSettings settings;
    private GeographicalLocationService locations;
    private SylHudExpansion expansion;
    private boolean expansionRegistered;

    @Override public void onEnable() {
        saveDefaultConfig();
        if (!reloadSettings()) {
            getLogger().severe("Cannot enable with an invalid configuration.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            locations = new GeographicalLocationService(this, this::settings);
            locations.start();
            expansion = new SylHudExpansion(this, locations);
            if (!expansion.register()) throw new IllegalStateException("The sylhud placeholder identifier is already registered.");
            expansionRegistered = true;
            HudCommand handler = new HudCommand(this, locations);
            var command = Objects.requireNonNull(getCommand("sylhud"));
            command.setExecutor(handler);
            command.setTabCompleter(handler);
            getLogger().info("Enabled " + getPluginMeta().getVersion() + " with " + settings.geography().areas().size()
                    + " areas, " + settings.neighborhoodCount() + " neighbourhood names and " + settings.geography().streets().size() + " streets.");
        } catch (RuntimeException | LinkageError ex) {
            getLogger().log(Level.SEVERE, "Could not initialize integrations. Check installed PlaceholderAPI, WorldGuard and FAWE versions.", ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /** Load separately so broken YAML never replaces working settings or rewrites the user's file. */
    public boolean reloadSettings() {
        HudSettings previous = settings;
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new File(getDataFolder(), "config.yml"));
            YamlConfiguration defaults;
            try (var stream = Objects.requireNonNull(getResource("config.yml"));
                 var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                defaults = YamlConfiguration.loadConfiguration(reader);
            }
            HudSettings loaded = HudSettings.parse(yaml, defaults, getLogger()::warning);
            settings = loaded;
            if (locations != null) locations.invalidate();
            return true;
        } catch (Exception ex) {
            settings = previous;
            if (locations != null && previous != null) {
                try { locations.invalidate(); }
                catch (RuntimeException recovery) { ex.addSuppressed(recovery); }
            }
            getLogger().log(Level.WARNING, "Failed to reload config.yml; previous configuration retained.", ex);
            return false;
        }
    }

    public HudSettings settings() { return settings; }

    @Override public void onDisable() {
        if (expansionRegistered) {
            expansion.unregister();
            expansionRegistered = false;
        }
        if (locations != null) locations.close();
    }
}
