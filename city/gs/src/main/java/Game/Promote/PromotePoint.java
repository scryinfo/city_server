package Game.Promote;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.util.UUID;

/*Promotion points*/
@Entity
public class PromotePoint {
    @Id
    @GeneratedValue
    public UUID id;
    public UUID pid;//Player id
    public int  type;//Promotion type id
    public long promotePoint;//Points

    public PromotePoint() {
    }

    public PromotePoint(UUID pid, int type, long promotePoint) {
        this.pid = pid;
        this.type = type;
        this.promotePoint = promotePoint;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPid() {
        return pid;
    }

    public void setPid(UUID pid) {
        this.pid = pid;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public long getPromotePoint() {
        return promotePoint;
    }

    public void setPromotePoint(long promotePoint) {
        this.promotePoint = promotePoint;
    }
}
