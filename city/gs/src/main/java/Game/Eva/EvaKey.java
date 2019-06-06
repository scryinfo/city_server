package Game.Eva;
/*此类用于封装和描述Eva的类型，便于做查询*/
public class EvaKey {
    private int at;
    private int bt;

    public EvaKey() {

    }

    public EvaKey(int at, int bt) {
        this.at = at;
        this.bt = bt;
    }

    public int getAt() {
        return at;
    }

    public void setAt(int at) {
        this.at = at;
    }

    public int getBt() {
        return bt;
    }

    public void setBt(int bt) {
        this.bt = bt;
    }
}
