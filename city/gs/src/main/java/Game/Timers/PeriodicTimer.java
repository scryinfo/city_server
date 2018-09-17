package Game.Timers;

import java.util.concurrent.TimeUnit;

public class PeriodicTimer {
    public PeriodicTimer(int period, int delay)
    {
        setPeriodic(period, delay);
    }
    public PeriodicTimer(int period)
    {
        setPeriodic(period, 0);
    }
    public PeriodicTimer(){}
    public boolean update(long diffNano)
    {
        t -= diffNano;
        if(t > 0)
            return false;

        t += period;
        return true;
    }
    public void setPeriodic(long period, long delay)
    {
        this.t = TimeUnit.MILLISECONDS.toNanos(delay);
        this.period = TimeUnit.MILLISECONDS.toNanos(period);
    }
    public int period() {
        return (int) TimeUnit.NANOSECONDS.toMillis(period);
    }
    private long period;
    private long t;
}
