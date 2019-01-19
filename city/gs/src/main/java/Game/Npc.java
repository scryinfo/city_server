package Game;

import Game.Action.IAction;
import Game.Meta.AIBuilding;
import Game.Meta.MetaData;
import org.apache.log4j.Logger;

import javax.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "NPC")
public class Npc {
    private static final Logger logger = Logger.getLogger(Npc.class);
    public Npc(Building born, long money, int type) {
        this.id = UUID.randomUUID();
        this.born = born;
        this.money = money;
        this.type = type;
        this.tempBuilding = null;
    }
    public Npc(Building born, long money, UUID id) {
        this.id = id;
        this.born = born;
        this.money = money;
        this.type = Npc.TALENT_TYPE;
        this.tempBuilding = null;
    }
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

    private int type;

    public long money() {
        return money;
    }
    protected Npc() {}

    public int salary() {
        return this.born.singleSalary();
    }

    public void idle() {
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
        int id = chooseId();
        IAction.logger.info(this.id().toString() + " choose " + id);
        AIBuilding aiBuilding = MetaData.getAIBuilding(id);
        if(aiBuilding == null)
           return;

        double idleRatio = 1.d;
        double sumFlow = City.instance().getSumFlow();
        if(sumFlow > 0)
           idleRatio = 1.d - (double)this.buildingLocated().getFlow() / sumFlow;
        IAction.logger.info("sumFlow " + sumFlow + " idle " + idleRatio);
        IAction action = aiBuilding.random(idleRatio, BrandManager.instance().getBuildingRatio(), id);
        action.act(this);
    }

    public int type() {
        return type;
    }
    public int chooseId() {
        int id = type*100000000;
        id += City.instance().currentHour()*1000000;
        id += City.instance().weather()*10000;
        id += MetaData.getDayId()*10;
        id += this.born.onStrike()?1:0;
        return id;
    }
    // where the npc is are not important, location phaseChange can not persist to db
    // after server restarted, all npc will return to its owner born
    public void goFor(Building building) {
        visit(building, false);
        building.enter(this);
    }
    public void hangOut(Building building) {
        visit(building, true);
    }
    private void visit(Building building, boolean hangOut) {
        if(building == this.born) {
            if(!hangOut)
                this.tempBuilding.leave(this);
            this.tempBuilding = null;
        }
        else {
            if(!hangOut)
                this.born.leave(this);
            this.tempBuilding = building;
        }
    }

    public void setApartment(Apartment apartment) {
        this.apartment = apartment;
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
        MoneyPool.instance().add(this.money);
    }
    public void goHome() {
        if(apartment == null)
            this.visit(born, true);
        else
            this.visit(apartment, true);
    }
    public void goWork() {
        this.visit(born, true);
    }
    public boolean isTalent() {
        return type == TALENT_TYPE;
    }
    public static final int TALENT_TYPE = 19;
}
