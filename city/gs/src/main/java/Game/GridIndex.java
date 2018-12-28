package Game;

import gs.Gs;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Embeddable
public class GridIndex implements Comparable<GridIndex> {
    @Column(name = "x", nullable = false)
    public int x;
    @Column(name = "y", nullable = false)
    public int y;
    GridIndex(int x, int y) {
        this.x = x;
        this.y = y;
    }
    protected GridIndex(){}
    Gs.GridIndex toProto() {
        return Gs.GridIndex.newBuilder().setX(x).setY(y).build();
    }
    @Override
    public final boolean equals(Object obj) {
        if(obj instanceof GridIndex)
            return this.compareTo((GridIndex)obj) == 0;//this.x == ((Game.GridIndex)obj).x && this.y == ((Game.GridIndex)obj).y;
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    GridIndexPair toSyncRange() {
        GridIndexPair res = new GridIndexPair();
        int delta = Grid.SYNC_RANGE_DELTA;
        int ux = this.x-delta<0?0:this.x-delta;
        int uy = this.y-delta<0?0:this.y-delta;
        res.l = new GridIndex(ux, uy);

        int bx = this.x+delta>=City.GridMaxX?City.GridMaxX-1:this.x+delta;
        int by = this.y+delta>=City.GridMaxY?City.GridMaxY-1:this.y+delta;
        res.r = new GridIndex(bx, by);
        return res;
    }
    List<Coordinate> toCoordinates() {
        List<Coordinate> res = new ArrayList<>();
        for(int i = this.x*City.GridX; i < (this.x+1)*City.GridX; ++i) {
            for(int j = this.y*City.GridY; j < (this.y+1)*City.GridY; ++j) {
                res.add(new Coordinate(i,j));
            }
        }
        return res;
    }
    @Override
    public int compareTo(GridIndex o) {
        if(this.x < o.x)
            return -1;
        else if(this.x == o.x)
        {
            if(this.y < o.y)
                return -1;
            else if(this.y == o.y)
                return 0;
            else
                return 1;
        }
        else
            return 1;
    }
}
