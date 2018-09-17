package Game;

import java.util.ArrayList;
import java.util.Collection;

public class CoordPair {
    public CoordPair(Coord l, Coord r) {
        this.l = l;
        this.r = r;
    }
    public Coord l;
    public Coord r;

    public Collection<Coord> coords() {
        Collection<Coord> res = new ArrayList<>();
        for(int x = l.x; x <= r.x; ++x) {
            for(int y = r.y; y <= r.y; ++y) {
                res.add(new Coord(x, y));
            }
        }
        return res;
    }
}
