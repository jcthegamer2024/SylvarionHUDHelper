# SylvarionHUDHelper

Geographical location detection for Sylvarion City: area, neighbourhood and nearby street, including buildings, upper floors and underground locations. This upgrades the existing plugin, preserving its main class, commands, permissions and internal PlaceholderAPI expansion.

| Placeholder | Example | Default fallback |
| --- | --- | --- |
| `%sylhud_area%` | Sylvarion City | Wilderness |
| `%sylhud_neighborhood%` | Central District | empty string |
| `%sylhud_street%` | Central Avenue | empty string |

Values are independent plain text. MythicHUD handles placement/styling; Nexo handles the pack. No HUD assets or configuration files are generated or modified. Postcode, building, zone and station placeholders remain unimplemented.

## Build and install

Use Java 25 and the existing Gradle 9.7.0 wrapper:

```powershell
.\gradlew.bat clean build
```

Linux/macOS: `./gradlew clean build` (first `chmod +x gradlew` if necessary). Output: `build/libs/SylvarionHUDHelper-1.0.0.jar`.

1. Back up your server's `plugins/SylvarionHUDHelper/config.yml` and stop Paper.
2. Replace the old plugin JAR in `plugins` with the built JAR. Keep only one copy.
3. Keep your existing compatible Paper 26.3, PlaceholderAPI 2.12.3, WorldGuard 7.x and FAWE installation. Use FAWE as WorldEdit; do not install another standalone WorldEdit alongside it.
4. Start Paper and check startup logs, then configure and run `/sylhud reload`.

The standard `plugin.yml` declares `org.sylvarion.hudhelper.SylvarionHUDHelper`, API 26.3, and hard dependencies on PlaceholderAPI/WorldGuard. There is no separate eCloud expansion. Use server restarts for JAR upgrades rather than Bukkit `/reload` or third-party unloaders.

Production dependencies remain compile-only and unchanged:

| Artifact | Pinned version | Repository metadata |
| --- | --- | --- |
| `io.papermc.paper:paper-api` | `26.3.build.244-stable` | [Paper](https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml) |
| `me.clip:placeholderapi` | `2.12.3` | [PlaceholderAPI](https://repo.extendedclip.com/releases/me/clip/placeholderapi/maven-metadata.xml) |
| `com.sk89q.worldguard:worldguard-bukkit` | `7.0.21` | [WorldGuard](https://maven.enginehub.org/repo/com/sk89q/worldguard/worldguard-bukkit/maven-metadata.xml) |
| `com.sk89q.worldedit:worldedit-bukkit` | `7.4.6` | [WorldEdit](https://maven.enginehub.org/repo/com/sk89q/worldedit/worldedit-bukkit/maven-metadata.xml) |

WorldGuard's published POM also references WorldEdit 7.4.6. Only public APIs are used, with no reflection/FAWE internals. WorldGuard and FAWE themselves must support Paper 26.3. Your installed WorldGuard development binary was not supplied; compilation cannot certify an unspecified development build. Live compatibility remains a server smoke test. No new production libraries were added; tests also use these existing WorldGuard/WorldEdit artifacts.

API references: [WorldGuard spatial queries](https://worldguard.enginehub.org/en/latest/developer/regions/spatial-queries/), [region geometry](https://worldguard.enginehub.org/en/latest/developer/regions/protected-region/), [PlaceholderAPI internal expansions](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/).

## Safe migration

Existing administrator configuration files are **never overwritten or automatically rewritten**. Fresh installs receive the updated bundled config. Existing installs continue to support the old flat `neighborhoods` map and `settings.outside-name`, including custom names.

When there are no valid configured areas, the flat map keeps the old highest-priority/ID-tie selection. The neighbourhood fallback remains the old outside name unless `settings.fallbacks.neighborhood` explicitly replaces it. The area fallback also defaults to the old outside name. A legacy-mode notice is logged. Streets can be used in legacy mode too.

To enable hierarchy:

1. Define an actual WorldGuard area region such as `sylvarion_city`.
2. Copy your old neighbourhood display names under `areas.sylvarion_city.neighborhoods`.
3. Add the new settings, streets and overrides as appropriate; keep your custom messages.
4. Run `/sylhud reload` and check `/sylhud region` and `/papi parse`.

With valid `areas`, only children of the winning area participate. A leftover flat map is preserved but ignored for selection, with a warning. No area is guessed from naming conventions. Broken YAML/unreadable files fail reload while retaining previous settings. Invalid individual values warn and are skipped or use defaults. Missing message templates use bundled defaults without saving over your file.

## Configuration

The complete default is in `src/main/resources/config.yml`. Example:

```yaml
settings:
  debug: false
  performance:
    player-samples-per-tick: 16
    geometry-regions-per-tick: 32
    stationary-region-refresh-ticks: 20
  fallbacks:
    area: "Wilderness"
    neighborhood: ""
    street: ""
  street-detection:
    enabled: true
    default-radius: 30
    switch-delay-ticks: 10
    grace-period-ticks: 40
    update-interval-ticks: 10
    geometry-refresh-ticks: 100
    distance-hysteresis: 2

areas:
  sylvarion_city:
    display-name: "Sylvarion City"
    worlds: [world] # Optional: replace with exact Bukkit world name.
    neighborhoods:
      central_district: "Central District"
      old_town: "Old Town"
      riverside: "Riverside"
      downtown: "Downtown"
  moon_lake:
    display-name: "Moon Lake"
    neighborhoods:
      moon_lake_park: "Theme Park"
      moon_lake_resort: "Resort"

streets:
  central_avenue:
    display-name: "Central Avenue"
    detection-radius: 30
    priority: 0
    worlds: [world] # Also supported on building overrides.
  station_road:
    display-name: "Station Road"
    detection-radius: 25
    priority: 0
  oak_street:
    display-name: "Oak Street"
    detection-radius: 20
    priority: 0

building-overrides:
  central_shopping_centre:
    street: central_avenue
  sylvarion_central_station:
    street: station_road
```

Additional tuning includes optional per-entry `worlds`, `geometry-refresh-ticks`, `distance-hysteresis`, and the capture budgets under `settings.performance`. There is no configured entry-count limit. Old configurations receive these defaults in memory without rewriting the file.

IDs are normalized to lowercase; duplicate normalized IDs warn and keep the first. Global/unrelated regions never qualify. Areas, neighbourhoods and building overrides use actual WorldGuard X/Y/Z containment, including Y bounds. Select areas independently by highest WG priority, then ascending normalized ID. Select a physically applicable child only from that winning area's configured neighbourhoods, using the same priority/tie policy. A child outside the area cannot supply an area.

Each world has its own query/index keyed by UUID; geometry is never borrowed across worlds. Without `worlds`, entries intentionally match locally existing IDs in any world, preserving old behaviour. To restrict reused IDs, set `worlds: [city_world]`. Children inherit area world restrictions. Invalid world-list types skip the entry rather than broadening its scope.

Street detection is independent of area membership: a road radius can extend beyond an area boundary. Missing/unsupported street geometry disables proximity for that entry and warns once per world/entry until reload. A local building override may still explicitly assign that configured name. Unknown street references in building overrides warn and are skipped.

All three fallbacks allow `""`. Names must be nonempty plain strings: formatting tags, angle brackets, legacy colour codes and control characters are rejected to prevent HUD formatting injection. Style names in MythicHUD. Command `messages` use MiniMessage with dynamic values inserted as literal text. Invalid templates fall back to bundled defaults. Debug logs location changes; unexpected lookup failures are rate-limited to once a minute.

## Street detection

1. A currently applicable building override wins immediately. Overlapping building overrides use highest WG priority, then alphabetical building ID. They use full X/Y/Z building containment, may bypass the street radius, and never modify regions/flags.
2. Otherwise search the local street index. Horizontal street containment gives distance zero and wins over proximity-only streets, regardless of Y or WG protection priority.
3. Otherwise choose shortest horizontal boundary distance within the individual radius. Equal distances prefer higher configured street `priority`, then alphabetical street ID. WG priority is not used for automatic street selection.
4. Apply the stability policy below.

Cuboid distances use rectangle edges/corners. Polygon distances use inclusive point-in-polygon containment and shortest distance to actual line segments, clamping projections to endpoints. Concave notches, diagonal edges, vertices and repeated vertices are supported. Bounding boxes only prune searches; they never substitute for polygon geometry. Centre points are never used.

For consistency with WorldGuard's inclusive block coordinates, player X/Z is floored to its block position before horizontal containment and distance calculations. A cuboid X=0 through X=10 includes block 10 (including player X=10.9); block 11 is one horizontal block beyond its boundary. Negative coordinates use mathematical floor. Polygon vertices/edges use WG integer coordinates inclusively. Distances are on this block lattice, not fractional player precision; diagonal edge distances can still be fractional.

Y is excluded from street proximity, so the same X/Z returns the same automatic street on roofs and underground. Multi-level roads at the same X/Z require explicit assignments or configured priorities to disambiguate. You only select roads/pavements with WorldEdit/FAWE and define their WorldGuard regions. Nearby buildings need no additional selections unless an explicit override is desired.

### Stability

- Initial detection and building overrides apply immediately.
- Clearly entering another street footprint from outside it bypasses extra switching delay at the next update.
- A competing proximity winner or overlapping footprint must remain preferred for `switch-delay-ticks`. Candidate changes cancel/restart the timer.
- With equal configured priorities and both candidates inside or both outside footprints, retain the current street when its distance is within `distance-hysteresis` blocks of the nearest. Intersections remain stable. Without history selection is deterministic. Zero hysteresis retains only exact-distance ties.
- When nothing qualifies, retain the previous street for `grace-period-ticks` since its last eligibility. Switch delay cannot extend out-of-range retention beyond that grace. Set grace/delay to zero to disable them.
- Every successful teleport resets state, including short same-world teleports. World changes, respawns, quit, reload and street geometry changes reset state too. Old names never carry across worlds. Teleports remove the old snapshot immediately; fallbacks may appear until the destination sample finishes in the background. Results from the old player session are discarded.

Ticks normally run at 20/second. Timers take effect on update boundaries and real-time duration increases under lag. Defaults: update 10 ticks, switch delay 10, grace 40, hysteresis 2 blocks. For fast vehicles consider a 2–5 tick interval after checking load.

## Performance and live edits

The one internal expansion retains `persist() == true`. Synchronous and asynchronous requests read immutable cached snapshots in constant time: no WG queries, geometry scans, disk I/O or task scheduling. Null players/missing snapshots use fallbacks; unknown placeholders return `null`. `/papi reload` retains registration, `/sylhud reload` refreshes the same expansion, and disable unregisters it.

The pipeline separates safe data capture from CPU work:

1. **Main thread:** rotate through players and capture positions/applicable WG regions, up to `player-samples-per-tick` (default 16). A player's configured update interval is a minimum spacing between samples. Geometry copying rotates across worlds and handles at most `geometry-regions-per-tick` configured entries (default 32) per tick. No tree building or street-distance calculation runs here. Unmoved players reuse applicable-region snapshots for up to `stationary-region-refresh-ticks` (default 20), while moving to another block refreshes them at the next sample.
2. **One dedicated daemon worker:** build/compare immutable spatial indexes; calculate hierarchy, exact polygon distances, overrides and street stability. Each player's immutable tracker state is captured before calculation. Prepared spatial results are reused when horizontal block, region snapshot, configuration and index are unchanged; delay/grace timers still advance. Y-only movement reuses horizontal distances if region membership is unchanged. Polygon edge evaluation uses squared distances and takes one square root at the end. Explicit building overrides skip automatic proximity searches.
3. **Main thread:** poll completed work without waiting. Publish only results whose player-session token, world UUID and geometry version still match. Teleport, quit/rejoin, reload, world changes and region-manager replacement cannot revive old results.

There is **one outstanding batch total**, containing at most the player capture budget and one index rebuild. While it runs, fresh player work is deferred rather than queued. Completed geometry scans are limited to one per tracked world. This backpressure prevents an unbounded backlog. A failed calculation releases its reservation, logs a rate-limited warning and retries; shutdown interrupts the worker and rejects new work. No worker accesses Bukkit or WorldGuard, schedules server tasks, or publishes directly.

Per-world spatial trees prune by radius-expanded bounds before exact distance calculations. They avoid scanning every street per player and avoid enormous chunk grids for long roads. Worst-case queries can still touch many streets when all radii/footprints overlap. Use sensible radii. Geometry copying itself must stay synchronous and its cost depends on vertex count, so the budgets limit operation counts rather than guaranteeing a millisecond limit.

WG has no universal notification for all region/third-party changes. The main thread incrementally copies configured geometry after each `geometry-refresh-ticks` period; the worker compares snapshots and rebuilds only when changed. Idle worlds stop scanning after a short inactivity window. Deleted/redefined regions and replaced managers are detected; flags and Y-only street changes do not change horizontal geometry. Stationary area/building edits are detected at the next region snapshot refresh plus sampling delay.

Refresh latency includes the configured interval, incremental scan duration (roughly street count divided by the geometry budget), worker calculation and main-thread publication. Under high player counts or worker load, actual update spacing may exceed `update-interval-ticks`: capacity limits take precedence over generating a backlog. Normal background completion adds at least a tick of publication latency; there is no fixed completion-time guarantee. `/sylhud reload` clears caches immediately and queues fresh capture/build work; `/sylhud region` queues a region/geometry refresh and shows the latest completed sample. Initial loading or reloads can briefly show fallbacks while indexes rebuild.

Keep the defaults initially. For stronger main-thread protection lower the capture budgets (for example 4 players and 8 geometry entries per tick), accepting slower updates/startup. For fast vehicles, reduce `update-interval-ticks` only if the configured player budget and available CPU can keep up. No asynchronous design guarantees zero TPS impact: WG containment queries and copying large polygon data still run synchronously, and the worker consumes CPU. Profile with your real city/player load before increasing limits.

Quit removes player state, world unload removes world caches, and disable releases tasks/listeners/caches. None of these operations waits for an in-flight background batch. No new runtime dependencies were added.

## Commands and MythicHUD

| Command | Permission | Behaviour |
| --- | --- | --- |
| `/sylhud help` | `sylhud.admin` | Lists subcommands; also default command |
| `/sylhud info` | `sylhud.admin` | Plugin/version information |
| `/sylhud reload` | `sylhud.admin` | Reload settings, clear caches, queue fresh background calculations |
| `/sylhud region` | `sylhud.region` | Queue a refresh and show latest completed player diagnostics |

Both permissions default to operators. Console administration and permission-aware completion are preserved. Diagnostics show world, exact player X/Y/Z, applicable region IDs/priorities, configured status, selected area/neighbourhood IDs and names, street ID/name, method, current horizontal distance and active building override. Methods: `footprint`, `proximity`, `building-override`, `switch-delay`, `grace`, `fallback`. An override with no geometry reports `n/a` distance. Diagnostics do not bypass street timers.

In your existing MythicHUD text fields use these independently, retaining your layout, YAML format and styling:

```text
%sylhud_area%
%sylhud_neighborhood%
%sylhud_street%
```

Apply changes with your normal MythicHUD reload workflow. No Nexo/resource pack changes are needed by this plugin.

## Verification

`./gradlew.bat clean build` runs 55 tests covering cuboid/diagonal/concave geometry, corners/vertices, reversed winding, actual WG containment parity, geometry replacement, nearby buildings/floors/underground, hierarchy/overlaps, outside fallbacks, override precedence, distance/priority/ID selection, world scopes, stability timers, state resets, configuration reload snapshots, validation, empty fallbacks, file-byte preservation and a 10,000-street index checked against brute force. New tests cover dedicated-thread execution, 10,000 rejected backlog submissions, nonblocking polling, failure recovery, shutdown/interruption, stale-result guards, tracker isolation, unchanged-block memoization, timer progress with cached geometry, index rebuild versions and capture-budget validation. Reports: `build/reports/tests/test/index.html`.

World-change/teleport tests exercise the pure reset policy used by event handlers. Compilation/tests do not start Paper or test Bukkit event delivery, MythicHUD rendering or an unspecified installed development binary. Before deployment, smoke-test your actual plugins:

1. Confirm clean startup and `/sylhud info`.
2. Use `/papi parse me %sylhud_area%`, `/papi parse me %sylhud_neighborhood%`, `/papi parse me %sylhud_street%`; compare `/sylhud region` and HUD results.
3. Walk cuboid/diagonal/concave roads and nearby buildings, roofs and underground levels.
4. Test intersections, delay, grace, entering another footprint and overlapping assigned buildings.
5. Test area overlaps, children outside parents, unrelated high-priority regions and world restrictions.
6. Teleport, change worlds, disconnect/rejoin and confirm no old street survives.
7. Redefine/delete a street and wait for incremental geometry refresh, then use `/sylhud reload` to queue a rebuild without waiting for that interval. Change names, radii and overrides and verify reload after the background batch completes.
8. Run `/papi reload`, restart to verify file preservation, and test non-op permissions/tab completion.

## Modified file summary

Paths below are relative to the project root. Java paths are under `src/main/java/org/sylvarion/hudhelper/`; test paths are under `src/test/java/org/sylvarion/hudhelper/`.

| Files | Change |
| --- | --- |
| `build.gradle.kts` | Existing WG/WE versions added to test classpath; production dependencies unchanged |
| `src/main/resources/config.yml`, `src/main/resources/plugin.yml` | New defaults/schema/messages, capture budgets and geographical description |
| `SylvarionHUDHelper.java` | Geographic lifecycle, reload rollback and startup logging |
| `config/HudSettings.java`, new `config/GeoConfigParser.java` | Legacy compatibility and validated geographic config |
| `placeholder/SylHudExpansion.java` | Three cached placeholders in existing expansion |
| `command/HudCommand.java` | Enhanced geographic diagnostics |
| New `geo/GeoConfig.java` | Immutable area/street/building definitions |
| New `geo/Footprint.java` | Pure cuboid/polygon distances |
| New `geo/StreetIndex.java` | Cached spatial tree |
| New `geo/StreetMatch.java`, `geo/StreetTracker.java` | Street metadata and temporal stability |
| New `geo/HierarchySelector.java` | Independent area/child selection |
| New `geo/LocationSnapshot.java`, `geo/LocationEngine.java` | Cached values and pure detection orchestration |
| New `geo/WorldGuardGeometry.java` | Public API geometry adapter |
| New `geo/GeographicalLocationService.java` | Bounded main-thread capture, asynchronous pipeline, versioned publication and lifecycle |
| New `geo/LocationBatch.java`, `geo/BoundedWorker.java`, `geo/WorkBudget.java` | Pure batch calculations/memoization, single-batch executor and validated capture limits |
| Removed `neighborhood/WorldGuardNeighborhoodService.java`, `neighborhood/NeighborhoodResult.java` | Replaced by geographic service/snapshot; existing priority selector retained |
| Test `config/HudSettingsTest.java`, new `config/GeoConfigurationTest.java` | Old/new schema, validation/reload tests |
| New tests `geo/GeoFixtures.java`, `geo/FootprintTest.java`, `geo/LocationEngineTest.java`, `geo/StreetIndexTest.java`, `geo/StreetTrackerTest.java` | Geometry, scale, selection and timing tests |
| New tests `geo/LocationBatchTest.java`, `geo/BoundedWorkerTest.java` | Async boundary, result validity, backpressure, worker failure/shutdown and cache reuse |
| `README.md` | Upgrade, migration, algorithms, tuning and verification guide |

`settings.gradle.kts`, `gradle.properties`, the wrapper and existing `NeighborhoodSelectorTest` are unchanged by this upgrade. The pure engine and snapshot boundary allow future integrations without putting WG work into placeholder requests.
