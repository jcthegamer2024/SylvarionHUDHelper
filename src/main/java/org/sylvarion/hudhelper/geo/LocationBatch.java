package org.sylvarion.hudhelper.geo;

import java.util.*;
import java.util.concurrent.CancellationException;
import org.sylvarion.hudhelper.neighborhood.RegionInfo;

/** Worker boundary: only copied values and immutable geometry, never Bukkit/WG objects. */
public final class LocationBatch {
    private LocationBatch() {}
    public record Geometry(GeoConfig.Street street,Footprint footprint) {}
    public record Rebuild(UUID world,Object scanToken,Object baseVersion,Object nextVersion,
                          StreetIndex previous,List<Geometry> geometry) {
        public Rebuild { geometry=List.copyOf(geometry); }
    }
    public record Built(UUID world,Object scanToken,Object version,StreetIndex index,boolean changed) {}
    public record SpatialCache(UUID world, int x,int z,List<RegionInfo> regions,StreetIndex index,
                               GeoConfig config,LocationEngine.Prepared prepared) {
        public SpatialCache { regions=List.copyOf(regions); }
        public boolean matches(Request request,StreetIndex index) {
            return world.equals(request.world()) && x==(int)Math.floor(request.x())
                    && z==(int)Math.floor(request.z()) && regions.equals(request.regions()) && this.index==index && config==request.config();
        }
    }
    public record Request(UUID player,Object session,UUID world,String worldName,double x,double y,double z,long tick,
                          List<RegionInfo> regions,StreetIndex index,Object version,StreetTracker.State tracker,
                          GeoConfig config,Map<String,String> legacy,SpatialCache cache) {
        public Request { regions=List.copyOf(regions); legacy=Map.copyOf(legacy); }
    }
    public record Output(UUID player,Object session,UUID world,Object version,LocationSnapshot snapshot,
                         StreetTracker tracker,SpatialCache cache) {
        public boolean validFor(Object session,UUID world,Object version) {
            return this.session==session && this.world.equals(world) && this.version==version;
        }
    }
    public record Input(Rebuild rebuild,List<Request> requests) { public Input { requests=List.copyOf(requests); } }
    public record Result(Built built,List<Output> players) { public Result { players=List.copyOf(players); } }

    public static Result compute(Input input) {
        checkInterrupted();
        Built built=null;
        if (input.rebuild()!=null) {
            Rebuild build=input.rebuild();
            Map<String,StreetIndex.Entry> entries=new HashMap<>();
            for (Geometry geometry : build.geometry()) {
                checkInterrupted();
                entries.put(geometry.street().id(),new StreetIndex.Entry(geometry.street(),geometry.footprint()));
            }
            boolean changed=!entries.equals(build.previous().entries());
            built=new Built(build.world(),build.scanToken(),changed ? build.nextVersion() : build.baseVersion(),
                    changed ? new StreetIndex(entries.values()) : build.previous(),changed);
        }
        List<Output> results=new ArrayList<>();
        for (Request request : input.requests()) {
            checkInterrupted();
            StreetIndex index=request.index(); Object version=request.version();
            StreetTracker tracker=StreetTracker.fromState(request.tracker());
            if (built!=null && built.world().equals(request.world()) && request.version()==input.rebuild().baseVersion()) {
                index=built.index(); version=built.version();
                if (built.changed()) tracker.reset();
            }
            SpatialCache cache=request.cache();
            if (cache==null || !cache.matches(request,index)) {
                var prepared=LocationEngine.prepare(request.worldName(),request.x(),request.z(),request.regions(),index,request.config(),request.legacy());
                cache=new SpatialCache(request.world(),(int)Math.floor(request.x()),(int)Math.floor(request.z()),
                        request.regions(),index,request.config(),prepared);
            }
            LocationSnapshot snapshot=LocationEngine.finish(request.world(),request.worldName(),request.x(),request.y(),request.z(),request.regions(),
                    index,tracker,request.tick(),request.config(),cache.prepared());
            results.add(new Output(request.player(),request.session(),request.world(),version,snapshot,tracker,cache));
        }
        return new Result(built,results);
    }
    private static void checkInterrupted() { if (Thread.currentThread().isInterrupted()) throw new CancellationException("Location worker stopped"); }
}
