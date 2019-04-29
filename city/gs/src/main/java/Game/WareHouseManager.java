package Game;

import Game.Meta.MetaWarehouse;
import Game.Timers.PeriodicTimer;
import Shared.LogDb;
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
        //获取已经租出去的容量(你设置的值必须比已经租出去的容量大，否则设置失败)
        if(wareHouse.canUseBy(playerId)&&capacity>=0
                &&minHours>=wareHouse.metaWarehouse.minHourToRent
                &&minHours<=maxHours
                &&maxHours<=wareHouse.metaWarehouse.maxHourToRent
                &&wareHouse.store.availableSize()>=capacity&&money>=0
                &&capacity>=wareHouse.getRentUsedCapacity()){
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

    public Gs.rentWareHouse rentWareHouse(Player player,Gs.rentWareHouse rentInfo){
        UUID bid = Util.toUuid(rentInfo.getBid().toByteArray());
        UUID renterId = Util.toUuid(rentInfo.getRenterId().toByteArray());
        int hourToRent = rentInfo.getHourToRent();
        int rentCapacity = rentInfo.getRentCapacity();
        int rent = rentInfo.getRent();
        Long startTime = rentInfo.getStartTime();
        Building building = City.instance().getBuilding(bid);
        if(!(building instanceof WareHouse)||building==null)
            return null;
        WareHouse wareHouse= (WareHouse) building;
        //租金等于多少1小时1个容量
        if(wareHouse.getRentCapacity()-wareHouse.getRentUsedCapacity()>=rentCapacity
                &&player.money()>=rent
                &&player.id()!=renterId
                &&hourToRent>=wareHouse.getMinHourToRent()
                &&hourToRent<=wareHouse.getMaxHourToRent()
                &&rentCapacity>0
                &&rent==wareHouse.getRent()*hourToRent*rentCapacity){
            wareHouse.setRentUsedCapacity(wareHouse.getRentUsedCapacity()+rentCapacity);
            UUID owner = wareHouse.ownerId();
            Player bOwner = GameDb.getPlayer(owner);
            player.decMoney(rent);
            bOwner.addMoney(rent);
            MoneyPool.instance().add(rent);
            WareHouseRenter wareHouseRenter = new WareHouseRenter(renterId, wareHouse, rentCapacity, startTime, hourToRent, rent);
            LogDb.rentWarehouseIncome(wareHouseRenter.getOrderId(),bid,renterId,startTime,startTime+hourToRent*3600*1000,hourToRent,rent,rentCapacity);
            wareHouse.addRenter(wareHouseRenter);
            wareHouse.updateTodayRentIncome(rent);//修改今日出租收入
            wareHouseRenter.setWareHouse(wareHouse);
            List updateList = new ArrayList();
            updateList.addAll(Arrays.asList(player, wareHouse, bOwner));
            GameDb.saveOrUpdate(updateList);
            //更新缓存数据
            wareHouseMap.put(wareHouse.id(),wareHouse);
            Gs.rentWareHouse.Builder builder = rentInfo.toBuilder().setOrderNumber(wareHouseRenter.getOrderId());
            return builder.build();
        } else
            return null;
    }
}
