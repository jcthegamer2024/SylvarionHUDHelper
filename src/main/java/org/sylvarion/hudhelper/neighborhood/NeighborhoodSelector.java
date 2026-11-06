package org.sylvarion.hudhelper.neighborhood;

import java.util.*;

/** Selection policy independent of Bukkit and WorldGuard for future data providers. */
public final class NeighborhoodSelector {
    private NeighborhoodSelector() {}
    public static final Comparator<RegionInfo> ORDER = Comparator.comparingInt(RegionInfo::priority)
            .reversed().thenComparing(RegionInfo::id);

    public static Optional<RegionInfo> select(Collection<RegionInfo> regions, Map<String, String> configured) {
        return regions.stream().filter(region -> !region.id().equals("__global__"))
                .filter(region -> configured.containsKey(region.id())).min(ORDER);
    }
}
