package com.astroworld;

import com.astroworld.config.AstroConfig;

/** The five phases of the concert. */
public enum ShowPhase {
    IDLE, PRE_SHOW, ARRIVAL, THE_SHOW, ASTROWORLD_CHAOS, FINALE;

    public static final ShowPhase[] ORDER = {PRE_SHOW, ARRIVAL, THE_SHOW, ASTROWORLD_CHAOS, FINALE};

    public int durationTicks() {
        AstroConfig c = AstroConfig.INSTANCE;
        return 20 * switch (this) {
            case PRE_SHOW -> c.preShowSeconds;
            case ARRIVAL -> c.arrivalSeconds;
            case THE_SHOW -> c.showSeconds;
            case ASTROWORLD_CHAOS -> c.chaosSeconds;
            case FINALE -> c.finaleSeconds;
            default -> 0;
        };
    }

    public static int totalTicks() {
        int t = 0;
        for (ShowPhase p : ORDER) t += p.durationTicks();
        return t;
    }

    /** Phase for a given tick since show start; IDLE once the show is over. */
    public static ShowPhase at(int tick) {
        int t = tick;
        for (ShowPhase p : ORDER) {
            if (t < p.durationTicks()) return p;
            t -= p.durationTicks();
        }
        return IDLE;
    }

    /** Tick at which this phase begins. */
    public int startTick() {
        int t = 0;
        for (ShowPhase p : ORDER) {
            if (p == this) return t;
            t += p.durationTicks();
        }
        return t;
    }

    /** 0..1 progress through this phase for a given global tick. */
    public float progress(float tick) {
        int d = durationTicks();
        if (d <= 0) return 0;
        return Math.max(0f, Math.min(1f, (tick - startTick()) / d));
    }
}
