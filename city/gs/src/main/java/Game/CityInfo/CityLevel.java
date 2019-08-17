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
}
