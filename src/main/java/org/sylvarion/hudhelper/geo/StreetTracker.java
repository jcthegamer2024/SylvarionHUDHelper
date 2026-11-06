package org.sylvarion.hudhelper.geo;

import java.util.*;

/** Per-player temporal policy. Caller supplies server ticks and resets on teleports/reload. */
public final class StreetTracker {
    private StreetMatch current;
    private String pending;
    private long pendingSince, lastEligible;
    private UUID world;
    public static final Comparator<StreetMatch> ORDER = Comparator.comparingDouble(StreetMatch::distance)
            .thenComparing(Comparator.comparingInt((StreetMatch m) -> m.street().priority()).reversed())
            .thenComparing(StreetMatch::id);

    public StreetMatch update(UUID world, long tick, List<StreetMatch> eligible, StreetMatch override, GeoConfig config) {
        if (!Objects.equals(this.world,world)) { reset(); this.world=world; }
        if (override != null) { current=override; lastEligible=tick; pending=null; return current; }
        StreetMatch best = eligible.stream().min(ORDER).orElse(null);
        StreetMatch stillEligible = current == null ? null : eligible.stream().filter(m -> m.id().equals(current.id())).findFirst().orElse(null);
        if (stillEligible != null) {
            current=stillEligible; lastEligible=tick;
            if (best != null && best.inside() == current.inside() && best.street().priority() == current.street().priority()
                    && current.distance()-best.distance() <= config.hysteresis()) best=current;
        }
        if (current == null) { current=best; lastEligible=tick; pending=null; return current; }
        if (best != null && best.id().equals(current.id())) { current=best; lastEligible=tick; pending=null; return current; }
        // A clear entrance into a different footprint must not wait for a proximity switch timer.
        if (best != null && best.inside() && (stillEligible == null || !stillEligible.inside())) {
            current=best; lastEligible=tick; pending=null; return current;
        }
        if (best == null) {
            pending=null;
            if (tick-lastEligible >= config.gracePeriod()) current=null;
            return current == null ? null : new StreetMatch(current.street(), current.distance(), "grace", null);
        }
        if (!best.id().equals(pending)) { pending=best.id(); pendingSince=tick; }
        if (tick-pendingSince >= config.switchDelay()) { current=best; lastEligible=tick; pending=null; return current; }
        // Do not retain an out-of-range street past its grace just to satisfy a switch delay.
        if (stillEligible == null && tick-lastEligible >= config.gracePeriod()) return null;
        return new StreetMatch(current.street(), current.distance(), "switch-delay", null);
    }
    public void reset() { current=null; pending=null; world=null; lastEligible=0; pendingSince=0; }
    public record State(StreetMatch current, String pending, long pendingSince, long lastEligible, UUID world) {}
    public State state() { return new State(current,pending,pendingSince,lastEligible,world); }
    /** The asynchronous boundary carries an immutable State, never the live mutable tracker. */
    public static StreetTracker fromState(State state) {
        StreetTracker copy=new StreetTracker();
        copy.current=state.current(); copy.pending=state.pending(); copy.world=state.world();
        copy.pendingSince=state.pendingSince(); copy.lastEligible=state.lastEligible();
        return copy;
    }
    public StreetTracker copy() { return fromState(state()); }
}
