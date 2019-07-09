package Game;

import Game.Meta.MetaData;
import gs.Gs;
import org.bson.Document;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.util.Objects;

@Embeddable
public class Coordinate {
    @Column(name = "x", nullable = false)
    public int x;
    @Column(name = "y", nullable = false)
    public int y;

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }

    public Coordinate(int x, int y) {
        this.x = x;
        this.y = y;
    }
    public Coordinate(Document d) {
        this.x = d.getInteger("x");
        this.y = d.getInteger("y");
    }
    public Coordinate(Gs.MiniIndex i) {
        this.x = i.getX();
        this.y = i.getY();
    }

    public Coordinate() {}
    public static double distance(Coordinate a, Coordinate b) {
        return Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2));
    }
    public GridIndex toGridIndex() {
        return new GridIndex(x/City.GridX, y/City.GridY);
    }
    public Coordinate shiftLU(int n) {
        return this.offset(-n, -n);
    }
    public Coordinate shiftLB(int n) {
        return this.offset(-n, n);
    }
    public Coordinate shiftRU(int n) {
        return this.offset(-n, n);
    }
    public Coordinate shiftRB(int n) {
        return this.offset(n, n);
    }
    public Coordinate offset(int x, int y) {
        int fx = x;
        int fy = y;
        if(x != 0) {
            if(x > 0) {
                int mx = MetaData.getCity().x;
                fx = this.x + x >= mx ? mx : this.x + x;
            }
            else {
                fx = this.x + x < 0 ? 0 : this.x + x;
            }
        }
        else
            fx = this.x;
        if(y != 0) {
            if(y > 0) {
                int my = MetaData.getCity().y;
                fy = this.y + y >= my ? my : this.y + y;
            }
            else {
                fy = this.y + y < 0 ? 0 : this.y + y;
            }
        }
        else
            fy = this.y;
        return new Coordinate(fx, fy);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Coordinate coordinate = (Coordinate) o;
        return x == coordinate.x &&
                y == coordinate.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    public Gs.MiniIndex toProto() {
        return Gs.MiniIndex.newBuilder()
                .setX(this.x)
                .setY(this.y)
                .build();
    }
}