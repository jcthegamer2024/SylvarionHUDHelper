package org.sylvarion.hudhelper.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sylvarion.hudhelper.geo.*;
import org.sylvarion.hudhelper.neighborhood.RegionInfo;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GeoConfigurationTest {
    @TempDir Path directory;
    private YamlConfiguration defaults() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")),StandardCharsets.UTF_8));
    }
    @Test void missingPerformanceSettingsUseBoundedDefaultsForOldConfigurations() {
        var yaml=new YamlConfiguration();
        var budget=WorkBudget.parse(yaml,message -> fail(message));
        assertEquals(16,budget.playersPerTick());
        assertEquals(32,budget.regionsPerTick());
        assertEquals(20,budget.stationaryRefreshTicks());
        yaml.set("settings.performance.player-samples-per-tick",4);
        yaml.set("settings.performance.geometry-regions-per-tick",8);
        yaml.set("settings.performance.stationary-region-refresh-ticks",40);
        assertEquals(new WorkBudget(4,8,40),WorkBudget.parse(yaml,message -> fail(message)));
    }
    @Test void invalidPerformanceBudgetsWarnRatherThanDisablingWorkLimits() {
        var yaml=new YamlConfiguration();
        yaml.set("settings.performance.player-samples-per-tick",0);
        yaml.set("settings.performance.geometry-regions-per-tick",Double.POSITIVE_INFINITY);
        yaml.set("settings.performance.stationary-region-refresh-ticks",1.5);
        List<String> warnings=new ArrayList<>();
        assertEquals(new WorkBudget(16,32,20),WorkBudget.parse(yaml,warnings::add));
        assertEquals(3,warnings.size());
    }
    @Test void bundledConfigurationLoadsWithoutWarningsAndAllowsEmptyFallbacks() {
        var warnings=new ArrayList<String>();
        var settings=HudSettings.parse(defaults(),defaults(),warnings::add);
        assertTrue(warnings.isEmpty(),warnings.toString());
        assertEquals(2,settings.geography().areas().size());
        assertEquals(3,settings.geography().streets().size());
        assertEquals(2,settings.geography().buildings().size());
        assertEquals("",settings.geography().neighborhoodFallback());
        assertEquals("",settings.geography().streetFallback());
        var yaml=defaults(); yaml.set("settings.fallbacks.area","");
        assertEquals("",HudSettings.parse(yaml,defaults(),warnings::add).geography().areaFallback());
    }
    @Test void invalidRadiiTimersReferencesWorldScopesAndNamesWarnSafely() {
        var yaml=defaults();
        yaml.set("streets.central_avenue.detection-radius",Double.NaN);
        yaml.set("streets.station_road.detection-radius",-1);
        yaml.set("streets.oak_street.display-name","<red>Road");
        yaml.set("settings.street-detection.update-interval-ticks",0);
        yaml.set("settings.street-detection.switch-delay-ticks",1.5);
        yaml.set("settings.street-detection.grace-period-ticks",-1);
        yaml.set("settings.street-detection.geometry-refresh-ticks","100");
        yaml.set("settings.street-detection.distance-hysteresis",Double.POSITIVE_INFINITY);
        yaml.set("settings.fallbacks.street","&aUnsafe");
        yaml.set("areas.moon_lake.worlds","world");
        yaml.set("building-overrides.central_shopping_centre.street","missing");
        var warnings=new ArrayList<String>();
        var config=HudSettings.parse(yaml,defaults(),warnings::add).geography();
        assertTrue(warnings.size()>=10);
        assertEquals(30,config.streets().get("central_avenue").radius());
        assertEquals(30,config.streets().get("station_road").radius());
        assertFalse(config.streets().containsKey("oak_street"));
        assertFalse(config.areas().containsKey("moon_lake"));
        assertFalse(config.buildings().containsKey("central_shopping_centre"));
        assertEquals(10,config.updateInterval()); assertEquals(10,config.switchDelay());
        assertEquals(40,config.gracePeriod()); assertEquals(100,config.geometryRefresh());
        assertEquals("",config.streetFallback());
    }
    @Test void defaultRadiusNormalizedIdsAndWorldListsAreSupported() {
        var yaml=defaults();
        yaml.set("settings.street-detection.default-radius",42);
        yaml.set("streets.CUSTOM.display-name","Custom Street");
        yaml.set("streets.CUSTOM.worlds",List.of("city_world"));
        yaml.set("building-overrides.MALL.street","CUSTOM");
        yaml.set("building-overrides.MALL.worlds",List.of("city_world"));
        var config=HudSettings.parse(yaml,defaults(),message -> fail(message)).geography();
        assertEquals(42,config.streets().get("custom").radius());
        assertTrue(config.streets().get("custom").inWorld("city_world"));
        assertFalse(config.streets().get("custom").inWorld("other"));
        assertEquals("custom",config.buildings().get("mall").street());
    }
    @Test void reloadChangesHierarchyStreetNamesAndOverridesWithoutMutatingPreviousSettingsOrDisk() throws Exception {
        var yaml=defaults();
        Path path=directory.resolve("config.yml"); yaml.save(path.toFile());
        byte[] original=Files.readAllBytes(path);
        var fromDisk=new YamlConfiguration(); fromDisk.load(path.toFile());
        var old=HudSettings.parse(fromDisk,defaults(),message -> fail(message));
        fromDisk.set("areas.sylvarion_city.display-name","Renamed City");
        fromDisk.set("streets.central_avenue.display-name","Renamed Avenue");
        fromDisk.set("building-overrides.central_shopping_centre.street","station_road");
        var changed=HudSettings.parse(fromDisk,defaults(),message -> fail(message));
        assertEquals("Sylvarion City",old.geography().areas().get("sylvarion_city").name());
        assertEquals("Renamed City",changed.geography().areas().get("sylvarion_city").name());
        assertEquals("Central Avenue",old.geography().streets().get("central_avenue").name());
        assertEquals("Renamed Avenue",changed.geography().streets().get("central_avenue").name());
        assertEquals("station_road",changed.geography().buildings().get("central_shopping_centre").street());
        assertArrayEquals(original,Files.readAllBytes(path));
        var road=changed.geography().streets().get("central_avenue");
        var index=new StreetIndex(List.of(new StreetIndex.Entry(road,new Footprint.Box(0,0,10,10))));
        var result=LocationEngine.detect(UUID.randomUUID(),"world",5,64,5,List.of(new RegionInfo("sylvarion_city",1)),index,new StreetTracker(),0,changed.geography(),Map.of());
        assertEquals("Renamed City",result.hierarchy().area()); assertEquals("Renamed Avenue",result.streetName());
    }
    @Test void legacyFallbackIsPreservedAndHierarchyDoesNotUseFlatChildren() {
        var yaml=new YamlConfiguration();
        yaml.set("settings.outside-name","Outside City"); yaml.set("neighborhoods.old_town","Custom Old Town");
        var old=HudSettings.parse(yaml,defaults(),message -> {});
        assertEquals("Outside City",old.geography().neighborhoodFallback());
        assertEquals("Outside City",old.geography().areaFallback());
        assertEquals("Custom Old Town",old.neighborhoods().get("old_town"));
        yaml.set("areas.city.display-name","City");
        var upgraded=HudSettings.parse(yaml,defaults(),message -> {});
        var result=HierarchySelector.select("world",List.of(new RegionInfo("city",1),new RegionInfo("old_town",99)),upgraded.geography(),upgraded.neighborhoods());
        assertEquals("City",result.area()); assertEquals("",result.neighborhood());
    }
    @Test void disabledStreetDetectionCannotRetainPreviousStreet() {
        var yaml=defaults();
        var old=HudSettings.parse(yaml,defaults(),message -> fail(message));
        var road=old.geography().streets().get("central_avenue");
        var index=new StreetIndex(List.of(new StreetIndex.Entry(road,new Footprint.Box(0,0,10,10))));
        var tracker=new StreetTracker(); UUID world=UUID.randomUUID();
        var first=LocationEngine.detect(world,"world",5,64,5,List.of(),index,tracker,0,old.geography(),Map.of());
        assertEquals("Central Avenue",first.streetName());
        yaml.set("settings.street-detection.enabled",false);
        var updated=HudSettings.parse(yaml,defaults(),message -> fail(message));
        var after=LocationEngine.detect(world,"world",5,64,5,List.of(),index,tracker,1,updated.geography(),Map.of());
        assertEquals("",after.streetName()); assertNull(after.selectedStreet());
    }
}
