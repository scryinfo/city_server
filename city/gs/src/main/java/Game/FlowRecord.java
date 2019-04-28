package Game;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.util.UUID;
import gs.Gs;
@Entity(name = "flow_records")
public class FlowRecord extends Record{
    FlowRecord(){}
    FlowRecord(UUID pid, int newTs, int newValue){
        playerId = pid;
        ts = newTs;
        value = newValue;
    }
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    UUID playerId = null;
}
