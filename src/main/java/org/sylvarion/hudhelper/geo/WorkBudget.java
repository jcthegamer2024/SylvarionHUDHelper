package org.sylvarion.hudhelper.geo;

import org.bukkit.configuration.file.YamlConfiguration;
import java.util.function.Consumer;

/** Bounds the number of Bukkit/WG samples taken in one server tick. */
public record WorkBudget(int playersPerTick, int regionsPerTick, int stationaryRefreshTicks) {
    public static WorkBudget parse(YamlConfiguration yaml, Consumer<String> warn) {
        return new WorkBudget(integer(yaml,"player-samples-per-tick",16,1,1024,warn),
                integer(yaml,"geometry-regions-per-tick",32,1,4096,warn),
                integer(yaml,"stationary-region-refresh-ticks",20,1,1200,warn));
    }
    private static int integer(YamlConfiguration yaml,String key,int fallback,int min,int max,Consumer<String> warn) {
        String path="settings.performance."+key;
        Object raw=yaml.get(path,fallback);
        if (raw instanceof Number number) {
            double value=number.doubleValue();
            if (Double.isFinite(value) && value == Math.rint(value) && value >= min && value <= max) return (int)value;
        }
        warn.accept(path+" must be an integer in ["+min+", "+max+"]; using "+fallback);
        return fallback;
    }
}
