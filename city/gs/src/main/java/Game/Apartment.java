package Game;

import gs.Gs;
import org.bson.Document;
import org.bson.types.ObjectId;

import javax.persistence.Entity;
import java.util.*;

@Entity(name = "Apartment")
public class Apartment extends Building {

    public Apartment(MetaApartment meta, Coord pos, UUID ownerId) {
        super(meta, pos, ownerId);
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
    private Map<UUID, Npc> guest = new HashMap<>();
    private Deque<Integer> incomingHistory = new ArrayDeque<>();
    public Gs.ApartmentInfo detailProto() {
        return Gs.ApartmentInfo.newBuilder()
                .setCommon(this.commonProto())
                .setRent(this.rent)
                .setRenter(guest.size())
                .setChart(Gs.Nums.newBuilder().addAllNum(incomingHistory))
                .build();
    }
}
