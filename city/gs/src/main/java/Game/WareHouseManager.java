package Game;

import Game.OrderGenerateUtil.OrderCodeFactory;
import Game.Timers.PeriodicTimer;
import Shared.LogDb;
import Shared.Util;
import gs.Gs;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class WareHouseManager {
    private static WareHouseManager instance = new WareHouseManager();

    public static WareHouseManager instance() {
        return instance;
    }

    public static Map<UUID, WareHouse> wareHouseMap = new HashMap<>();//任何增删改之后，都需要同步到此数据集中
    private PeriodicTimer timer = new PeriodicTimer((int) TimeUnit.SECONDS.toMillis(1));

    static {
        City.instance().forEachBuilding(b -> {
            if (b instanceof WareHouse) {
                wareHouseMap.put(b.id(), (WareHouse) b);
            }
        });
    }

    //定时检查任务
    public void update(Long diffNano) {
        if (timer.update(diffNano)) {
            wareHouseMap.values().forEach(wareHouse -> {
                Set<WareHouseRenter> renters = wareHouse.rentersOverdueAndRemove();//要删除的过期租户信息
                renters.forEach(renter -> {
                    renter.setWareHouse(null);
                    wareHouse.getRenters().remove(renter);
                    //设置已使用容量减少
                    wareHouse.setRentUsedCapacity(wareHouse.getRentUsedCapacity() - renter.getRentCapacity());
                    if (!wareHouse.isRent()) {//如果未出租了，需要修改其他容量为租仓库的已使用容量
                        wareHouse.updateOtherSize();
                    }
                    GameDb.delete(renter);
                    System.out.println("已删除租户" + wareHouse);
                    System.out.println("集散中心是" + wareHouse.id() + "名称" + wareHouse.getName());
                    GameDb.saveOrUpdate(wareHouse);
                });
            });
        }
    }

    //设置仓库出租信息
    public Boolean settingWareHouseRentInfo(UUID playerId, Gs.SetWareHouseRent setting) {
        UUID bid = Util.toUuid(setting.getBuildingId().toByteArray());
        int capacity = setting.getRentCapacity();
        int minHours = setting.getMinHourToRent();
        int maxHours = setting.getMaxHourToRent();
        int money = setting.getRent();
        boolean isRent = setting.getEnableRent();
        Building building = City.instance().getBuilding(bid);
        if (!(building instanceof WareHouse))
            return false;
        WareHouse wareHouse = (WareHouse) building;
        wareHouse.store.setOtherUseSize(0);//清空仓库的其他使用容量
        //获取已经租出去的容量(你设置的值必须比已经租出去的容量大，否则设置失败)
        if (wareHouse.canUseBy(playerId)
                && capacity >= 0
                && isRent == true
                && minHours >= wareHouse.metaWarehouse.minHourToRent
                && minHours <= maxHours
                && maxHours <= wareHouse.metaWarehouse.maxHourToRent
                && wareHouse.store.availableSize() >= capacity && money >= 0
                && capacity >= wareHouse.getRentUsedCapacity()) {
            wareHouse.openRent();//开启出租
            wareHouse.store.setOtherUseSize(capacity);
            wareHouse.setRentCapacity(capacity);
            wareHouse.setMinHourToRent(minHours);
            wareHouse.setMaxHourToRent(maxHours);
            wareHouse.setRent(money);
            GameDb.saveOrUpdate(wareHouse);
            //同步缓存
            wareHouseMap.put(wareHouse.id(), wareHouse);
            return true;
        } else
            return false;
    }

    //关闭出租(重置容量)
    public boolean closeWareHouseRentInfo(UUID playerId, Gs.SetWareHouseRent setting) {
        UUID bid = Util.toUuid(setting.getBuildingId().toByteArray());
        int capacity = setting.getRentCapacity();
        int minHours = setting.getMinHourToRent();
        int maxHours = setting.getMaxHourToRent();
        int money = setting.getRent();
        boolean isRent = setting.getEnableRent();
        Building building = City.instance().getBuilding(bid);
        if (!(building instanceof WareHouse))
            return false;
        WareHouse wareHouse = (WareHouse) building;
        if (wareHouse.canUseBy(playerId)
                && isRent == false
                && capacity == 0
                && minHours == 0
                && maxHours == 0
                && money == 0) {
            //修改出租容量
            wareHouse.closeRent();//开启出租
            wareHouse.updateOtherSize();
            wareHouse.setRentCapacity(0);
            wareHouse.setMinHourToRent(0);
            wareHouse.setMaxHourToRent(0);
            wareHouse.setRent(0);
            GameDb.saveOrUpdate(wareHouse);
            //同步缓存
            wareHouseMap.put(wareHouse.id(), wareHouse);
            return true;
        } else
            return false;
    }

    public Gs.rentWareHouse rentWareHouse(Player player, Gs.rentWareHouse rentInfo) {
        UUID bid = Util.toUuid(rentInfo.getBid().toByteArray());
        UUID renterId = Util.toUuid(rentInfo.getRenterId().toByteArray());
        int hourToRent = rentInfo.getHourToRent();//租的时间
        int rentCapacity = rentInfo.getRentCapacity();//容量
        int rent = rentInfo.getRent();//价格
        Long startTime = System.currentTimeMillis();//起租时间
        Building building = City.instance().getBuilding(bid);
        if (!(building instanceof WareHouse) || building == null)
            return null;
        WareHouse wareHouse = (WareHouse) building;
        if (wareHouse.isRent()
                && wareHouse.getRentCapacity() - wareHouse.getRentUsedCapacity() >= rentCapacity
                && player.money() >= rent
                && player.id() != renterId
                && hourToRent >= wareHouse.getMinHourToRent()
                && hourToRent <= wareHouse.getMaxHourToRent()
                && rentCapacity > 0
                && rent == wareHouse.getRent() * hourToRent * rentCapacity) {
            wareHouse.setRentUsedCapacity(wareHouse.getRentUsedCapacity() + rentCapacity);
            UUID owner = wareHouse.ownerId();
            Player bOwner = GameDb.getPlayer(owner);
            player.decMoney(rent);
            bOwner.addMoney(rent);
            MoneyPool.instance().add(rent);
            WareHouseRenter wareHouseRenter = new WareHouseRenter(renterId, wareHouse, rentCapacity, startTime, hourToRent, rent);
            wareHouse.addRenter(wareHouseRenter);
            wareHouse.updateTodayRentIncome(rent);//修改今日出租收入
            wareHouseRenter.setWareHouse(wareHouse);
            wareHouseRenter.setOrderId(OrderCodeFactory.getOrderId(wareHouse.metaId()));//自动生成订单号
            LogDb.rentWarehouseIncome(wareHouseRenter.getOrderId(), bid, renterId, startTime, startTime + hourToRent * 3600 * 1000, hourToRent, rent, rentCapacity);
            LogDb.playerPay(player.id(), rent);
            LogDb.playerIncome(owner, rent);
            List updateList = new ArrayList();
            updateList.addAll(Arrays.asList(player, wareHouse, bOwner));
            GameDb.saveOrUpdate(updateList);
            //更新缓存数据
            wareHouseMap.put(wareHouse.id(), wareHouse);
            Gs.rentWareHouse.Builder builder = rentInfo.toBuilder().setOrderNumber(wareHouseRenter.getOrderId()).setStartTime(startTime);
            return builder.build();
        } else
            return null;
    }

    //根据玩家id获取租的仓库
    public List<WareHouseRenter> getWareHouseByRenterId(UUID renterId) {
        List<WareHouseRenter> renters = new ArrayList<>();
        wareHouseMap.values().forEach(w -> {
            w.getRenters().forEach(r -> {
                if (r.getRenterId().equals(renterId))
                    renters.add(r);
            });
        });
        return renters;
    }

    //获取所有租户
    public List<WareHouseRenter> getAllRenter() {
        List<WareHouseRenter> renters = new ArrayList<>();
        wareHouseMap.values().forEach(w -> {
            w.getRenters().forEach(r -> {
                renters.add(r);
            });
        });
        return renters;
    }
    //从集散中心获取玩家租的信息
    public List<WareHouseRenter> getWareHouseByRenterIdFromWareHouse(UUID bid,UUID renterId) {
        List<WareHouseRenter> renters = new ArrayList<>();
        wareHouseMap.values().forEach(w -> {
           if(w.id().equals(bid)){
               w.getRenters().forEach(r -> {
                   if (r.getRenterId().equals(renterId))
                       renters.add(r);
               });
           }
        });
        return renters;
    }


    //同步数据
    public static void  updateWareHouseMap(WareHouse wareHouse){
        wareHouseMap.put(wareHouse.id(), wareHouse);
    }
    public static void  updateWareHouseMap(WareHouseRenter renter){
        wareHouseMap.put(renter.getWareHouse().id(), renter.getWareHouse());
    }

}
