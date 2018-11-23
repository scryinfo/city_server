package Game;

import gs.Gs;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.PostLoad;
import javax.persistence.Transient;
import java.util.*;

@Entity(name = "apartment")
public class Apartment extends Building {

    public Apartment(MetaApartment meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
        this.qty = meta.qty;
    }
    @Transient
    private MetaApartment meta;

    private int qty;

    @Column(nullable = false)
    private int rent;

    @Transient
    private Map<UUID, Npc> renters = new HashMap<>();

    @Transient
    private Deque<Integer> incomingHistory = new ArrayDeque<>();

    public Apartment() {
    }

    @Override
    public int cost() {
        return this.rent;
    }
    @Override
    public int quality() {
        return this.qty;
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

    public Gs.Apartment detailProto() {
        return Gs.Apartment.newBuilder()
                .setInfo(super.toProto())
                .setRent(this.rent)
                .setRenter(renters.size())
                .setChart(Gs.Nums.newBuilder().addAllNum(incomingHistory))
                .setQty(qty)
                .build();
    }
    public void appendDetailProto(Gs.BuildingSet.Builder builder) {
        builder.addApartment(this.detailProto());
    }

    @Override
    protected void enterImpl(Npc npc) {
        npc.setApartment(this);
        renters.put(npc.id(), npc);
    }

    @Override
    protected void leaveImpl(Npc npc) {
        npc.setApartment(null);
        renters.remove(npc.id());
    }

    @Override
    protected void _update(long diffNano) {

    }
}
