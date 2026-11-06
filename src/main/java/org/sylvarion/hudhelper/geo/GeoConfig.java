package org.sylvarion.hudhelper.geo;

import java.util.*;

/** World names are exact Bukkit world names; an empty set means any world, queried independently. */
public record GeoConfig(Map<String, Area> areas, Map<String, Street> streets,
                        Map<String, Building> buildings, String areaFallback, String neighborhoodFallback,
                        String streetFallback, boolean enabled, int switchDelay, int gracePeriod,
                        int updateInterval, int geometryRefresh, double hysteresis) {
    public GeoConfig {
        areas = Map.copyOf(areas); streets = Map.copyOf(streets); buildings = Map.copyOf(buildings);
    }
    public record Area(String name, Map<String, String> neighborhoods, Set<String> worlds) {
        public Area { neighborhoods = Map.copyOf(neighborhoods); worlds = Set.copyOf(worlds); }
        public boolean inWorld(String world) { return worlds.isEmpty() || worlds.contains(world); }
    }
    public record Street(String id, String name, double radius, int priority, Set<String> worlds) {
        public Street { worlds = Set.copyOf(worlds); }
        public boolean inWorld(String world) { return worlds.isEmpty() || worlds.contains(world); }
    }
    public record Building(String street, Set<String> worlds) {
        public Building { worlds = Set.copyOf(worlds); }
        public boolean inWorld(String world) { return worlds.isEmpty() || worlds.contains(world); }
    }
}
