package Game;

import javax.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "NPC")
public class Npc {
    @Id
    private UUID id;

    @Transient
    private Building building;

    @Transient
    private Building tempBuilding;

    @Transient
    private Apartment apartment;

    @Column(name = "money", nullable = false)
    private long money;

    @Embeddable //hide those members, the only purpose is to mapping to the table
    protected static class AdapterData {
        @Column(name = "buildingId", updatable = false, nullable = false)
        protected UUID buildingId;
        @Column(name = "tempBuildingId")
        protected UUID tempBuildingId;
        @Column(name = "apartmentId")
        protected UUID apartmentId;
    }
    @Embedded
    protected final AdapterData adapterData = new AdapterData();

    @PrePersist
    @PreUpdate
    private void _1() {
        this.adapterData.buildingId = building.id();
        this.adapterData.tempBuildingId = tempBuilding==null?null:tempBuilding.id();
        this.adapterData.apartmentId = apartment==null?null:apartment.id();
    }
    @PostLoad
    private void _2() {
        this.building = City.instance().getBuilding(this.adapterData.buildingId);
        this.tempBuilding = this.adapterData.tempBuildingId==null?null:City.instance().getBuilding(this.adapterData.tempBuildingId);
        this.apartment = this.adapterData.apartmentId==null?null: (Apartment) City.instance().getBuilding(this.adapterData.apartmentId);
    }
    public Npc(Building building, int salary) {
        this.id = UUID.randomUUID();
        this.building = building;
        this.money = salary;
        this.tempBuilding = null;
    }
//    public Npc(Document doc) {
//        this.id = doc.getObjectId("_id");
//        this.city = City.instance();
//        this.building = city.getBuilding(doc.getObjectId("b"));
//        this.money = doc.getInteger("m");
//        ObjectId tmpBuildingId = doc.getObjectId("t");
//        this.tempBuilding = tmpBuildingId == Util.NullOid?null:city.getBuilding(tmpBuildingId);
//        if(tempBuilding != null)
//        {
//            tempBuilding.enter(this);
//            inTravel = true;
//        }
//    }
//    Document toBson() {
//        Document doc = new Document()
//                .append("_id", this.id)
//                .append("b", this.building.id())
//                .append("t", this.tempBuilding == null? Util.NullOid:this.tempBuilding.id())
//                .append("m", this.money);
//        return doc;
//    }
    public Coord coordinate() {
        return this.tempBuilding == null? this.building.coordinate():this.tempBuilding.coordinate();
    }
    public UUID id() {
        return id;
    }
    public boolean hasApartment() {
        return this.apartment != null;
    }
    public void update(long diffNano) {
       int section = City.instance().currentTimeSectionIdx();
       switch(section) {
           //??
       }
    }

    // where the npc is are not important, location change can not persist to db
    // after server restarted, all npc will return to its owner building
    public void visit(Building building) {
        building.enter(this);
        if(building == this.building) {
            this.tempBuilding.leave(this);
            this.tempBuilding = null;
        }
        else {
            this.building.leave(this);
            this.tempBuilding = building;
        }
    }
    public boolean rent(Apartment apartment) {
        if(apartment.rent() > this.money)
            return false;
        this.money -= apartment.rent();
        this.apartment = apartment;
        apartment.take(this);
        return true;
    }
    public void addMoney(int money) {
        this.money += money;
    }

    public Building building() {
        return this.building;
    }

    public void readyForDestory() {
        this.visit(this.building);
    }
}
