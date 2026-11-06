package org.sylvarion.hudhelper.geo;

import com.sk89q.worldguard.protection.regions.*;

/** Copies public WG geometry; retained snapshots never reference mutable WG region objects. */
public final class WorldGuardGeometry {
    private WorldGuardGeometry() {}
    public static Footprint copy(ProtectedRegion region) {
        if (region instanceof ProtectedCuboidRegion) {
            var min=region.getMinimumPoint(); var max=region.getMaximumPoint();
            return new Footprint.Box(min.x(),min.z(),max.x(),max.z());
        }
        if (region instanceof ProtectedPolygonalRegion polygon) {
            return new Footprint.Polygon(polygon.getPoints().stream().map(p -> new Footprint.Point(p.x(),p.z())).toList());
        }
        return null;
    }
}
