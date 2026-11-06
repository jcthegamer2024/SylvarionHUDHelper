package org.sylvarion.hudhelper.geo;

import java.util.*;
import org.sylvarion.hudhelper.neighborhood.*;

/** Pure location policy; no Bukkit or WorldGuard objects cross this boundary. */
public final class LocationEngine {
    private LocationEngine() {}
    public static LocationSnapshot detect(UUID worldId, String world, double x, double y, double z,
            List<RegionInfo> regions, StreetIndex index, StreetTracker tracker, long tick,
            GeoConfig config, Map<String,String> legacy) {
        return finish(worldId,world,x,y,z,regions,index,tracker,tick,config,prepare(world, x,z,regions,index,config,legacy));
    }

    public record Prepared(HierarchySelector.Selection hierarchy, List<StreetMatch> candidates, StreetMatch override) {
        public Prepared { candidates=List.copyOf(candidates); }
    }

    /** Spatial results may be reused while horizontal block, regions, config and index are unchanged. */
    public static Prepared prepare(String world,double x,double z,List<RegionInfo> regions,StreetIndex index,
                                   GeoConfig config,Map<String,String> legacy) {
        var hierarchy=HierarchySelector.select(world,regions,config,legacy);
        // WorldGuard tests inclusive integer block positions. Use the same lattice horizontally.
        double bx=Math.floor(x), bz=Math.floor(z);
        if (config.enabled()) {
            RegionInfo building=regions.stream().filter(r -> !r.id().equals("__global__"))
                    .filter(r -> config.buildings().containsKey(r.id()))
                    .filter(r -> {
                        var b=config.buildings().get(r.id()); var s=config.streets().get(b.street());
                        return b.inWorld(world) && s != null && s.inWorld(world);
                    }).min(NeighborhoodSelector.ORDER).orElse(null);
            StreetMatch override=null;
            if (building != null) {
                var def=config.streets().get(config.buildings().get(building.id()).street());
                var geometry=index.entries().get(def.id());
                override=new StreetMatch(def,geometry == null ? Double.NaN : geometry.footprint().distance(bx,bz),"building-override",building.id());
            }
            List<StreetMatch> candidates=override == null ? index.nearby(bx,bz).stream().filter(m -> m.street().inWorld(world)).toList() : List.of();
            return new Prepared(hierarchy,candidates,override);
        }
        return new Prepared(hierarchy,List.of(),null);
    }

    public static LocationSnapshot finish(UUID worldId,String world,double x,double y,double z,List<RegionInfo> regions,
                                         StreetIndex index,StreetTracker tracker,long tick,GeoConfig config,Prepared prepared) {
        StreetMatch street=null;
        if (config.enabled()) {
            street=tracker.update(worldId,tick,prepared.candidates(),prepared.override(),config);
            if (street != null) {
                var geometry=index.entries().get(street.id());
                if (geometry != null && (street.method().equals("grace") || street.method().equals("switch-delay")))
                    street=new StreetMatch(street.street(),geometry.footprint().distance(Math.floor(x),Math.floor(z)),street.method(),street.building());
            }
        } else tracker.reset();
        return new LocationSnapshot(worldId,world,x,y,z,"Ready",regions,prepared.hierarchy(),street,street == null ? config.streetFallback() : street.street().name());
    }
}
