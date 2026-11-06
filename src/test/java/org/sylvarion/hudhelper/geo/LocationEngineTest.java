package org.sylvarion.hudhelper.geo;

import org.junit.jupiter.api.Test;
import org.sylvarion.hudhelper.neighborhood.RegionInfo;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.sylvarion.hudhelper.geo.GeoFixtures.*;

class LocationEngineTest {
    private LocationSnapshot detect(GeoConfig config,StreetIndex index,List<RegionInfo> regions,double x,double y,double z) {
        return LocationEngine.detect(WORLD,"city_world",x,y,z,regions,index,new StreetTracker(),0,config,Map.of());
    }
    @Test void areasAndNeighborhoodsAreSelectedIndependentlyAndChildrenBelongToWinningArea() {
        var config=config(Map.of("city",new GeoConfig.Area("City",Map.of("central","Central"),Set.of()),
                "lake",new GeoConfig.Area("Lake",Map.of("park","Park"),Set.of())),Map.of(),Map.of());
        var regions=List.of(new RegionInfo("city",5),new RegionInfo("lake",1),new RegionInfo("central",10),
                new RegionInfo("park",100),new RegionInfo("unrelated",1000));
        var result=detect(config,new StreetIndex(List.of()),regions,1,64,1);
        assertEquals("City",result.hierarchy().area());
        assertEquals("Central",result.hierarchy().neighborhood());
    }
    @Test void areaTiesUseIdAndOutsideAreasDoesNotSelectOrphanedNeighborhood() {
        var config=config(Map.of("alpha",new GeoConfig.Area("Alpha",Map.of("central","Central"),Set.of()),
                "beta",new GeoConfig.Area("Beta",Map.of(),Set.of())),Map.of(),Map.of());
        var selected=HierarchySelector.select("world",List.of(new RegionInfo("beta",1),new RegionInfo("alpha",1)),config,Map.of());
        assertEquals("alpha",selected.areaId());
        var outside=HierarchySelector.select("world",List.of(new RegionInfo("central",100)),config,Map.of("central","Legacy"));
        assertEquals("Wilderness",outside.area());
        assertEquals("",outside.neighborhood());
    }
    @Test void worldRestrictionsPreventSameIdsInOtherWorldsMatching() {
        var s=new GeoConfig.Street("road","Road",30,0,Set.of("city_world"));
        var config=config(Map.of("city",new GeoConfig.Area("City",Map.of("central","Central"),Set.of("city_world"))),Map.of("road",s),Map.of());
        var index=new StreetIndex(List.of(new StreetIndex.Entry(s,new Footprint.Box(0,0,10,10))));
        var result=LocationEngine.detect(UUID.randomUUID(),"other_world",5,64,5,List.of(new RegionInfo("city",1)),index,new StreetTracker(),0,config,Map.of());
        assertEquals("Wilderness",result.hierarchy().area());
        assertEquals("",result.streetName());
    }
    @Test void legacyFlatNamesContinueToWorkWithNoAreaConfiguration() {
        var result=HierarchySelector.select("world",List.of(new RegionInfo("central",3)),config(Map.of(),Map.of(),Map.of()),Map.of("central","Old Central"));
        assertEquals("Old Central",result.neighborhood());
        assertEquals("Wilderness",result.area());
    }
    @Test void nearbyBuildingUpperFloorsAndUndergroundUseSameHorizontalStreet() {
        var road=street("road",30,0);
        var config=config(Map.of(),Map.of("road",road),Map.of());
        var index=new StreetIndex(List.of(new StreetIndex.Entry(road,new Footprint.Box(0,0,100,5))));
        for (double y : new double[]{-60,0,64,150,319}) {
            var result=detect(config,index,List.of(new RegionInfo("building",100)),50,y,25);
            assertEquals("road",result.streetName());
            assertEquals(20,result.selectedStreet().distance());
            assertEquals("proximity",result.selectedStreet().method());
        }
    }
    @Test void horizontalFootprintWinsOverCloserPriorityOverrideAndWorldGuardPriority() {
        var a=street("inside",30,-10); var b=street("outside",30,100);
        var config=config(Map.of(),Map.of(a.id(),a,b.id(),b),Map.of());
        var index=new StreetIndex(List.of(new StreetIndex.Entry(a,new Footprint.Box(0,0,10,10)),
                new StreetIndex.Entry(b,new Footprint.Box(12,0,20,10))));
        var result=detect(config,index,List.of(new RegionInfo("outside",999)),5,200,5);
        assertEquals("inside",result.streetName());
        assertEquals("footprint",result.selectedStreet().method());
    }
    @Test void overlappingStreetsAndEqualDistancesResolveByConfiguredPriorityThenId() {
        var a=street("alpha",50,0); var b=street("beta",50,1); var c=street("charlie",50,1);
        var config=config(Map.of(),Map.of(a.id(),a,b.id(),b,c.id(),c),Map.of());
        var index=new StreetIndex(List.of(new StreetIndex.Entry(c,new Footprint.Box(0,0,10,10)),
                new StreetIndex.Entry(b,new Footprint.Box(0,0,10,10)),new StreetIndex.Entry(a,new Footprint.Box(0,0,10,10))));
        assertEquals("beta",detect(config,index,List.of(),5,64,5).streetName());
        assertEquals("beta",detect(config,index,List.of(),5,64,20).streetName());
        var equal=config(Map.of(),Map.of(a.id(),a),Map.of());
        var d=street("delta",50,0);
        var equalIndex=new StreetIndex(List.of(new StreetIndex.Entry(d,new Footprint.Box(0,0,10,10)),new StreetIndex.Entry(a,new Footprint.Box(0,0,10,10))));
        assertEquals("alpha",detect(equal,equalIndex,List.of(),5,64,20).streetName());
    }
    @Test void closestStreetWinsDespiteFartherStreetPriorityOverride() {
        var a=street("near",50,0); var b=street("far",50,100);
        var config=config(Map.of(),Map.of(a.id(),a,b.id(),b),Map.of());
        var index=new StreetIndex(List.of(new StreetIndex.Entry(a,new Footprint.Box(0,0,10,10)),new StreetIndex.Entry(b,new Footprint.Box(0,30,10,40))));
        assertEquals("near",detect(config,index,List.of(),5,64,12).streetName());
    }
    @Test void buildingOverridesUseWorldGuardPriorityThenIdAndBypassRadius() {
        var a=street("road_a",1,0); var b=street("road_b",1,0);
        var config=config(Map.of(),Map.of(a.id(),a,b.id(),b),Map.of("building_a",new GeoConfig.Building(a.id(),Set.of()),
                "building_b",new GeoConfig.Building(b.id(),Set.of())));
        var index=new StreetIndex(List.of(new StreetIndex.Entry(a,new Footprint.Box(0,0,1,1)),new StreetIndex.Entry(b,new Footprint.Box(1000,0,1001,1))));
        var result=detect(config,index,List.of(new RegionInfo("building_a",5),new RegionInfo("building_b",10)),500,64,0);
        assertEquals("road_b",result.streetName());
        assertEquals("building_b",result.selectedStreet().building());
        assertEquals("building-override",result.selectedStreet().method());
        assertEquals(500,result.selectedStreet().distance());
        var tie=detect(config,index,List.of(new RegionInfo("building_b",10),new RegionInfo("building_a",10)),500,64,0);
        assertEquals("road_a",tie.streetName());
    }
    @Test void overrideWorksWithoutStreetGeometryAndIsIndependentOfProximity() {
        var a=street("road",30,0);
        var config=config(Map.of(),Map.of(a.id(),a),Map.of("building",new GeoConfig.Building(a.id(),Set.of())));
        var result=detect(config,new StreetIndex(List.of()),List.of(new RegionInfo("building",1)),5,64,5);
        assertEquals("road",result.streetName());
        assertTrue(Double.isNaN(result.selectedStreet().distance()));
    }
    @Test void outsideAllRegionsAndRadiusProducesFallbacks() {
        var result=detect(config(Map.of(),Map.of(),Map.of()),new StreetIndex(List.of()),List.of(),1000,64,1000);
        assertEquals("Wilderness",result.hierarchy().area()); assertEquals("",result.hierarchy().neighborhood());
        assertEquals("",result.streetName()); assertNull(result.selectedStreet());
    }
    @Test void fractionalNegativeLocationsFollowWorldGuardBlockCoordinates() {
        var a=street("road",0,0); var config=config(Map.of(),Map.of(a.id(),a),Map.of());
        var index=new StreetIndex(List.of(new StreetIndex.Entry(a,new Footprint.Box(-5,-5,-1,-1))));
        assertEquals("road",detect(config,index,List.of(),-0.01,64,-0.01).streetName());
        assertEquals("",detect(config,index,List.of(),0,64,0).streetName());
    }
}
