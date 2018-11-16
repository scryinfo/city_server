package Game;

import javax.persistence.*;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "NPC")
public class Npc {
    @Id
    private UUID id;

    @Transient
    private Building born;

    @Transient
    private Building tempBuilding;

    @Transient
    private Apartment apartment;

    @Column(name = "money", nullable = false)
    private long money;

    public long money() {
        return money;
    }
    protected Npc() {
    }



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
        this.adapterData.buildingId = born.id();
        this.adapterData.tempBuildingId = tempBuilding==null?null:tempBuilding.id();
        this.adapterData.apartmentId = apartment==null?null:apartment.id();
    }
    @PostLoad
    private void _2() {
        this.born = City.instance().getBuilding(this.adapterData.buildingId);
        this.tempBuilding = this.adapterData.tempBuildingId==null?null:City.instance().getBuilding(this.adapterData.tempBuildingId);
        this.apartment = this.adapterData.apartmentId==null?null: (Apartment) City.instance().getBuilding(this.adapterData.apartmentId);
    }
    public Npc(Building building, int salary) {
        this.id = UUID.randomUUID();
        this.born = building;
        this.money = salary;
        this.tempBuilding = null;
    }
//    public Npc(Document doc) {
//        this.id = doc.getObjectId("_id");
//        this.city = City.instance();
//        this.born = city.getBuilding(doc.getObjectId("b"));
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
//                .append("b", this.born.id())
//                .append("t", this.tempBuilding == null? Util.NullOid:this.tempBuilding.id())
//                .append("m", this.money);
//        return doc;
//    }
    public Coordinate coordinate() {
        return this.tempBuilding == null? this.born.coordinate():this.tempBuilding.coordinate();
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

       int id = chooseId();
       AIBuilding aiBuilding = MetaData.getAIBuilding(id);
       if(aiBuilding == null)
           return;
        Set<Building> buildings;
       switch (aiBuilding.random(BrandManager.instance().getBuildingRatio())) {
           case IDLE:
               break;
           case GOTO_APARTMENT:
               buildings = buildingLocated().getAllBuildingEffectMe(MetaBuilding.APARTMENT);
               break;
           case GOTO_PUBLIC_FACILITY:
               buildings = buildingLocated().getAllBuildingEffectMe(MetaBuilding.PUBLIC);
               break;
           case GOTO_RETAIL_SHOP:
               buildings = buildingLocated().getAllBuildingEffectMe(MetaBuilding.RETAIL);
               break;
       }
    }
    private Building chooseOne(Set<Building> buildings) {
        return null;
    }
    private int type;
    private boolean stopWork;
    public int chooseId() {
        int id = type*100000000;
        id += City.instance().currentHour()*1000000;
        id += City.instance().weather()*10000;
        id += MetaData.getDayId()*10;
        id += stopWork?1:0;
        return id;
    }
    // where the npc is are not important, location change can not persist to db
    // after server restarted, all npc will return to its owner born
    public void visit(Building building) {
        building.enter(this);
        if(building == this.born) {
            this.tempBuilding.leave(this);
            this.tempBuilding = null;
        }
        else {
            this.born.leave(this);
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
    public boolean decMoney(int money) {
        if(money() < money)
            return false;
        this.money -= money;
        return true;
    }
    public Building building() {
        return this.born;
    }
    public Building buildingLocated() {
        return this.tempBuilding == null?this.born:this.tempBuilding;
    }
    public void readyForDestroy() {
        this.visit(this.born);
    }
    public void backHome() {
        if(apartment == null)
            this.visit(born);
        else
            this.visit(apartment);
    }
}
