package Game.Eva;

import java.util.Objects;

/*This type is used to encapsulate and describe the type of Eva, which is convenient for query*/
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
