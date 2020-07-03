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

    public static Map<UUID, WareHouse> wareHouseMap = new HashMap<>();//After any additions, deletions and changes, you need to synchronize to this data set
    private PeriodicTimer timer = new PeriodicTimer((int) TimeUnit.SECONDS.toMillis(1));

    static {
        City.instance().forEachBuilding(b -> {
            if (b instanceof WareHouse) {
                wareHouseMap.put(b.id(), (WareHouse) b);
            }
        });
    }

    //Regular inspection task
    public void update(Long diffNano) {
        if (timer.update(diffNano)) {
            wareHouseMap.values().forEach(wareHouse -> {
                Set<WareHouseRenter> renters = wareHouse.rentersOverdueAndRemove();//Expired tenant information to be deleted
                renters.forEach(renter -> {
                    renter.setWareHouse(null);
                    wareHouse.getRenters().remove(renter);
                    //Set used capacity reduced
                    wareHouse.setRentUsedCapacity(wareHouse.getRentUsedCapacity() - renter.getRentCapacity());
                    if (!wareHouse.isRent()) {//If it is not rented, you need to modify the other capacity to the used capacity of the rented warehouse
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

    //Set warehouse rental information
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
        wareHouse.store.setOtherUseSize(0);//Empty other storage capacity
        //Get the rented capacity (the value you set must be larger than the rented capacity, otherwise the setting fails)
        if (wareHouse.canUseBy(playerId)
                && capacity >= 0
                && isRent == true
                && minHours >= wareHouse.metaWarehouse.minHourToRent
                && minHours <= maxHours
                && maxHours <= wareHouse.metaWarehouse.maxHourToRent
                && wareHouse.store.availableSize() >= capacity && money >= 0
                && capacity >= wareHouse.getRentUsedCapacity()) {
            wareHouse.openRent();//Start rental
            wareHouse.store.setOtherUseSize(capacity);
            wareHouse.setRentCapacity(capacity);
            wareHouse.setMinHourToRent(minHours);
            wareHouse.setMaxHourToRent(maxHours);
            wareHouse.setRent(money);
            GameDb.saveOrUpdate(wareHouse);
            //Sync cache
            wareHouseMap.put(wareHouse.id(), wareHouse);
            return true;
        } else
            return false;
    }

    //Close rental (reset capacity)
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
            //Modify rental capacity
            wareHouse.closeRent();//Start rental
            wareHouse.updateOtherSize();
            wareHouse.setRentCapacity(0);
            wareHouse.setMinHourToRent(0);
            wareHouse.setMaxHourToRent(0);
            wareHouse.setRent(0);
            GameDb.saveOrUpdate(wareHouse);
            //Sync cache
            wareHouseMap.put(wareHouse.id(), wareHouse);
            return true;
        } else
            return false;
    }

    public Gs.rentWareHouse rentWareHouse(Player player, Gs.rentWareHouse rentInfo) {
        UUID bid = Util.toUuid(rentInfo.getBid().toByteArray());
        UUID renterId = Util.toUuid(rentInfo.getRenterId().toByteArray());
        int hourToRent = rentInfo.getHourToRent();//Rented time
        int rentCapacity = rentInfo.getRentCapacity();//capacity
        int rent = rentInfo.getRent();//price
        Long startTime = System.currentTimeMillis();//Rental time
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
            wareHouse.updateTodayRentIncome(rent);//Modify today's rental income
            wareHouseRenter.setWareHouse(wareHouse);
            wareHouseRenter.setOrderId(OrderCodeFactory.getOrderId(wareHouse.metaId()));//Automatic order number generation
            LogDb.rentWarehouseIncome(wareHouseRenter.getOrderId(),bid, renterId,startTime + hourToRent * 3600000, hourToRent, rent, rentCapacity);
            LogDb.playerPay(player.id(), rent,0);
            LogDb.playerIncome(owner, rent,building.type());
            List updateList = new ArrayList();
            updateList.addAll(Arrays.asList(player, wareHouse, bOwner));
            GameDb.saveOrUpdate(updateList);
            //Update cache data
            wareHouseMap.put(wareHouse.id(), wareHouse);
            Gs.rentWareHouse.Builder builder = rentInfo.toBuilder().setOrderNumber(wareHouseRenter.getOrderId()).setStartTime(startTime);
            return builder.build();
        } else
            return null;
    }

    //Get rented warehouse based on player id
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

    //Get all tenants
    public List<WareHouseRenter> getAllRenter() {
        List<WareHouseRenter> renters = new ArrayList<>();
        wareHouseMap.values().forEach(w -> {
            w.getRenters().forEach(r -> {
                renters.add(r);
            });
        });
        return renters;
    }
    //Get player rent information from the distribution center
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


    //Synchronous Data
    public static void  updateWareHouseMap(WareHouse wareHouse){
        wareHouseMap.put(wareHouse.id(), wareHouse);
    }
    public static void  updateWareHouseMap(WareHouseRenter renter){
        wareHouseMap.put(renter.getWareHouse().id(), renter.getWareHouse());
    }

}
