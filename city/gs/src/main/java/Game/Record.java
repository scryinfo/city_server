package Game;
import gs.Gs;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.util.UUID;

@Entity(name = "record")
public class Record {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    public int ts = 0;
    public int value = 0;
    public Gs.Record toproto(){
        return Gs.Record.newBuilder().setTs(ts).setValue(value).build();
    }
}
