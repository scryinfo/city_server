package Game;

import gs.Gs;
import org.bson.Document;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.util.Objects;

@Embeddable
public class Coord {
    @Column(name = "x", nullable = false)
    public int x;
    @Column(name = "y", nullable = false)
    public int y;
    public Coord(int x, int y) {
        this.x = x;
        this.y = y;
    }
    public Coord(Document d) {
        this.x = d.getInteger("x");
        this.y = d.getInteger("y");
    }
    public Coord(Gs.MiniIndex i) {
        this.x = i.getX();
        this.y = i.getY();
    }

    protected Coord() {
    }

    GridIndex toGridIndex() {
        return new GridIndex(x/City.GridMaxX, y/City.GridMaxY);
    }
    public Coord shiftLU(int n) {
        return this.offset(-n, -n);
    }
    public Coord shiftLB(int n) {
        return this.offset(-n, n);
    }
    public Coord shiftRU(int n) {
        return this.offset(-n, n);
    }
    public Coord shiftRB(int n) {
        return this.offset(n, n);
    }
    public Coord offset(int x, int y) {
        int fx = x;
        int fy = y;
        if(x != 0) {
            if(x > 0) {
                int mx = MetaData.getCity().x;
                fx = this.x + x >= mx ? mx : this.x + x;
            }
            else {
                fx = this.x - x < 0 ? 0 : this.x - x;
            }
        }
        if(y != 0) {
            if(y > 0) {
                int my = MetaData.getCity().y;
                fy = this.y + y >= my ? my : this.y + y;
            }
            else {
                fy = this.y - y < 0 ? 0 : this.y - y;
            }
        }
        return new Coord(fx, fy);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Coord coord = (Coord) o;
        return x == coord.x &&
                y == coord.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    Gs.MiniIndex toProto() {
        return Gs.MiniIndex.newBuilder()
                .setX(this.x)
                .setY(this.y)
                .build();
    }

    public Document toBson() {
        return new Document().append("x", x).append("y", y);
    }
}