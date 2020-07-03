package Game.Technology;

import Game.Player;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.UUID;

/*Technology Point*/
@Entity
public class SciencePoint {
    @Id
    @GeneratedValue
    UUID id;
    public int  type;
    public UUID pid;
    public long point;

    public UUID getId() {
        return id;
    }

    public int getType() {
        return type;
    }


    public UUID getPid() {
        return pid;
    }

    public SciencePoint(UUID pid,int type, long sciencePoint) {
        this.pid = pid;
        this.type = type;
        this.point = sciencePoint;
    }

    public SciencePoint() {
    }


}
