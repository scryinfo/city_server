package Game.Timers;

public class DateTimeTracker {
    private long begin;
    private long end;

    public void resetEnd(long end) {
        this.end = end;
    }

    enum State {
        NOT_BEGIN,
        IN_PROGRESS,
        FINISHED
    }
    private State state;
    public DateTimeTracker(long begin, long end) {
        this.begin = begin;
        this.end = end;
        checkState();

    }
    public void update(long diffNano) {
        checkState();
    }
    public boolean passed() {
        return this.state == State.FINISHED;
    }
    private void checkState() {
        long now = System.currentTimeMillis();
        if(now < begin)
            state = State.NOT_BEGIN;
        else if(now >= end)
            state = State.FINISHED;
        else
            state = State.IN_PROGRESS;
    }
}
