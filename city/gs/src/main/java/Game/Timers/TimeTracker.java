package Game.Timers;

import java.util.concurrent.TimeUnit;

public class TimeTracker extends TimerBase {
    public TimeTracker() {
    }

    public TimeTracker(int expiry) {
        reset(expiry);
    }

    public void update(long diffNano) {
        if (!passed())
            t -= diffNano;
    }

    public int left_duration() {
        return t < 0 ? 0 : (int)TimeUnit.NANOSECONDS.toMillis(t);
    }

    public boolean passed() {
        return t <= 0;
    }

    public void reset() {
        t = expiryTime;
    }

    public void resetAccurate() {
        if (t < 0)
            t += expiryTime;
        else
            t = expiryTime;
    }

    public void reset(int expiry) {
        long nano = TimeUnit.MILLISECONDS.toNanos(expiry);
        t = nano;
        expiryTime = nano;
    }

    public int expiry() {
        return (int) TimeUnit.NANOSECONDS.toMillis(expiryTime);
    }

    public void extend_duration(int duration) {
        t += TimeUnit.MILLISECONDS.toNanos(duration);
        // should I do expiryTime += (duration - t)?
    }

    private long t = 0;
    private long expiryTime = 0;
}
