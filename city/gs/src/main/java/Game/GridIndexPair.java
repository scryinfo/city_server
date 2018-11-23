package Game;

import java.util.*;

public class GridIndexPair {
    public GridIndex l;
    public GridIndex r;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GridIndexPair that = (GridIndexPair) o;
        return Objects.equals(l, that.l) &&
                Objects.equals(r, that.r);
    }

    @Override
    public int hashCode() {
        return Objects.hash(l.hashCode(), r.hashCode());
    }

    public List<GridIndex> toIndexList() {
        List<GridIndex> res = new ArrayList<>();
        for(int i = l.x; i <= r.x; ++i)
        {
            for(int j = l.y; j <= r.y; ++j)
            {
                GridIndex idx = new GridIndex(i, j);
                res.add(idx);
            }
        }
        return res;
    }
    public Set<GridIndex> toIndexSet() {
        Set<GridIndex> res = new TreeSet<>();
        for(int i = l.x; i <= r.x; ++i)
        {
            for(int j = l.y; j <= r.y; ++j)
            {
                GridIndex idx = new GridIndex(i, j);
                res.add(idx);
            }
        }
        return res;
    }
}
