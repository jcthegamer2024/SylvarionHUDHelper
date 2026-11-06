package org.sylvarion.hudhelper.geo;

import java.util.List;

/** Horizontal geometry on WorldGuard's inclusive integer block lattice. No Y coordinate is used. */
public sealed interface Footprint permits Footprint.Box, Footprint.Polygon {
    double distance(double x, double z);
    Bounds bounds();

    record Point(double x, double z) {}
    record Bounds(double minX, double minZ, double maxX, double maxZ) {
        public Bounds expand(double radius) { return new Bounds(minX-radius, minZ-radius, maxX+radius, maxZ+radius); }
        public boolean contains(double x, double z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }
        public Bounds union(Bounds other) {
            return new Bounds(Math.min(minX, other.minX), Math.min(minZ, other.minZ),
                    Math.max(maxX, other.maxX), Math.max(maxZ, other.maxZ));
        }
    }
    record Box(double minX, double minZ, double maxX, double maxZ) implements Footprint {
        public Box {
            if (minX > maxX || minZ > maxZ) throw new IllegalArgumentException("Inverted box");
        }
        @Override public double distance(double x, double z) {
            return Math.hypot(Math.max(Math.max(minX-x, x-maxX), 0), Math.max(Math.max(minZ-z, z-maxZ), 0));
        }
        @Override public Bounds bounds() { return new Bounds(minX, minZ, maxX, maxZ); }
    }
    record Polygon(List<Point> points) implements Footprint {
        public Polygon {
            points = List.copyOf(points);
            if (points.size() < 3) throw new IllegalArgumentException("Polygon requires at least three vertices");
        }
        @Override public double distance(double x, double z) {
            boolean inside = false;
            double shortest = Double.POSITIVE_INFINITY;
            for (int i=0, j=points.size()-1; i<points.size(); j=i++) {
                Point a = points.get(j), b = points.get(i);
                double d = segmentDistanceSquared(x, z, a, b);
                if (d <= 1e-18) return 0; // Inclusive vertices and edges, including repeated vertices.
                shortest = Math.min(shortest, d);
                if ((a.z > z) != (b.z > z) && x < (b.x-a.x)*(z-a.z)/(b.z-a.z)+a.x) inside = !inside;
            }
            return inside ? 0 : Math.sqrt(shortest);
        }
        @Override public Bounds bounds() {
            double minX=Double.POSITIVE_INFINITY, minZ=minX, maxX=Double.NEGATIVE_INFINITY, maxZ=maxX;
            for (Point p : points) { minX=Math.min(minX,p.x); minZ=Math.min(minZ,p.z); maxX=Math.max(maxX,p.x); maxZ=Math.max(maxZ,p.z); }
            return new Bounds(minX,minZ,maxX,maxZ);
        }
    }
    static double segmentDistance(double x, double z, Point a, Point b) {
        return Math.sqrt(segmentDistanceSquared(x,z,a,b));
    }
    private static double segmentDistanceSquared(double x, double z, Point a, Point b) {
        double dx=b.x-a.x, dz=b.z-a.z, length=dx*dx+dz*dz;
        double t = length == 0 ? 0 : Math.clamp(((x-a.x)*dx+(z-a.z)*dz)/length, 0, 1);
        double px=x-(a.x+t*dx), pz=z-(a.z+t*dz);
        return px*px+pz*pz;
    }
}
