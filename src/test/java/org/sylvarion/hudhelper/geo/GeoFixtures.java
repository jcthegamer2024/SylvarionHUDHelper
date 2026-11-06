package org.sylvarion.hudhelper.geo;

import java.util.*;

final class GeoFixtures {
    static final UUID WORLD=UUID.fromString("00000000-0000-0000-0000-000000000001");
    static GeoConfig.Street street(String id,double radius,int priority) { return new GeoConfig.Street(id,id,radius,priority,Set.of()); }
    static GeoConfig config(Map<String,GeoConfig.Area> areas, Map<String,GeoConfig.Street> streets, Map<String,GeoConfig.Building> buildings) {
        return new GeoConfig(areas,streets,buildings,"Wilderness","","",true,10,40,10,100,2);
    }
    static StreetMatch match(GeoConfig.Street street,double distance) { return new StreetMatch(street,distance,distance==0 ? "footprint" : "proximity",null); }
}
