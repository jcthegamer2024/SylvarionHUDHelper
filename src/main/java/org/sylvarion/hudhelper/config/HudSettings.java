package org.sylvarion.hudhelper.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import java.util.*;
import java.util.function.Consumer;
import org.sylvarion.hudhelper.geo.GeoConfig;
import org.sylvarion.hudhelper.geo.WorkBudget;

/** Immutable configuration, published atomically after a successful reload. */
public record HudSettings(boolean debug, String outsideName, Map<String, String> neighborhoods,
                          Map<String, String> messages, GeoConfig geography, WorkBudget workBudget) {
    public HudSettings {
        neighborhoods = Map.copyOf(neighborhoods);
        messages = Map.copyOf(messages);
    }

    public static HudSettings parse(YamlConfiguration yaml, YamlConfiguration defaults, Consumer<String> warn) {
        Object debug = yaml.get("settings.debug", false);
        if (!(debug instanceof Boolean)) warn.accept("settings.debug must be true or false; using false.");
        String outside = name(yaml.get("settings.outside-name", "Wilderness"), "settings.outside-name", warn);
        Map<String, String> names = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("neighborhoods");
        if (section == null && yaml.contains("neighborhoods")) warn.accept("neighborhoods must be a mapping of region IDs to plain-text names.");
        if (section != null) for (String key : section.getKeys(false)) {
            String id = key.toLowerCase(Locale.ROOT);
            if (!id.matches("[a-z0-9_,'+\\-/]+") || id.equals("__global__")) {
                warn.accept("Ignoring invalid or global region ID: " + key);
                continue;
            }
            String display = name(section.get(key), "neighborhoods." + key, warn);
            if (display != null) {
                if (names.putIfAbsent(id, display) != null) warn.accept("Ignoring duplicate region ID: " + key);
            }
        }
        Map<String, String> messages = new HashMap<>();
        ConfigurationSection templates = Objects.requireNonNull(defaults.getConfigurationSection("messages"));
        for (String key : templates.getKeys(false)) {
            Object value = yaml.get("messages." + key, templates.getString(key));
            if (!(value instanceof String)) {
                warn.accept("messages." + key + " must be a MiniMessage string; using default.");
                value = templates.getString(key);
            }
            try {
                MiniMessage.miniMessage().deserialize((String) value, TagResolver.resolver(
                        List.of("count", "version", "world", "status", "id", "priority", "configured", "selected", "display", "x", "y", "z", "area", "neighborhood", "street", "method", "distance", "building")
                                .stream().map(tag -> Placeholder.unparsed(tag, "value")).toArray(TagResolver[]::new)));
            } catch (RuntimeException ex) {
                warn.accept("messages." + key + " has invalid MiniMessage formatting; using default.");
                value = templates.getString(key);
            }
            messages.put(key, (String) value);
        }
        String outsideName = outside == null ? "Wilderness" : outside;
        return new HudSettings(Boolean.TRUE.equals(debug), outsideName, names, messages,
                GeoConfigParser.parse(yaml, outsideName, warn), WorkBudget.parse(yaml,warn));
    }

    static String name(Object value, String path, Consumer<String> warn) { return name(value, path, warn, false); }
    static String name(Object value, String path, Consumer<String> warn, boolean allowEmpty) {
        if (!(value instanceof String text) || (!allowEmpty && text.isBlank())) {
            warn.accept(path + " must be a non-empty plain-text string; entry ignored.");
            return null;
        }
        // The HUD may parse its own markup after PAPI substitution: reject markup at the source.
        if (text.contains("<") || text.contains(">") || text.contains("§")
                || text.matches("(?is).*&(?:[0-9a-fk-orx]|#[0-9a-f]{6}).*")
                || text.codePoints().anyMatch(Character::isISOControl)) {
            warn.accept(path + " contains formatting or control characters; use plain text. Entry ignored.");
            return null;
        }
        return text;
    }

    public boolean configured(String id) {
        return neighborhoods.containsKey(id) || geography.areas().containsKey(id) || geography.streets().containsKey(id)
                || geography.buildings().containsKey(id) || geography.areas().values().stream().anyMatch(a -> a.neighborhoods().containsKey(id));
    }
    public int neighborhoodCount() {
        return neighborhoods.size() + geography.areas().values().stream().mapToInt(a -> a.neighborhoods().size()).sum();
    }
}
