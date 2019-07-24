package Game;

import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.UUID;

/*科技点*/
@Entity
public class SciencePoint {
    @Id
    @GeneratedValue
    UUID id;
    public int  type;
    public long sciencePoint;

    public UUID getId() {
        return id;
    }

    public int getType() {
        return type;
    }

    public long getSciencePoint() {
        return sciencePoint;
    }


    public SciencePoint(int type, long sciencePoint, Player player) {
        this.type = type;
        this.sciencePoint = sciencePoint;
        //this.player = player;
    }

    public SciencePoint() {
    }
}
