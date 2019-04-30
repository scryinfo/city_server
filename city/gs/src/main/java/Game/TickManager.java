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
    @OneToMany(mappedBy="tickManager",fetch = FetchType.LAZY)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    private Map<Long, TickGroup> _groupList; //key是表示tick间隔的时长

    public TickGroup registerTick(long tickInterval, Building obj){
        TickGroup gp = _groupList.get(tickInterval);
        if(gp == null){
            gp = new TickGroup(this, tickInterval);
            _groupList.put(tickInterval,gp);
        }
        gp.add(obj);
        obj.setTickGroup(gp);
        GameDb.saveOrUpdate(this);
        return gp;
    }
    public void unRegisterTick(long tickInterval, Building obj){
        TickGroup gp =  _groupList.get(tickInterval);
        gp.del(obj);
        if(gp.isEmpty()){
            _groupList.remove(tickInterval);
        }
        GameDb.saveOrUpdate(this);
    }
    public void tick(long deltaTime){
        for (Iterator<Map.Entry<Long, TickGroup>> it = _groupList.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Long,TickGroup> item = it.next();
            item.getValue().tick(deltaTime);
        }
    }
}