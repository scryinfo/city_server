package Game;

import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.HashMap;
import java.util.Map;

public class Apartment extends Building {

    public Apartment(MetaApartment meta, Coord pos, ObjectId ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
    }
    public Apartment(MetaApartment meta, Document d) {
        super(meta, d);
        this.meta = meta;
    }

    private MetaApartment meta;

    public void setRent(int n) {
        this.rent = n;
    }
    private int rent;
    public int rent() {
        return rent;
    }
    public void take(Npc npc) {
        guest.put(npc.id(), npc);
    }
    private Map<ObjectId, Npc> guest = new HashMap<>();
}
