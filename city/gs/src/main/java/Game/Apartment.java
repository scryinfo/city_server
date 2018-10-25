package Game;

import gs.Gs;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.PostLoad;
import javax.persistence.Transient;
import java.util.*;

@Entity(name = "apartment")
public class Apartment extends Building {

    public Apartment(MetaApartment meta, Coord pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
    }
    @Transient
    private MetaApartment meta;

    @Column(nullable = false)
    private int rent;

    @Transient
    private Map<UUID, Npc> guest = new HashMap<>();

    @Transient
    private Deque<Integer> incomingHistory = new ArrayDeque<>();

    public Apartment() {
    }

    @PostLoad
    private void _1() {
        //this.meta = MetaData.getApartment(this._d.metaId);
        this.meta = (MetaApartment) super.metaBuilding;
        //this.metaBuilding = this.meta;
    }

    public void setRent(int n) {
        this.rent = n;
    }



    public int rent() {
        return rent;
    }
    public void take(Npc npc) {
        guest.put(npc.id(), npc);
    }

    public Gs.Apartment detailProto() {
        return Gs.Apartment.newBuilder()
                .setInfo(super.toProto())
                .setRent(this.rent)
                .setRenter(guest.size())
                .setChart(Gs.Nums.newBuilder().addAllNum(incomingHistory))
                .build();
    }
    public void appendDetailProto(Gs.BuildingSet.Builder builder) {
        builder.addApartment(this.detailProto());
    }
    @Override
    protected void _update(long diffNano) {

    }
}
