package org.sylvarion.hudhelper.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.sylvarion.hudhelper.geo.GeoConfig;
import java.util.*;
import java.util.function.Consumer;

final class GeoConfigParser {
    private GeoConfigParser() {}
    static GeoConfig parse(YamlConfiguration yaml, String legacyOutside, Consumer<String> warn) {
        Map<String,GeoConfig.Area> areas = new LinkedHashMap<>();
        ConfigurationSection areaSection = section(yaml,"areas",warn);
        if (areaSection != null) for (String key : areaSection.getKeys(false)) {
            String id=id(key,warn); ConfigurationSection entry=section(areaSection,key,warn);
            if (id == null || entry == null) continue;
            String name=HudSettings.name(entry.get("display-name"),"areas."+key+".display-name",warn);
            Set<String> worlds=worlds(entry,warn);
            if (name == null || worlds == null) continue;
            Map<String,String> neighborhoods=new LinkedHashMap<>();
            ConfigurationSection children=section(entry,"neighborhoods",warn);
            if (children != null) for (String child : children.getKeys(false)) {
                String childId=id(child,warn), display=HudSettings.name(children.get(child),"areas."+key+".neighborhoods."+child,warn);
                if (childId != null && display != null) put(neighborhoods,childId,display,warn);
            }
            put(areas,id,new GeoConfig.Area(name,neighborhoods,worlds),warn);
        }
        double defaultRadius=number(yaml,"settings.street-detection.default-radius",30,0,1_000_000,false,warn);
        Map<String,GeoConfig.Street> streets=new LinkedHashMap<>();
        ConfigurationSection streetSection=section(yaml,"streets",warn);
        if (streetSection != null) for (String key : streetSection.getKeys(false)) {
            String id=id(key,warn); ConfigurationSection entry=section(streetSection,key,warn);
            if (id == null || entry == null) continue;
            String name=HudSettings.name(entry.get("display-name"),"streets."+key+".display-name",warn);
            Set<String> worlds=worlds(entry,warn);
            if (name == null || worlds == null) continue;
            double radius=number(entry,"detection-radius",defaultRadius,0,1_000_000,false,warn);
            int priority=(int)number(entry,"priority",0,Integer.MIN_VALUE,Integer.MAX_VALUE,true,warn);
            put(streets,id,new GeoConfig.Street(id,name,radius,priority,worlds),warn);
        }
        Map<String,GeoConfig.Building> buildings=new LinkedHashMap<>();
        ConfigurationSection buildingSection=section(yaml,"building-overrides",warn);
        if (buildingSection != null) for (String key : buildingSection.getKeys(false)) {
            String id=id(key,warn); ConfigurationSection entry=section(buildingSection,key,warn);
            if (id == null || entry == null) continue;
            Object raw=entry.get("street");
            String street=raw instanceof String text ? text.toLowerCase(Locale.ROOT) : "";
            Set<String> worlds=worlds(entry,warn);
            if (!streets.containsKey(street)) { warn.accept("building-overrides."+key+" references an unknown street: "+raw); continue; }
            if (worlds != null) put(buildings,id,new GeoConfig.Building(street,worlds),warn);
        }
        boolean legacy=areas.isEmpty() && yaml.isConfigurationSection("neighborhoods");
        if (legacy) warn.accept("Using legacy flat neighborhoods configuration without rewriting it. Move entries under areas.<region>.neighborhoods to enable hierarchy.");
        else if (!areas.isEmpty() && yaml.contains("neighborhoods")) warn.accept("Hierarchical areas are enabled; the legacy flat neighborhoods map is ignored for selection.");
        Object enabled=yaml.get("settings.street-detection.enabled",true);
        if (!(enabled instanceof Boolean)) warn.accept("settings.street-detection.enabled must be true/false; using true.");
        return new GeoConfig(areas,streets,buildings,
                fallback(yaml,"area",legacyOutside,warn), fallback(yaml,"neighborhood",legacy ? legacyOutside : "",warn),
                fallback(yaml,"street","",warn), !(enabled instanceof Boolean b) || b,
                integer(yaml,"switch-delay-ticks",10,0,72000,warn),integer(yaml,"grace-period-ticks",40,0,72000,warn),
                integer(yaml,"update-interval-ticks",10,1,1200,warn),integer(yaml,"geometry-refresh-ticks",100,1,72000,warn),
                number(yaml,"settings.street-detection.distance-hysteresis",2,0,1000,false,warn));
    }
    private static String fallback(YamlConfiguration yaml,String key,String fallback,Consumer<String> warn) {
        String value=HudSettings.name(yaml.get("settings.fallbacks."+key,fallback),"settings.fallbacks."+key,warn,true);
        return value == null ? fallback : value;
    }
    private static int integer(YamlConfiguration yaml,String key,int fallback,int min,int max,Consumer<String> warn) {
        return (int)number(yaml,"settings.street-detection."+key,fallback,min,max,true,warn);
    }
    private static double number(ConfigurationSection section,String key,double fallback,double min,double max,boolean integer,Consumer<String> warn) {
        Object raw=section.get(key,fallback);
        if (raw instanceof Number number) {
            double value=number.doubleValue();
            if (Double.isFinite(value) && value >= min && value <= max && (!integer || value == Math.rint(value))) return value;
        }
        warn.accept(section.getCurrentPath()+"."+key+" must be "+(integer ? "an integer" : "a finite number")+" in ["+min+", "+max+"]; using "+fallback);
        return fallback;
    }
    private static ConfigurationSection section(ConfigurationSection parent,String key,Consumer<String> warn) {
        ConfigurationSection result=parent.getConfigurationSection(key);
        if (result == null && parent.contains(key)) warn.accept(parent.getCurrentPath()+"."+key+" must be a mapping.");
        return result;
    }
    private static String id(String key,Consumer<String> warn) {
        String id=key.toLowerCase(Locale.ROOT);
        if (id.matches("[a-z0-9_,'+\\-/]+") && !id.equals("__global__")) return id;
        warn.accept("Ignoring invalid/global region ID: "+key); return null;
    }
    private static Set<String> worlds(ConfigurationSection entry,Consumer<String> warn) {
        if (!entry.contains("worlds")) return Set.of();
        Object raw=entry.get("worlds");
        if (raw instanceof List<?> list && list.stream().allMatch(value -> value instanceof String text && !text.isBlank())) {
            Set<String> names=new HashSet<>(); list.forEach(value -> names.add((String)value)); return Set.copyOf(names);
        }
        warn.accept(entry.getCurrentPath()+".worlds must be a list of world names; ignoring entry."); return null;
    }
    private static <T> void put(Map<String,T> map,String id,T value,Consumer<String> warn) {
        if (map.putIfAbsent(id,value) != null) warn.accept("Ignoring duplicate normalized region ID: "+id);
    }
}
