package Game;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.util.UUID;
import gs.Gs;

@Entity(name = "eva_records")
public class EvaRecord extends Record{
    EvaRecord(){}
    EvaRecord(UUID newbid,short newTp, int newTs, int newValue){
        buildingId = newbid;
        typeId = newTp;
        ts = newTs;
        value = newValue;
    }
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    UUID buildingId = null;
    public short typeId;

}
