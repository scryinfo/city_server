package Game;

import java.util.ArrayList;
import java.util.Collection;

public class CoordPair {
    public static boolean overlap(CoordPair r1, CoordPair r2) {
        return !(r1.l.x > r2.r.x || r2.l.x > r1.r.x || r1.l.y > r2.r.y || r2.l.y > r1.r.y);
    }
    public CoordPair(Coord l, Coord r) {
        assert l.x <= r.x && l.y <= r.y;
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
