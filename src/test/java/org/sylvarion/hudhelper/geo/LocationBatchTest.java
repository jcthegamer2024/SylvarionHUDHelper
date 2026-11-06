package org.sylvarion.hudhelper.geo;

import org.junit.jupiter.api.Test;
import org.sylvarion.hudhelper.neighborhood.RegionInfo;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.sylvarion.hudhelper.geo.GeoFixtures.*;

class LocationBatchTest {
    private final UUID player=UUID.randomUUID();
    private final Object session=new Object(),version=new Object();
    private final GeoConfig.Street road=street("road",30,0);
    private final GeoConfig config=config(Map.of(),Map.of("road",road),Map.of());
    private final StreetIndex index=new StreetIndex(List.of(new StreetIndex.Entry(road,new Footprint.Box(0,0,10,10))));

    private LocationBatch.Request request(double x,double y,long tick,List<RegionInfo> regions,StreetIndex index,StreetTracker tracker,LocationBatch.SpatialCache cache) {
        return new LocationBatch.Request(player,session,WORLD,"world",x,y,5,tick,regions,index,version,tracker.state(),config,Map.of(),cache);
    }
    private LocationBatch.Output compute(LocationBatch.Request request) {
        return LocationBatch.compute(new LocationBatch.Input(null,List.of(request))).players().getFirst();
    }

    @Test void onlyMatchingSessionWorldAndGeometryVersionCanPublish() {
        var output=compute(request(5,64,0,List.of(),index,new StreetTracker(),null));
        assertTrue(output.validFor(session,WORLD,version));
        assertFalse(output.validFor(new Object(),WORLD,version)); // Teleport, quit/rejoin, reload.
        assertFalse(output.validFor(session,UUID.randomUUID(),version)); // World change/unload.
        assertFalse(output.validFor(session,WORLD,new Object())); // Geometry/manager replacement.
        assertFalse(output.validFor(session,null,version));
    }

    @Test void reusesPreparedGeometryWhilePlayerStaysInTheSameBlock() {
        var first=compute(request(5.1,64.1,0,List.of(),index,new StreetTracker(),null));
        var second=compute(request(5.9,64.9,10,List.of(),index,first.tracker(),first.cache()));
        assertSame(first.cache(),second.cache());
        assertEquals(5.9,second.snapshot().x());
        assertEquals("road",second.snapshot().streetName());
    }

    @Test void movementRegionAndGeometryChangesInvalidateSpatialMemo() {
        var first=compute(request(5,64,0,List.of(),index,new StreetTracker(),null));
        assertNotSame(first.cache(),compute(request(6,64,10,List.of(),index,first.tracker(),first.cache())).cache());
        assertSame(first.cache(),compute(request(5,65,10,List.of(),index,first.tracker(),first.cache())).cache()); // Y alone cannot change horizontal distances.
        assertNotSame(first.cache(),compute(request(5,64,10,List.of(new RegionInfo("building",1)),index,first.tracker(),first.cache())).cache());
        var rebuilt=new StreetIndex(index.entries().values());
        assertNotSame(first.cache(),compute(request(5,64,10,List.of(),rebuilt,first.tracker(),first.cache())).cache());
    }

    @Test void workerCopiesTrackerRatherThanMutatingPreviouslyAcceptedState() {
        var original=new StreetTracker();
        var first=compute(request(5,64,0,List.of(),index,original,null));
        assertNotSame(original,first.tracker());
        assertNull(original.update(WORLD,1,List.of(),null,config));
        assertEquals("road",first.tracker().update(WORLD,1,List.of(),null,config).id());
    }

    @Test void stationaryGraceTimersStillAdvanceWithMemoizedSpatialResults() {
        var first=compute(request(5,64,0,List.of(),index,new StreetTracker(),null));
        var outside=compute(request(100,64,10,List.of(),index,first.tracker(),first.cache()));
        assertEquals("grace",outside.snapshot().selectedStreet().method());
        var expired=compute(request(100,64,40,List.of(),index,outside.tracker(),outside.cache()));
        assertSame(outside.cache(),expired.cache());
        assertEquals("",expired.snapshot().streetName());
    }

    @Test void backgroundRebuildUsesNewGeometryAndVersionInSameBatch() {
        Object scan=new Object(),nextVersion=new Object();
        var build=new LocationBatch.Rebuild(WORLD,scan,version,nextVersion,index,
                List.of(new LocationBatch.Geometry(road,new Footprint.Box(100,0,110,10))));
        var first=compute(request(5,64,0,List.of(),index,new StreetTracker(),null));
        var request=request(5,64,10,List.of(),index,first.tracker(),first.cache());
        var result=LocationBatch.compute(new LocationBatch.Input(build,List.of(request)));
        assertTrue(result.built().changed()); assertSame(nextVersion,result.built().version());
        var output=result.players().getFirst();
        assertTrue(output.validFor(session,WORLD,nextVersion));
        assertEquals("",output.snapshot().streetName()); // No grace from reshaped/removed road.
        assertNotSame(first.cache(),output.cache());
    }

    @Test void unchangedGeometryRetainsTreeIdentityAndVersion() {
        var build=new LocationBatch.Rebuild(WORLD,new Object(),version,new Object(),index,
                List.of(new LocationBatch.Geometry(road,new Footprint.Box(0,0,10,10))));
        var result=LocationBatch.compute(new LocationBatch.Input(build,List.of()));
        assertFalse(result.built().changed()); assertSame(index,result.built().index()); assertSame(version,result.built().version());
    }

    @Test void trackerCopiesPreservePendingSwitchWithoutSharingMutation() {
        var a=street("a",30,0); var b=street("b",30,0);
        var tracker=new StreetTracker(); tracker.update(WORLD,0,List.of(match(a,5)),null,config);
        tracker.update(WORLD,1,List.of(match(a,20),match(b,5)),null,config);
        var copy=tracker.copy();
        assertEquals("b",copy.update(WORLD,11,List.of(match(a,20),match(b,5)),null,config).id());
        assertEquals("a",tracker.update(WORLD,5,List.of(match(a,5)),null,config).id());
    }
}
