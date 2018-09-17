package Game;

import java.util.*;

public class Ground {
    private TreeSet<Coord> coords = new TreeSet<>();

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
