package org.sylvarion.hudhelper.neighborhood;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NeighborhoodSelectorTest {
    @Test void ignoresUnconfiguredAndGlobalRegionsEvenWithHigherPriority() {
        var regions = List.of(new RegionInfo("shop", 1000), new RegionInfo("__global__", Integer.MAX_VALUE),
                new RegionInfo("central", 1), new RegionInfo("riverside", 10));
        assertEquals("riverside", NeighborhoodSelector.select(regions,
                Map.of("central", "Central", "riverside", "Riverside", "__global__", "Never")).orElseThrow().id());
    }

    @Test void resolvesTiesIndependentlyOfIterationOrder() {
        var regions = new ArrayList<>(List.of(new RegionInfo("old_town", 5), new RegionInfo("central", 5)));
        for (int seed = 0; seed < 50; seed++) {
            Collections.shuffle(regions, new Random(seed));
            assertEquals("central", NeighborhoodSelector.select(regions, Map.of("central", "Central", "old_town", "Old Town"))
                    .orElseThrow().id());
        }
    }

    @Test void supportsNegativeAndExtremePriorities() {
        assertEquals("max", NeighborhoodSelector.select(List.of(new RegionInfo("min", Integer.MIN_VALUE),
                new RegionInfo("negative", -1), new RegionInfo("max", Integer.MAX_VALUE)),
                Map.of("min", "Min", "negative", "Negative", "max", "Max")).orElseThrow().id());
    }

    @Test void noConfiguredMatchProducesNoSelection() {
        assertTrue(NeighborhoodSelector.select(List.of(new RegionInfo("shop", 2)), Map.of("city", "City")).isEmpty());
        assertTrue(NeighborhoodSelector.select(List.of(), Map.of("city", "City")).isEmpty());
    }
}
