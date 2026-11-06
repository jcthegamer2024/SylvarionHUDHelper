package org.sylvarion.hudhelper.geo;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.sylvarion.hudhelper.config.HudSettings;
import org.sylvarion.hudhelper.neighborhood.RegionInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;

/** Bounded main-thread capture -> one pure worker batch -> version-checked main-thread publication. */
public final class GeographicalLocationService implements Listener, AutoCloseable {
    private static final StreetIndex EMPTY_INDEX = new StreetIndex(List.of());
    private final JavaPlugin plugin;
    private final Supplier<HudSettings> settings;
    private final Map<UUID, LocationSnapshot> snapshots = new ConcurrentHashMap<>();
    // Everything below, except the immutable fallback and the worker's own internals, is main-thread confined.
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final ArrayDeque<UUID> playerRotation = new ArrayDeque<>();
    private final Map<UUID, WorldState> worlds = new HashMap<>();
    private final ArrayDeque<UUID> worldRotation = new ArrayDeque<>();
    private final ArrayDeque<LocationBatch.Rebuild> rebuilds = new ArrayDeque<>();
    private final Set<String> warned = new HashSet<>();
    private final BoundedWorker<LocationBatch.Input, LocationBatch.Result> worker =
            new BoundedWorker<>("SylvarionHUDHelper-location", LocationBatch::compute);
    private volatile LocationSnapshot emptySnapshot;
    private BukkitTask task;
    private long tick, nextWarning;
    private boolean closed;

    public GeographicalLocationService(JavaPlugin plugin, Supplier<HudSettings> settings) {
        this.plugin = plugin;
        this.settings = settings;
        emptySnapshot = LocationSnapshot.fallback(settings.get().geography(), "Awaiting location update");
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) add(player.getUniqueId());
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::frame, 1L, 1L);
    }

    private void frame() {
        if (closed) return;
        tick++;
        try {
            publishCompleted();
            captureGeometry();
            if (!worker.busy()) {
                List<LocationBatch.Request> requests = capturePlayers();
                LocationBatch.Rebuild rebuild = nextRebuild();
                if (rebuild != null || !requests.isEmpty()) worker.submit(new LocationBatch.Input(rebuild, requests));
            }
        } catch (RuntimeException ex) {
            warn("Location update failed; retaining last accepted results and retrying.", ex);
        }
    }

    /** Constant-time cache read, safe for PAPI's synchronous and asynchronous callers. */
    public LocationSnapshot snapshot(Player player) {
        LocationSnapshot result = player == null ? null : snapshots.get(player.getUniqueId());
        return result == null ? emptySnapshot : result;
    }

    /** Diagnostics queue a refresh rather than performing expensive work inside a command. */
    public LocationSnapshot resolve(Player player, boolean force) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Refresh requests require the server thread");
        Session session = add(player.getUniqueId());
        session.nextSample = 0;
        session.regions = null;
        WorldState world = worlds.get(session.world);
        if (force && world != null) world.nextScan = 0;
        return snapshot(player);
    }

    private List<LocationBatch.Request> capturePlayers() {
        List<LocationBatch.Request> requests = new ArrayList<>();
        HudSettings config = settings.get();
        int visits = Math.min(playerRotation.size(), config.workBudget().playersPerTick());
        for (int i = 0; i < visits; i++) {
            UUID id = playerRotation.removeFirst();
            Session session = sessions.get(id);
            if (session == null) continue;
            playerRotation.addLast(id);
            if (tick < session.nextSample || tick < session.blockedUntil) continue;
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) { remove(id); continue; }
            session.nextSample = tick + config.geography().updateInterval();
            Location loc = player.getLocation();
            try {
                World world = loc.getWorld();
                if (world == null) { unavailable(id, session, loc, "World unavailable"); continue; }
                if (!world.getUID().equals(session.world)) {
                    session.reset(); session.world = world.getUID(); snapshots.remove(id);
                }
                RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
                if (manager == null) {
                    if (worlds.containsKey(world.getUID())) removeWorld(world.getUID());
                    unavailable(id, session, loc, "Region data unavailable or disabled");
                    continue;
                }
                WorldState state = ensureWorld(world, manager);
                session.world = world.getUID();
                RegionSample sample = session.regions;
                if (sample == null || !sample.matches(loc) || tick - sample.tick >= config.workBudget().stationaryRefreshTicks()) {
                    List<RegionInfo> regions = new ArrayList<>();
                    for (var region : manager.getApplicableRegions(BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())))
                        regions.add(new RegionInfo(region.getId().toLowerCase(Locale.ROOT), region.getPriority()));
                    sample = new RegionSample(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), tick, List.copyOf(regions));
                    session.regions = sample;
                }
                requests.add(new LocationBatch.Request(id, session.token, world.getUID(), world.getName(),
                        loc.getX(), loc.getY(), loc.getZ(), tick, sample.regions, state.index, state.version,
                        session.tracker.state(), config.geography(), config.neighborhoods(), session.spatial));
            } catch (RuntimeException ex) {
                unavailable(id, session, loc, "WorldGuard lookup failed");
                warn("WorldGuard capture failed; using configured fallbacks.", ex);
            }
        }
        return requests;
    }

    private WorldState ensureWorld(World world, RegionManager manager) {
        WorldState state = worlds.get(world.getUID());
        if (state == null || state.manager != manager) {
            if (state != null) removeWorld(world.getUID());
            state = new WorldState(world.getName(), manager);
            worlds.put(world.getUID(), state);
            worldRotation.addLast(world.getUID());
        }
        state.lastUsed = tick;
        return state;
    }

    /** Copy at most N configured regions per tick, rotating worlds for fairness. No tree building here. */
    private void captureGeometry() {
        int remaining = settings.get().workBudget().regionsPerTick();
        int idle = 0;
        while (remaining > 0 && !worldRotation.isEmpty() && idle < worldRotation.size()) {
            UUID id = worldRotation.removeFirst();
            WorldState state = worlds.get(id);
            if (state == null) continue;
            worldRotation.addLast(id);
            long activeWindow = Math.max(settings.get().geography().geometryRefresh(),
                    Math.max(settings.get().geography().updateInterval(),
                            2L * (sessions.size() / settings.get().workBudget().playersPerTick() + 1))) + 20;
            if (tick - state.lastUsed > activeWindow) { idle++; continue; }
            if (state.pending || (state.iterator == null && tick < state.nextScan)) { idle++; continue; }
            idle = 0;
            if (state.iterator == null) {
                state.scanToken = new Object();
                state.collected = new ArrayList<>();
                state.iterator = (settings.get().geography().enabled() ? settings.get().geography().streets().values() : List.<GeoConfig.Street>of()).iterator();
            }
            if (state.iterator.hasNext()) {
                remaining--;
                GeoConfig.Street street = state.iterator.next();
                if (street.inWorld(state.name)) {
                    try {
                        var region = state.manager.getRegion(street.id());
                        Footprint geometry = region == null ? null : WorldGuardGeometry.copy(region);
                        if (geometry != null) state.collected.add(new LocationBatch.Geometry(street, geometry));
                        else if (warned.add(id + ":" + street.id()))
                            plugin.getLogger().warning("Street region " + street.id() + " in " + state.name
                                    + " is missing or unsupported; proximity is disabled for this entry. Building assignments may still use its name.");
                    } catch (RuntimeException ex) {
                        // A broken scan must not publish a partial city index.
                        state.iterator = null; state.collected = null;
                        state.nextScan = tick + settings.get().geography().geometryRefresh();
                        warn("Street geometry capture failed; keeping the previous index.", ex);
                        continue;
                    }
                }
            }
            if (!state.iterator.hasNext()) {
                rebuilds.addLast(new LocationBatch.Rebuild(id, state.scanToken, state.version, new Object(), state.index, state.collected));
                state.iterator = null; state.collected = null; state.pending = true;
            }
        }
    }

    private LocationBatch.Rebuild nextRebuild() {
        while (!rebuilds.isEmpty()) {
            LocationBatch.Rebuild build = rebuilds.removeFirst();
            WorldState state = worlds.get(build.world());
            if (state != null && state.scanToken == build.scanToken()) return build;
        }
        return null;
    }

    private void publishCompleted() {
        LocationBatch.Result completed;
        try { completed = worker.poll(); }
        catch (RuntimeException ex) {
            // Release failed build reservations; the next scan retries with fresh geometry.
            for (WorldState state : worlds.values()) if (state.pending) {
                state.pending = false; state.scanToken = new Object();
                state.nextScan = tick + settings.get().geography().geometryRefresh();
            }
            rebuilds.clear();
            warn("Background location calculation failed; retrying without blocking the server thread.", ex);
            return;
        }
        if (completed == null) return;
        LocationBatch.Built built = completed.built();
        if (built != null) {
            WorldState state = worlds.get(built.world());
            if (state != null && state.scanToken == built.scanToken()) {
                state.index = built.index(); state.version = built.version();
                state.pending = false; state.nextScan = tick + settings.get().geography().geometryRefresh();
                if (built.changed()) {
                    for (var entry : sessions.entrySet()) if (built.world().equals(entry.getValue().world)) {
                        entry.getValue().tracker = new StreetTracker(); entry.getValue().spatial = null;
                        entry.getValue().nextSample = 0;
                        snapshots.remove(entry.getKey());
                    }
                }
            }
        }
        for (LocationBatch.Output output : completed.players()) {
            Session session = sessions.get(output.player());
            WorldState world = worlds.get(output.world());
            if (session == null || world == null || !output.validFor(session.token, session.world, world.version)) continue;
            session.tracker = output.tracker(); session.spatial = output.cache();
            LocationSnapshot previous = snapshots.put(output.player(), output.snapshot());
            if (settings.get().debug() && (previous == null || !previous.hierarchy().equals(output.snapshot().hierarchy())
                    || !previous.streetName().equals(output.snapshot().streetName())))
                plugin.getLogger().info("Location for " + output.player() + ": " + output.snapshot().hierarchy() + " / " + output.snapshot().streetName());
        }
    }

    private void unavailable(UUID id, Session session, Location loc, String status) {
        session.reset();
        session.nextSample = tick + settings.get().geography().updateInterval();
        World world = loc.getWorld();
        session.world = world == null ? null : world.getUID();
        snapshots.put(id, new LocationSnapshot(session.world, world == null ? "unknown" : world.getName(),
                loc.getX(), loc.getY(), loc.getZ(), status, List.of(), emptySnapshot.hierarchy(), null, emptySnapshot.streetName()));
    }

    private Session add(UUID id) {
        Session session = sessions.get(id);
        if (session == null) { session = new Session(); sessions.put(id, session); playerRotation.addLast(id); }
        return session;
    }
    private void reset(Player player) {
        Session session = add(player.getUniqueId());
        session.reset(); session.nextSample = 0; session.blockedUntil = tick + 1;
        snapshots.remove(player.getUniqueId());
    }
    private void remove(UUID id) { sessions.remove(id); playerRotation.remove(id); snapshots.remove(id); }
    private void removeWorld(UUID id) {
        worlds.remove(id); worldRotation.remove(id);
        rebuilds.removeIf(build -> build.world().equals(id));
        for (var entry : sessions.entrySet()) if (id.equals(entry.getValue().world)) {
            entry.getValue().reset(); entry.getValue().nextSample = 0;
            snapshots.remove(entry.getKey());
        }
    }

    public void invalidate() {
        emptySnapshot = LocationSnapshot.fallback(settings.get().geography(), "Awaiting location update");
        snapshots.clear(); sessions.clear(); playerRotation.clear(); worlds.clear(); worldRotation.clear(); rebuilds.clear(); warned.clear();
        for (Player player : Bukkit.getOnlinePlayers()) add(player.getUniqueId());
        // In-flight old-generation results are discarded on publication, never waited for here.
    }

    private void warn(String message, RuntimeException ex) {
        if (tick < nextWarning) return;
        nextWarning = tick + 1200;
        plugin.getLogger().log(Level.WARNING, message + " (Warnings limited to once/minute.)", ex);
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) { add(event.getPlayer().getUniqueId()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { remove(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) { reset(event.getPlayer()); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) { reset(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR) public void onRespawn(PlayerRespawnEvent event) { reset(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        UUID id = event.getWorld().getUID(); removeWorld(id);
        warned.removeIf(key -> key.startsWith(id + ":"));
    }

    @Override public void close() {
        closed = true;
        if (task != null) task.cancel();
        worker.close();
        HandlerList.unregisterAll(this);
        snapshots.clear(); sessions.clear(); playerRotation.clear(); worlds.clear(); worldRotation.clear(); rebuilds.clear(); warned.clear();
    }

    private static final class Session {
        private Object token = new Object();
        private UUID world;
        private StreetTracker tracker = new StreetTracker();
        private LocationBatch.SpatialCache spatial;
        private RegionSample regions;
        private long nextSample, blockedUntil;
        private void reset() { token = new Object(); world = null; tracker = new StreetTracker(); spatial = null; regions = null; }
    }
    private record RegionSample(int x, int y, int z, long tick, List<RegionInfo> regions) {
        private boolean matches(Location loc) { return x == loc.getBlockX() && y == loc.getBlockY() && z == loc.getBlockZ(); }
    }
    private static final class WorldState {
        private final String name;
        private final RegionManager manager;
        private StreetIndex index = EMPTY_INDEX;
        private Object version = new Object(), scanToken;
        private long nextScan, lastUsed;
        private boolean pending;
        private Iterator<GeoConfig.Street> iterator;
        private List<LocationBatch.Geometry> collected;
        private WorldState(String name, RegionManager manager) { this.name = name; this.manager = manager; }
    }
}
