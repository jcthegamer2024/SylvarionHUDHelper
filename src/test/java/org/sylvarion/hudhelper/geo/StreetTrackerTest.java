package org.sylvarion.hudhelper.geo;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.sylvarion.hudhelper.geo.GeoFixtures.*;

class StreetTrackerTest {
    private final GeoConfig.Street a=street("a",30,0),b=street("b",30,0);
    private final GeoConfig config=config(Map.of(),Map.of("a",a,"b",b),Map.of());

    @Test void delaysSustainedSwitchAndCancelsWhenCandidateChangesBack() {
        var tracker=new StreetTracker();
        assertEquals("a",tracker.update(WORLD,0,List.of(match(a,5)),null,config).id());
        assertEquals("a",tracker.update(WORLD,10,List.of(match(a,15),match(b,5)),null,config).id());
        assertEquals("a",tracker.update(WORLD,15,List.of(match(a,5),match(b,15)),null,config).id());
        assertEquals("a",tracker.update(WORLD,20,List.of(match(a,15),match(b,5)),null,config).id());
        assertEquals("a",tracker.update(WORLD,29,List.of(match(a,15),match(b,5)),null,config).id());
        assertEquals("b",tracker.update(WORLD,30,List.of(match(a,15),match(b,5)),null,config).id());
    }
    @Test void approximateDistancesAndIntersectionsKeepCurrentStreetStable() {
        var tracker=new StreetTracker();
        tracker.update(WORLD,0,List.of(match(b,5)),null,config);
        assertEquals("b",tracker.update(WORLD,100,List.of(match(a,4),match(b,5)),null,config).id());
        assertEquals("b",tracker.update(WORLD,200,List.of(match(a,0),match(b,0)),null,config).id());
    }
    @Test void enteringDifferentFootprintBypassesDelay() {
        var tracker=new StreetTracker();
        tracker.update(WORLD,0,List.of(match(a,1)),null,config);
        assertEquals("b",tracker.update(WORLD,1,List.of(match(a,1),match(b,0)),null,config).id());
    }
    @Test void graceExpiresAndReentryResetsItsClock() {
        var tracker=new StreetTracker();
        tracker.update(WORLD,0,List.of(match(a,5)),null,config);
        assertEquals("grace",tracker.update(WORLD,39,List.of(),null,config).method());
        assertEquals("a",tracker.update(WORLD,40,List.of(match(a,5)),null,config).id());
        assertNotNull(tracker.update(WORLD,79,List.of(),null,config));
        assertNull(tracker.update(WORLD,80,List.of(),null,config));
    }
    @Test void worldChangesAndTeleportResetNeverKeepOldStreetOrDelayNewStreet() {
        var tracker=new StreetTracker();
        tracker.update(WORLD,0,List.of(match(a,5)),null,config);
        UUID other=UUID.randomUUID();
        assertNull(tracker.update(other,1,List.of(),null,config));
        assertEquals("b",tracker.update(other,2,List.of(match(b,5)),null,config).id());
        tracker.reset(); // Same reset used by teleport, configuration reload and geometry change handlers.
        assertNull(tracker.update(other,3,List.of(),null,config));
        assertEquals("a",tracker.update(other,4,List.of(match(a,5)),null,config).id());
    }
    @Test void buildingOverrideIsImmediateAndBecomesNormalDetectionOnExit() {
        var tracker=new StreetTracker();
        tracker.update(WORLD,0,List.of(match(a,0)),null,config);
        var override=new StreetMatch(b,100,"building-override","mall");
        assertEquals("mall",tracker.update(WORLD,1,List.of(match(a,0)),override,config).building());
        var exit=tracker.update(WORLD,2,List.of(match(a,0)),null,config);
        assertEquals("a",exit.id()); assertNull(exit.building());
    }
    @Test void zeroDelayAndGraceApplyWithoutExtraUpdate() {
        var fast=new GeoConfig(Map.of(),Map.of(),Map.of(),"","","",true,0,0,1,100,0);
        var tracker=new StreetTracker(); tracker.update(WORLD,0,List.of(match(a,10)),null,fast);
        assertEquals("b",tracker.update(WORLD,1,List.of(match(b,5)),null,fast).id());
        assertNull(tracker.update(WORLD,2,List.of(),null,fast));
    }
}
