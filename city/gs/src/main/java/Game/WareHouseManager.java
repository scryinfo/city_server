package Game;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class WareHouseManager {
    private static WareHouseManager instance=new WareHouseManager();
    public static WareHouseManager instance() {
        return instance;
    }
    public static Map<UUID, WareHouse> wareHouseMap = new HashMap<>();
    static {
       City.instance().forEachBuilding(p->{
           if(p instanceof WareHouse){
               wareHouseMap.put(p.id(), (WareHouse) p);
           }
       });
    }

    //定时检查任务
    public void update(Long diffNano){
        final long now = System.currentTimeMillis();
        Set<Map.Entry<UUID, WareHouse>> entries = wareHouseMap.entrySet();
        for (Map.Entry<UUID, WareHouse> entry : entries) {
            WareHouse wareHouse = entry.getValue();
            if(wareHouse.getRenters().size()>0){
                WareHouseRenter renter = wareHouse.getRenters().iterator().next();
                if(now - renter.getBeginTs() >= TimeUnit.HOURS.toMillis(renter.getHourToRent())){//超期了
                    System.out.println("仓库超期了");
                    //删除租的仓库
                    wareHouse.getRenters().remove(renter);
                    //renter
                    renter.setWareHouse(null);//清楚关系关系
                   GameDb.delete(renter);
                   System.out.println("已删除");
                }
            }
        }
    }
}
