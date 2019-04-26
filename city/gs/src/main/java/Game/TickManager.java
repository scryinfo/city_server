package Game;

import org.hibernate.annotations.Cascade;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Transient;
import java.util.*;

@Entity(name = "TickManager")
public class TickManager {
    public static final int ID = 0;
    @Id
    private Integer id = ID;

    public static TickManager instance(){
        return  tickManager;
    }

    public static void init() {
        GameDb.initTickMgr();
        tickManager = GameDb.getTickMgr(tickManager);
        if(tickManager._tickerList == null)
            tickManager._tickerList = new ArrayList<>();
    }

    private static TickManager tickManager;
    //@OneToMany(mappedBy = "tickManager", orphanRemoval = true)
    @OneToMany(orphanRemoval = true)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    List<Building> _tickerList;
    public void registerTick(Building obj){
        _tickerList.add(obj);
        GameDb.saveOrUpdate(this);
    }
    public void unRegisterTick(Building obj){
        _tickerList.remove(obj);
    }
    public void tick(long delta){
        _tickerList.forEach(ticker -> ticker.tick(delta));
    }
}
