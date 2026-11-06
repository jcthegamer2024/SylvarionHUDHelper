package org.sylvarion.hudhelper.geo;

import java.util.*;
import org.sylvarion.hudhelper.neighborhood.RegionInfo;

public record LocationSnapshot(UUID worldId, String world, double x, double y, double z, String status,
                               List<RegionInfo> regions, HierarchySelector.Selection hierarchy,
                               StreetMatch selectedStreet, String streetName) {
    public LocationSnapshot { regions=List.copyOf(regions); }
    public static LocationSnapshot fallback(GeoConfig config, String status) {
        return new LocationSnapshot(null,"unknown",0,0,0,status,List.of(),
                new HierarchySelector.Selection(null,config.areaFallback(),null,config.neighborhoodFallback()),null,config.streetFallback());
    }
}
