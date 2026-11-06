package org.sylvarion.hudhelper.geo;

import java.util.*;

/** Balanced bounding volume tree; expanded bounds prune candidates, exact geometry decides distance. */
public final class StreetIndex {
    public record Entry(GeoConfig.Street street, Footprint footprint, Footprint.Bounds bounds) {
        public Entry(GeoConfig.Street street, Footprint footprint) { this(street,footprint,footprint.bounds().expand(street.radius())); }
        public StreetMatch match(double x, double z) {
            double distance = footprint.distance(x,z);
            return new StreetMatch(street, distance, distance == 0 ? "footprint" : "proximity", null);
        }
    }
    private final Map<String, Entry> entries;
    private final Node root;
    public StreetIndex(Collection<Entry> entries) {
        Map<String, Entry> map = new HashMap<>();
        entries.forEach(entry -> map.put(entry.street.id(), entry));
        this.entries = Map.copyOf(map);
        root = build(new ArrayList<>(entries));
    }
    public Map<String, Entry> entries() { return entries; }
    public List<StreetMatch> nearby(double x, double z) {
        List<StreetMatch> results = new ArrayList<>();
        query(root,x,z,results);
        return results;
    }
    private static Node build(List<Entry> entries) {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Street index build stopped");
        if (entries.isEmpty()) return null;
        Footprint.Bounds bounds = entries.getFirst().bounds();
        for (Entry e : entries) bounds = bounds.union(e.bounds());
        if (entries.size() <= 8) return new Node(bounds, List.copyOf(entries), null, null);
        boolean splitX = bounds.maxX()-bounds.minX() >= bounds.maxZ()-bounds.minZ();
        entries.sort(Comparator.comparingDouble(e -> splitX ? e.bounds().minX()+e.bounds().maxX() : e.bounds().minZ()+e.bounds().maxZ()));
        int mid=entries.size()/2;
        return new Node(bounds, List.of(), build(new ArrayList<>(entries.subList(0,mid))), build(new ArrayList<>(entries.subList(mid,entries.size()))));
    }
    private static void query(Node node, double x, double z, List<StreetMatch> results) {
        if (node == null || !node.bounds.contains(x,z)) return;
        for (Entry entry : node.leaves) {
            if (!entry.bounds().contains(x,z)) continue;
            StreetMatch match = entry.match(x,z);
            if (match.distance() <= entry.street.radius()+1e-9) results.add(match);
        }
        query(node.left,x,z,results); query(node.right,x,z,results);
    }
    private record Node(Footprint.Bounds bounds, List<Entry> leaves, Node left, Node right) {}
}
