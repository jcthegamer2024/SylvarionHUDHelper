package org.sylvarion.hudhelper.geo;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.sylvarion.hudhelper.geo.GeoFixtures.*;

class StreetIndexTest {
    @Test void indexMatchesBruteForceForTenThousandStreets() {
        List<StreetIndex.Entry> entries=new ArrayList<>();
        for (int i=0;i<10000;i++) {
            int x=(i%100)*100-5000,z=(i/100)*100-5000;
            entries.add(new StreetIndex.Entry(street("street_"+i,30,0),new Footprint.Box(x,z,x+10,z+10)));
        }
        var index=new StreetIndex(entries);
        Random random=new Random(73);
        for (int i=0;i<150;i++) {
            int x=random.nextInt(10000)-5000,z=random.nextInt(10000)-5000;
            Set<String> expected=new HashSet<>();
            entries.forEach(e -> { if(e.footprint().distance(x,z)<=e.street().radius()) expected.add(e.street().id()); });
            Set<String> actual=new HashSet<>(); index.nearby(x,z).forEach(m -> actual.add(m.id()));
            assertEquals(expected,actual);
        }
    }
    @Test void supportsLongRoadsNegativeCoordinatesAndExactRadius() {
        var entry=new StreetIndex.Entry(street("long",20,0),new Footprint.Box(-100000,-5,100000,5));
        var index=new StreetIndex(List.of(entry));
        assertEquals(1,index.nearby(-99999,25).size());
        assertTrue(index.nearby(-99999,26).isEmpty());
        assertEquals(0,index.nearby(99999,0).getFirst().distance());
    }
    @Test void rebuildingAfterRegionEditsDoesNotRetainOldGeometry() {
        var s=street("road",10,0);
        var old=new StreetIndex(List.of(new StreetIndex.Entry(s,new Footprint.Box(0,0,10,10))));
        var edited=new StreetIndex(List.of(new StreetIndex.Entry(s,new Footprint.Box(100,100,110,110))));
        assertEquals(1,old.nearby(5,5).size());
        assertTrue(edited.nearby(5,5).isEmpty());
        assertEquals(1,edited.nearby(105,105).size());
    }
}
