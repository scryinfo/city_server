package Statistic;

public class IndustryInfo {
    public int type;
    public long  total;
    public long time;

    public IndustryInfo(int type, long total, long time) {
        this.type = type;
        this.total = total;
        this.time = time;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
