package Game.CityInfo;

import Game.Eva.EvaManager;
import Game.GameDb;
import Game.Timers.PeriodicTimer;
import gs.Gs;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Transient;
import java.util.concurrent.TimeUnit;

@Entity
public class CityLevel {
    public static final int ID = 0;
    private static CityLevel instance;
    public static CityLevel instance() {
        return instance;
    }
    public CityLevel() {}
    @Id
    public final int id = ID;
    private long sumValue;
    public static void init() {
        GameDb.initCityLevel();
        instance = GameDb.getCityLevel();
    }
    @Transient
    private PeriodicTimer timer = new PeriodicTimer((int) TimeUnit.HOURS.toMillis(2));
    public void update(long diffNano) {
        if (timer.update(diffNano)) {
            sumValue = EvaManager.getInstance().getAllSumValue();
            GameDb.saveOrUpdate(this);
        }
    }

    public Gs.CityLevel toProto() {
        return Gs.CityLevel.newBuilder().setSumValue(this.sumValue).build();
    }

   /* public void updateCityLevel(int totalPoint){
        *//*城市等级升级*//*
        *//*1.当前的城市经验点数=减去当前等级已经使用的点数，因为总点数是累加的不会清0*//*
        *//*2.当前的城市经验点数 和城市的升级所需的经验值对比，如果满足就升级，和Eva一样，否则只增加经验值不升级 *//*
        *//*3. 每升1级都发明一个新的商品和产生对应的原料*//*
    }*/
}
