package Game;

import Game.Contract.BuildingContract;
import Game.Contract.IBuildingContract;

import Game.Meta.MetaApartment;

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
        this.buildingContract = new BuildingContract(0, 0, false);
    }
    @Transient
    private MetaApartment meta;

    @Column(nullable = false)
    @Embedded
    private BuildingContract buildingContract;

    @Column(nullable = false)
    private int rent;

    @Transient
    private Deque<Integer> incomingHistory = new ArrayDeque<>();

    protected Apartment() {}

    @Override
    public int cost() {
        return this.rent;
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
                .setRenter(0)
                .setChart(Gs.Nums.newBuilder().addAllNum(incomingHistory))
                .setLift(getLift())
                .setContractInfo(this.buildingContract.toProto())
                .build();
    }
    public void appendDetailProto(Gs.BuildingSet.Builder builder) {
        builder.addApartment(this.detailProto());
    }

    @Override
    protected void _update(long diffNano) {

    }

    @Override
    public int getTotalSaleCount() {
        return getCapacity();
    }

    @Override
    public BuildingContract getBuildingContract()
    {
        return buildingContract;
    }


    public int getCapacity(){
        return this.meta.npc;
    }
}
