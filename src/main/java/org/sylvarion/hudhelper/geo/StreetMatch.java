package org.sylvarion.hudhelper.geo;

public record StreetMatch(GeoConfig.Street street, double distance, String method, String building) {
    public boolean inside() { return distance == 0; }
    public boolean override() { return building != null; }
    public String id() { return street.id(); }
}
