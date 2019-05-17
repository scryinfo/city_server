package Game;

import Game.Action.IAction;
import Game.Meta.AIBuilding;
import Game.Meta.MetaData;
import Shared.GlobalConfig;
import org.apache.log4j.Logger;
import org.hibernate.annotations.SelectBeforeUpdate;

import javax.persistence.*;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity(name = "NPC")
@SelectBeforeUpdate(false)
@Table(name = "NPC")
public class Npc {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Npc npc = (Npc) o;
        return Objects.equals(id, npc.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    private static final Logger logger = Logger.getLogger(Npc.class);
    public Npc(Building born, long money, int type) {
        //this.id = UUID.randomUUID();
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
    public boolean canWork() {
        return type() != 10 && type() != 11;
    }
    @Id
    @GeneratedValue
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
    
    @Column(name = "status", nullable = false)
    private int status = 0; //失业状态,初始时为工作状态
    
    @Column(name = "ts", nullable = false)
    private long ts = 0;  //失业时间
 
    public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public long getTs() {
		return ts;
	}

	public void setTs(long ts) {
		this.ts = ts;
	}

	public long getUnEmployeddTs() {
		return System.currentTimeMillis()-getTs();
	}
	
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
        if(GlobalConfig.DEBUGLOG){
            if(this.born == null){
                int t = 0 ;
                return;
            }
        }
        this.born.takeAsWorker(this);
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
    public Set<Object> update(long diffNano) {
        if(this.born.outOfBusiness())
            return null;
        int id = chooseId();
        IAction.logger.info(this.id().toString() + " choose " + id);
        AIBuilding aiBuilding = MetaData.getAIBuilding(id);
        if(aiBuilding == null)
           return null;

        double idleRatio = 1.d;
        double sumFlow = City.instance().getSumFlow();
        if(sumFlow > 0)
           idleRatio = 1.d - (double)this.buildingLocated().getFlow() / sumFlow;
        IAction.logger.info("sumFlow " + sumFlow + " idle " + idleRatio);

        BrandManager.BuildingRatio r = BrandManager.instance().getBuildingRatio();
        if(this.hasApartment())
            r.apartment = 0;
        IAction action = aiBuilding.random(idleRatio, r, id);
        if(action == null) {
            logger.fatal("flow error, this building pos" + this.buildingLocated().coordinate() + " flow " + this.buildingLocated().getFlow());
            return null;
        }
        return action.act(this);
    }

    public int type() {
        return type;
    }
    public int chooseId() {
        int id = type*100000000;
        id += City.instance().currentHour()*1000000;
        id += City.instance().weather()*10000;
        id += MetaData.getDayId()*10;
   //   id += this.born.onStrike()?1:0;
        id += status;
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
            if(!hangOut) {
                if (this.tempBuilding != null) {
                    this.tempBuilding.leave(this);
                    this.tempBuilding = null;
                }
            }
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
    public void setBorn(Building born) {
		this.born = born;
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
