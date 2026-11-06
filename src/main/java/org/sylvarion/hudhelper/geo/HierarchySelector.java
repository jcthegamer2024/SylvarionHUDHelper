package org.sylvarion.hudhelper.geo;

import java.util.*;
import org.sylvarion.hudhelper.neighborhood.*;

public final class HierarchySelector {
    private HierarchySelector() {}
    public record Selection(String areaId, String area, String neighborhoodId, String neighborhood) {}
    public static Selection select(String world, List<RegionInfo> regions, GeoConfig config, Map<String,String> legacy) {
        RegionInfo area = regions.stream().filter(r -> !r.id().equals("__global__"))
                .filter(r -> config.areas().containsKey(r.id()) && config.areas().get(r.id()).inWorld(world))
                .min(NeighborhoodSelector.ORDER).orElse(null);
        Map<String,String> neighborhoods = area == null ? (config.areas().isEmpty() ? legacy : Map.of())
                : config.areas().get(area.id()).neighborhoods();
        RegionInfo neighborhood = NeighborhoodSelector.select(regions, neighborhoods).orElse(null);
        return new Selection(area == null ? null : area.id(), area == null ? config.areaFallback() : config.areas().get(area.id()).name(),
                neighborhood == null ? null : neighborhood.id(), neighborhood == null ? config.neighborhoodFallback() : neighborhoods.get(neighborhood.id()));
    }
}
