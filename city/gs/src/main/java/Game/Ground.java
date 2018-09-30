package Game;

import com.vladmihalcea.hibernate.type.array.IntArrayType;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.util.*;
import java.util.stream.Stream;

@Embeddable
public class Ground {
    @Transient
    private TreeSet<Coord> coords = new TreeSet<>();

    @TypeDef(
            name = "int-array",
            typeClass = IntArrayType.class
    )
    @Embeddable //hide those members, the only purpose is to mapping to the table
    protected static class _D {
        @Type( type = "int-array" )
        @Column(
                name = "ground",
                columnDefinition = "integer[]"
        )
        private int[] groundIdx;
    }
    @Embedded
    protected final _D _d = new _D();

    // don't override this in subclass, or else this function will not gets called unless call super._base1()
    // so I name this function names strange in purpose
    @PrePersist
    @PreUpdate
    private void _1() {
        this._d.groundIdx = coords.stream().flatMap(c -> Stream.of(c.x, c.y)).mapToInt(x -> x).toArray();
    }
    @PostLoad
    private void _2() {
        Collection<Coord> coords = new ArrayList<>(this._d.groundIdx.length/2);
        for(int i = 0; i < this._d.groundIdx.length; i+=2)
            coords.add(new Coord(this._d.groundIdx[i], this._d.groundIdx[i+1]));
        try {
            this.add(coords);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public boolean containAll(CoordPair cp) {
        for (int x = cp.l.x; x <= cp.r.x; ++x) {
            for (int y = cp.l.y; y <= cp.r.y; ++y) {
                if (!this.coords.contains(new Coord(x, y)))
                    return false;
            }
        }
        return true;
    }

    public boolean containAny(CoordPair cp) {
        for (int x = cp.l.x; x <= cp.r.x; ++x) {
            for (int y = cp.l.y; y <= cp.r.y; ++y) {
                if (this.coords.contains(new Coord(x, y)))
                    return true;
            }
        }
        return false;
    }

    public boolean containAny(Collection<Coord> coords) {
        for (Coord c : coords) {
            if (this.coords.contains(c))
                return true;
        }
        return false;
    }

    public boolean containAll(Collection<Coord> coords) {
        for (Coord c : coords) {
            if (!this.coords.contains(c))
                return false;
        }
        return true;
    }

    public void add(CoordPair cp) throws Exception {
        if (this.containAny(cp))
            throw new Exception("intersection found");
        this.coords.addAll(cp.coords());
    }

    public void add(Collection<Coord> coords) throws Exception {
        if (this.containAny(coords))
            throw new Exception("intersection found");
        this.coords.addAll(coords);
    }
}
