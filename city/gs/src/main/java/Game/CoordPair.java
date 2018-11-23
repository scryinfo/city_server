package Game;

import java.util.ArrayList;
import java.util.Collection;

public class CoordPair {
    public static boolean overlap(CoordPair r1, CoordPair r2) {
        return !(r1.l.x > r2.r.x || r2.l.x > r1.r.x || r1.l.y > r2.r.y || r2.l.y > r1.r.y);
    }
    public CoordPair(Coordinate l, Coordinate r) {
        assert l.x <= r.x && l.y <= r.y;
        this.l = l;
        this.r = r;
    }
    public Coordinate l;
    public Coordinate r;

    public Collection<Coordinate> toCoordinates() {
        Collection<Coordinate> res = new ArrayList<>();
        for(int x = l.x; x <= r.x; ++x) {
            for(int y = l.y; y <= r.y; ++y) {
                res.add(new Coordinate(x, y));
            }
        }
        return res;
    }
}
