package Game.Meta;

import org.bson.Document;

import java.util.List;
import java.util.Objects;

public class Formula {
    public static final class Key {
        public Type type;
        public int targetId;
        public int targetLv;

        public Key(Type type, int targetId, int targetLv) {
            this.type = type;
            this.targetId = targetId;
            this.targetLv = targetLv;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Key key = (Key) o;
            return targetId == key.targetId &&
                    targetLv == key.targetLv &&
                    type == key.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, targetId, targetLv);
        }
    }
    public enum Type{
        RESEARCH,
        INVENT
    }
    public Key key;
    public int phaseSec;
    public int phase;
    public int critiChance;
    public int critiV;
    public int[] successChance;
    public static final class Consume {
        public MetaMaterial m;
        public int n;
    }
    public Consume[] consumes = new Consume[3];

    Formula(Document d) {
        key = new Key(Type.values()[d.getInteger("type")], d.getInteger("good"), d.getInteger("lv"));
        phaseSec = d.getInteger("phaseSec");
        phase = d.getInteger("phase");
        critiChance = d.getInteger("critiChance");
        critiV = d.getInteger("critiV");
        List<Integer> l = (List<Integer>) d.get("successChance");
        if(l.size() != phase)
            throw new IllegalArgumentException();
        successChance = new int[l.size()];
        for(int i = 0; i < l.size(); ++i) {
            successChance[i] = l.get(i);
        }
        for(int i = 0; i < consumes.length; ++i) {
            consumes[i] = new Consume();
            consumes[i].m = MetaData.getMaterial(d.getInteger("material" + i));
            consumes[i].n = d.getInteger("num" + i);
        }
    }
}
