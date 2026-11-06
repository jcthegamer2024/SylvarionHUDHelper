package org.sylvarion.hudhelper.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HudSettingsTest {
    private YamlConfiguration defaults() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")), StandardCharsets.UTF_8));
    }

    private YamlConfiguration legacy() {
        var yaml = new YamlConfiguration();
        yaml.set("settings.debug",false);
        yaml.set("settings.outside-name","Wilderness");
        yaml.set("neighborhoods.central_district","Central District");
        yaml.set("neighborhoods.riverside","Riverside");
        yaml.set("neighborhoods.old_town","Old Town");
        yaml.set("neighborhoods.downtown","Downtown");
        yaml.set("neighborhoods.sylvarion_airport","Sylvarion Airport");
        yaml.set("neighborhoods.moon_lake","Moon Lake");
        return yaml;
    }

    @Test void loadsDefaultsAndPreservesLiteralNames() {
        var warnings = new ArrayList<String>();
        var settings = HudSettings.parse(legacy(), defaults(), warnings::add);
        assertEquals(6, settings.neighborhoods().size());
        assertEquals("Central District", settings.neighborhoods().get("central_district"));
        assertEquals("Wilderness", settings.outsideName());
        assertFalse(settings.debug());
        assertEquals(1,warnings.size()); // Legacy migration notice; user's file is not changed.
        assertThrows(UnsupportedOperationException.class, () -> settings.neighborhoods().put("x", "x"));
    }

    @Test void invalidEntriesWarnAndDoNotReachHud() {
        var yaml = legacy();
        yaml.set("neighborhoods", null);
        yaml.set("neighborhoods.__global__", "Global");
        yaml.set("neighborhoods.city", "<red>Injected</red>");
        yaml.set("neighborhoods.legacy", "&aInjected");
        yaml.set("neighborhoods.numeric", 123);
        yaml.set("neighborhoods.blank", " ");
        yaml.set("neighborhoods.control", "City\nName");
        yaml.set("neighborhoods.UPPER", "City & Country");
        yaml.set("settings.outside-name", "§cOutside");
        yaml.set("settings.debug", "yes please");
        yaml.set("messages.info", 42);
        var warnings = new ArrayList<String>();
        var settings = HudSettings.parse(yaml, defaults(), warnings::add);
        assertEquals(Map.of("upper", "City & Country"), settings.neighborhoods());
        assertEquals("Wilderness", settings.outsideName());
        assertFalse(settings.debug());
        assertEquals(defaults().getString("messages.info"), settings.messages().get("info"));
        assertEquals(10, warnings.size());
    }

    @Test void supportsLargeMappingsAndNewSnapshotsForReloads() {
        var yaml = legacy();
        for (int i = 0; i < 10_000; i++) yaml.set("neighborhoods.region_" + i, "Name " + i);
        var before = HudSettings.parse(yaml, defaults(), message -> assertTrue(message.startsWith("Using legacy")));
        yaml.set("neighborhoods.central_district", "New Central");
        var after = HudSettings.parse(yaml, defaults(), message -> assertTrue(message.startsWith("Using legacy")));
        assertEquals(10_006, after.neighborhoods().size());
        assertEquals("Central District", before.neighborhoods().get("central_district"));
        assertEquals("New Central", after.neighborhoods().get("central_district"));
    }
}
