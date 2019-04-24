package Game;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.util.UUID;

@Entity(name = "city_record")
public class Record{
    Record(){}
    Record(UUID newbid,int newTp, long newTs, int newValue){
        buildingId = newbid;
        typeId = newTp;
        ts = newTs;
        value = newValue;
    }
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    UUID buildingId;
    int typeId;
    public long ts;
    public int value;
}
