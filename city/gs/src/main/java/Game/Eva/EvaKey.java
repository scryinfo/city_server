package Game.Eva;

import java.util.Objects;

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

    @Override
    public int hashCode() {
        return  Objects.hash(at, bt);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        EvaKey key = (EvaKey) obj;
        return this.at == key.at && this.bt == key.bt;
    }
}
