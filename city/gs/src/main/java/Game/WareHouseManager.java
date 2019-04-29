package Game;

import Game.Meta.MetaWarehouse;
import Game.Timers.PeriodicTimer;
import Shared.Util;
import com.google.protobuf.ByteString;
import com.sun.org.apache.regexp.internal.RE;
import gs.Gs;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class WareHouseManager {
    private static WareHouseManager instance=new WareHouseManager();
    public static WareHouseManager instance() {
        return instance;
    }
    public static Map<UUID, WareHouse> wareHouseMap = new HashMap<>();
    private PeriodicTimer timer = new PeriodicTimer((int) TimeUnit.SECONDS.toMillis(1));

    static {
        City.instance().forEachBuilding(b->{
            if(b instanceof  WareHouse){
                wareHouseMap.put(b.id(), (WareHouse) b);
            }
        });
    }

    //定时检查任务
    public void update(Long diffNano){
        if (timer.update(diffNano)){
            Set<WareHouse> updateSet = new HashSet<>();
            wareHouseMap.values().forEach(wareHouse->{
                Set<WareHouseRenter> renters = wareHouse.rentersOverdueAndRemove();//要删除的过期租户信息
                if(renters!=null){//表命当前仓库是需要修改的，添加到修改列表
                    updateSet.add(wareHouse);
                }
                renters.forEach(renter -> {
                    renter.setWareHouse(null);
                    wareHouse.getRenters().remove(renter);
                    GameDb.delete(renter);
                    System.out.println("已删除租户"+wareHouse);
                    System.out.println("集散中心是"+wareHouse.id()+"名称"+wareHouse.getName());
                    GameDb.saveOrUpdate(wareHouse);
                });
            });
        }
    }
    //设置仓库出租信息
    public Boolean settingWareHouseRentInfo(UUID playerId, Gs.setWareHouseRent setting){
        UUID bid = Util.toUuid(setting.getBuildingId().toByteArray());
        int capacity = setting.getRentCapacity();
        int minHours = setting.getMinHourToRent();
        int maxHours = setting.getMaxHourToRent();
        int money = setting.getRent();
        Building building = City.instance().getBuilding(bid);
        if(!(building instanceof  WareHouse))
            return false;
        WareHouse wareHouse = (WareHouse) building;
        wareHouse.store.setOtherUseSize(0);//清空仓库的其他使用容量
        if(wareHouse.canUseBy(playerId)&&capacity>=0
                &&minHours>=wareHouse.metaWarehouse.minHourToRent
                &&minHours<=maxHours
                &&maxHours<=wareHouse.metaWarehouse.maxHourToRent
                &&wareHouse.store.availableSize()>=capacity&&money>=0){
            wareHouse.store.setOtherUseSize(capacity);
            wareHouse.setRentCapacity(capacity);
            wareHouse.setMinHourToRent(minHours);
            wareHouse.setMaxHourToRent(maxHours);
            wareHouse.setRent(money);
            GameDb.saveOrUpdate(wareHouse);
            //同步缓存
            wareHouseMap.put(wareHouse.id(), wareHouse);
            return true;
        }else
            return false;
    }
}
