package Game;

import gs.Gs;

public class Record {
    public int ts = 0;
    public int value;
    public Gs.Record toproto(){
        return Gs.Record.newBuilder().setTs(ts).setValue(value).build();
    }
}
