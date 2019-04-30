package Game;

import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.*;

@Entity(name = "TickManager")
public class TickManager {
    public static final int ID = 0;
    @Id
    private Integer id = ID;

    public static TickManager instance(){
        return  tickManager;
    }

    public void postLoad(){
        for (Iterator<Map.Entry<Long, TickGroup>> it = _groupList.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Long,TickGroup> item = it.next();
            GameDb.getTickGroup(item.getValue());
        }
    }

    public static void init() {
        /*GameDb.RegisterClass(PublicFacility.class);
        GameDb.RegisterClass(TickManager.class);
        GameDb.RegisterClass(TickGroup.class);
        GameDb.RegisterClass(Building.class);*/
        GameDb.initTickMgr();
        tickManager = GameDb.getTickMgr(tickManager);
        if(tickManager._groupList == null)
            tickManager._groupList = new HashMap<Long,TickGroup>();
    }

    private static TickManager tickManager;
    //@OneToMany(mappedBy="tickManager",fetch = FetchType.LAZY)
    @OneToMany(fetch = FetchType.LAZY)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    private Map<Long, TickGroup> _groupList; //key是表示tick间隔的时长

    public TickGroup registerTick(long tickInterval, Building obj, boolean needSave){
        TickGroup gp = _groupList.get(tickInterval);
        if(gp == null){
            gp = new TickGroup(this, tickInterval);
            _groupList.put(tickInterval,gp);
        }
        if(gp.add(obj) && needSave){
            obj.setTickGroup(gp);
            GameDb.saveOrUpdate(this);
        }
        return gp;
    }
    //从特定的tick组中移除某个实例的tick
    public boolean unRegisterTick(long tickInterval, Building obj, boolean needSave){
        TickGroup gp =  _groupList.get(tickInterval);
        boolean changed = gp.del(obj);
        if(gp.isEmpty()){
            _groupList.remove(tickInterval);
        }
        if(needSave && changed){
            GameDb.saveOrUpdate(this);
            return true;
        }
        return false;
    }
    //从所有tick组中移除某个实例的tick
    public boolean unRegisterTick(Building obj , boolean needSave ){
        boolean changed = false;
        for(Iterator<Map.Entry<Long,TickGroup>> it = _groupList.entrySet().iterator(); it.hasNext();){
            Map.Entry<Long,TickGroup> item = it.next();
            if(item.getValue().del(obj)){
                changed = true;
            }
        }
        if(changed && needSave){
            GameDb.saveOrUpdate(this);
            return true;
        }
        return false;
    }

    public void tick(long deltaTime){
        for (Iterator<Map.Entry<Long, TickGroup>> it = _groupList.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Long,TickGroup> item = it.next();
            item.getValue().tick(deltaTime);
        }
    }
}