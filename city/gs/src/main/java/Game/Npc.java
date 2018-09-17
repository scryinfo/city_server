package Game;

import Shared.Util;
import org.bson.Document;
import org.bson.types.ObjectId;

public class Npc {
    private ObjectId id = new ObjectId();
    private Building building;
    private Building tempBuilding;
    private Apartment apartment;
    private boolean inTravel;
    public Npc(Building building) {
        this.building = building;
        this.city = City.instance();
        this.timeSection = city.currentTimeSectionIdx();
        this.money = 0;
        this.tempBuilding = null;
        this.inTravel = false;
    }
    public Npc(Document doc) {
        this.id = doc.getObjectId("_id");
        this.city = City.instance();
        this.building = city.getBuilding(doc.getObjectId("b"));
        this.money = doc.getInteger("m");
        ObjectId tmpBuildingId = doc.getObjectId("t");
        this.tempBuilding = tmpBuildingId == Util.NullOid?null:city.getBuilding(tmpBuildingId);
        if(tempBuilding != null)
            inTravel = true;
    }
    Document toBson() {
        Document doc = new Document()
                .append("_id", this.id)
                .append("b", this.building.id())
                .append("t", this.tempBuilding == null? Util.NullOid:this.tempBuilding.id())
                .append("m", this.money);
        return doc;
    }
    public Coord coordinate() {
        return this.tempBuilding == null? this.building.coordinate():this.tempBuilding.coordinate();
    }
    public ObjectId id() {
        return id;
    }
    private int timeSection;
    private City city;
    public void update(long diffNano) {
       int section = City.instance().currentTimeSectionIdx();
       switch(section) {
           //??
       }
    }
    private void visit(Building building) {
        if(building == this.building) {
            this.tempBuilding = null;
            this.inTravel = false;
        }
        else {
            this.tempBuilding = building;
            this.inTravel = true;
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
    private long money;
    public void addMoney(int money) {
        this.money += money;
    }

    public Building building() {
        return this.building;
    }
}
