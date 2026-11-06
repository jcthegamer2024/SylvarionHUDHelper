package org.sylvarion.hudhelper.geo;

import com.sk89q.worldedit.math.*;
import com.sk89q.worldguard.protection.regions.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FootprintTest {
    private Footprint.Polygon polygon(double... coordinates) {
        List<Footprint.Point> points=new ArrayList<>();
        for (int i=0;i<coordinates.length;i+=2) points.add(new Footprint.Point(coordinates[i],coordinates[i+1]));
        return new Footprint.Polygon(points);
    }
    @Test void cuboidUsesInclusiveBoundaryAndCornerDistances() {
        var box=new Footprint.Box(-10,-20,10,20);
        assertEquals(0,box.distance(10,20));
        assertEquals(1,box.distance(11,20));
        assertEquals(5,box.distance(13,24));
        assertEquals(0,box.distance(-10,-20));
    }
    @Test void diagonalRoadUsesEdgesNotBoundingRectangleOrCentre() {
        var road=polygon(0,0,100,100,102,98,2,-2);
        assertEquals(0,road.distance(90,89));
        assertEquals(0,road.distance(50,50));
        assertEquals(Math.sqrt(2),road.distance(50,52),1e-9);
        assertEquals(100/Math.sqrt(2),road.distance(0,100),1e-9);
    }
    @Test void concaveNotchIsOutsideAndEdgesAndVerticesAreInclusive() {
        var l=polygon(0,0,10,0,10,2,2,2,2,10,0,10);
        assertEquals(0,l.distance(1,8));
        assertEquals(3,l.distance(5,5));
        assertEquals(0,l.distance(2,8));
        assertEquals(0,l.distance(2,2));
        var reversed=new ArrayList<>(l.points()); Collections.reverse(reversed);
        assertEquals(3,new Footprint.Polygon(reversed).distance(5,5));
    }
    @Test void handlesRepeatedVerticesAndDegenerateSegments() {
        var polygon=polygon(0,0,10,0,10,0,10,10,0,10,0,0);
        assertEquals(0,polygon.distance(5,5));
        assertEquals(Math.sqrt(2),polygon.distance(11,11),1e-9);
        assertEquals(5,Footprint.segmentDistance(3,4,new Footprint.Point(0,0),new Footprint.Point(0,0)));
    }
    @Test void worldGuardGeometrySnapshotsAgreeWithActualInclusiveContains() {
        List<ProtectedRegion> regions=List.of(
                new ProtectedCuboidRegion("box",BlockVector3.at(-5,-64,-5),BlockVector3.at(5,320,5)),
                new ProtectedPolygonalRegion("diagonal",List.of(BlockVector2.at(0,0),BlockVector2.at(10,10),BlockVector2.at(12,8),BlockVector2.at(2,-2)),-64,320),
                new ProtectedPolygonalRegion("concave",List.of(BlockVector2.at(0,0),BlockVector2.at(10,0),BlockVector2.at(10,2),BlockVector2.at(2,2),BlockVector2.at(2,10),BlockVector2.at(0,10)),-64,320));
        for (var region : regions) for (int x=-10;x<=20;x++) for (int z=-10;z<=20;z++)
            assertEquals(region.contains(BlockVector3.at(x,64,z)),WorldGuardGeometry.copy(region).distance(x,z)==0,
                    region.getId()+" at "+x+","+z);
    }
    @Test void geometrySnapshotsDetectWorldGuardRegionReplacement() {
        var region=new ProtectedCuboidRegion("box",BlockVector3.at(0,0,0),BlockVector3.at(10,10,10));
        var first=WorldGuardGeometry.copy(region);
        // WG replaces regions on redefine; deprecated direct point mutation is ignored by WG.
        var replacement=new ProtectedCuboidRegion("box",BlockVector3.at(100,0,100),BlockVector3.at(110,10,110));
        assertEquals(0,first.distance(5,5));
        assertNotEquals(first,WorldGuardGeometry.copy(replacement));
    }
}
