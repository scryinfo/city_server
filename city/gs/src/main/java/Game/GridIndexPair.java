package Game;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class GridIndexPair {
    public GridIndex l;
    public GridIndex r;

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
