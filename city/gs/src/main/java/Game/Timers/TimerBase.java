package Game.Timers;

public class TimerBase {
    public TimerBase(){}
    public void start()
    {
        start = true;
    }
    // stop a timer will not affect current time passed, if need reset, call reset
    public void stop()
    {
        start = false;
    }
    public boolean isStart()
    {
        return start;
    }
    private boolean start = false;
}
