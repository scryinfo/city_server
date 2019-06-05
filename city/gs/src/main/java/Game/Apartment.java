package Game;

import Game.Contract.BuildingContract;
import Game.Contract.IBuildingContract;
import Game.Eva.Eva;
import Game.Eva.EvaManager;
import Game.Meta.MetaApartment;
import Game.Meta.MetaBuilding;
import gs.Gs;

import javax.persistence.*;
import java.util.*;

@Entity(name = "apartment")
public class Apartment extends Building implements IBuildingContract
{

    public Apartment(MetaApartment meta, Coordinate pos, UUID ownerId)
    {
        super(meta, pos, ownerId);
        this.meta = meta;
        this.qty = meta.qty;
        this.buildingContract = new BuildingContract(0, 0, false);
    }
    @Transient
    private MetaApartment meta;

    @Column(nullable = false)
    @Embedded
    private BuildingContract buildingContract;

    private int qty;

    @Column(nullable = false)
    private int rent;

    @Transient
    private Map<UUID, Npc> renters = new HashMap<>();

    @Transient
    private Deque<Integer> incomingHistory = new ArrayDeque<>();

    protected Apartment() {}

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
        this.meta = (MetaApartment) super.metaBuilding;
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
                .setQty(getTotalQty())//加成过后的品质
                .setLift(getLift())
                .setContractInfo(this.buildingContract.toProto())
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

    @Override
    public boolean npcSelectable() {
        return meta.npc > this.renters.size();
    }

    @Override
    public BuildingContract getBuildingContract()
    {
        return buildingContract;
    }

    private int getTotalQty(){
        Eva eva = EvaManager.getInstance().getEva(this.ownerId(),type(), Gs.Eva.Btype.Quality_VALUE);
        return (int)Math.ceil((this.qty * (1 + EvaManager.getInstance().computePercent(eva))));
    }
}
