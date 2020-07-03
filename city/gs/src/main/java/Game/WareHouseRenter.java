package Game;

import Game.Meta.MetaItem;
import Game.OrderGenerateUtil.OrderCodeFactory;
import Shared.Util;
import gs.Gs;

import javax.persistence.*;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @Description:Warehouse tenant table (entity mapping)
 * @Author: yty
 * @CreateDate: 2019/4/8 10:17
 * @UpdateRemark: update content:
 * @Version: 1.0
 */
@Entity
public class WareHouseRenter implements Serializable, IStorage, IShelf {
    @Id
    private Long orderId; //Order number

    private UUID renterId;    //Tenant id

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name="wareHouse_id")
    private WareHouse wareHouse;//building

    @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "store_id")
    protected Storage store;   //warehouse

    @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "shelf_id")
    protected Shelf shelf; //Shelf

    @Column(nullable = false)
    private long todayIncome = 0;

    @Column(nullable = false)
    private long todayIncomeTs = 0;//A day has passed

    private static final long DAY_MILLISECOND = 1000 * 3600 * 24;
    private Integer rentCapacity;//Rented inventory
    private Long beginTs;    //Lease start time
    private Integer hourToRent; //Lease time
    private Integer rent;       //rent
    public WareHouseRenter(
            UUID renterId,
            WareHouse wareHouse,
            Integer rentCapacity,
            Long beginTs,
            Integer hourToRent,
            Integer rent) {
        this.renterId = renterId;
        this.wareHouse = wareHouse;
        this.rentCapacity = rentCapacity;
        this.beginTs = beginTs;
        this.hourToRent = hourToRent;
        this.rent = rent;
        this.store = new Storage(rentCapacity);
        this.shelf = new Shelf(rentCapacity);
    }

    public WareHouseRenter() { }

    public UUID getRenterId() {
        return renterId;
    }

    public void setRenterId(UUID renterId) {
        this.renterId = renterId;
    }

    public WareHouse getWareHouse() {
        return wareHouse;
    }

    public void setWareHouse(WareHouse wareHouse) {
        this.wareHouse = wareHouse;
    }

    public Storage getStore() {
        return store;
    }

    public void setStore(Storage store) {
        this.store = store;
    }

    public Shelf getShelf() {
        return shelf;
    }

    public void setShelf(Shelf shelf) {
        this.shelf = shelf;
    }

    public Integer getRentCapacity() {
        return rentCapacity;
    }

    public void setRentCapacity(Integer rentCapacity) {
        this.rentCapacity = rentCapacity;
    }

    public Long getBeginTs() {
        return beginTs;
    }

    public void setBeginTs(Long beginTs) {
        this.beginTs = beginTs;
    }

    public Integer getHourToRent() {
        return hourToRent;
    }

    public void setHourToRent(Integer hourToRent) {
        this.hourToRent = hourToRent;
    }

    public Integer getRent() {
        return rent;
    }

    public void setRent(Integer rent) {
        this.rent = rent;
    }


    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public void updateTodayIncome(long income)
    {
        if (System.currentTimeMillis() - todayIncomeTs >= DAY_MILLISECOND)//More than one day
        {
            todayIncome = income;
            todayIncomeTs = Util.getTodayStartTs();
        }
        else
        {
            todayIncome += income;
        }
    }


    public Gs.PrivateBuildingInfo getPrivateWareHouseInfo(){
        long now = System.currentTimeMillis();
        Gs.PrivateBuildingInfo.Builder builder = Gs.PrivateBuildingInfo.newBuilder()
                .setBuildId(Util.toByteString(this.wareHouse.id()))
                .setTime(now);
        if (now - todayIncomeTs>= DAY_MILLISECOND)
        {
            builder.setTodayIncome(0);
        }
        else
        {
            builder.setTodayIncome(todayIncome);
        }
        return builder.build();
    }

    public Gs.WareHouseRenter toProto(){
        Gs.WareHouseRenter.Builder builder = Gs.WareHouseRenter.newBuilder();
        //Start assignment
        builder.setBuildingId(Util.toByteString(this.getWareHouse().id()))
                .setRenterId(Util.toByteString(this.renterId))
                .setBeginTs(this.beginTs)
                .setHourToRent(this.hourToRent)
                .setRentCapacity(this.rentCapacity)
                .setRent(this.rent)
                .setStore(this.store.toProto())
                .setAvailableCapacity(this.store.availableSize())
                .setShelf(this.shelf.toProto())
                .setInfo(this.wareHouse.toProto())
                .setShelfIncome(this.todayIncome)
                .setOrderId(this.orderId);
        return builder.build();
    }

    //Get all tenant information
    public void forEachRenderByBuildId(UUID buildId, Consumer<WareHouseRenter> f) {
        List<WareHouseRenter> renter = GameDb.getAllRenterByBuilderId(buildId);
        if(renter != null&&renter.size()>0) {
            renter.forEach(f);
        }
    }

    //Shelf function
    @Override
    public boolean addshelf(Item mi, int price, boolean autoReplenish) {
        if(!this.store.has(mi.key, mi.n))
            return false;
        if(this.shelf.add(mi, price,autoReplenish)) {
            this.store.lock(mi.key, mi.n);
            return true;
        }
        else
            return false;
    }

    @Override
    public boolean delshelf(ItemKey id, int n, boolean unLock) {
        if(this.shelf.del(id, n)) {
            if(unLock)
                this.store.unLock(id, n);
            else{//If it is consumption, then the number of locks that need to be consumed
                this.store.consumeLock(id, n);
            }
            return true;
        }
        return false;
    }

    @Override
    public Shelf.Content getContent(ItemKey id) {
        return shelf.getContent(id);
    }

    @Override
    public boolean setPrice(ItemKey id, int price) {
        Shelf.Content i = this.shelf.getContent(id);
        if(i == null)
            return false;
        i.price = price;
        return true;
    }

    @Override
    public boolean setAutoReplenish(ItemKey id, boolean autoRepOn) {
        Shelf.Content i = this.shelf.getContent(id);
        if(i == null)
            return false;
        this.shelf.add(new Item(id,0),i.price,autoRepOn);
        return  true;
    }

    @Override
    public int getSaleCount(int itemId) {
        return this.shelf.getSaleNum(itemId);
    }

    @Override
    public Map<Item, Integer> getSaleDetail(int itemId) {
        return shelf.getSaleDetail(itemId);
    }

    @Override
    public boolean reserve(MetaItem m, int n) {
        return store.reserve(m,n);
    }

    @Override
    public boolean lock(ItemKey m, int n) {
        return store.lock(m,n);
    }

    @Override
    public boolean unLock(ItemKey m, int n) {
        return store.unLock(m, n);
    }

    @Override
    public Storage.AvgPrice consumeLock(ItemKey m, int n) {
        return store.consumeLock(m, n);
    }

    @Override
    public void consumeReserve(ItemKey m, int n, int price) {
        store.consumeReserve(m, n, price);
    }

    @Override
    public void markOrder(UUID orderId) {
        store.markOrder(orderId);
    }

    @Override
    public void clearOrder(UUID orderId) {
        store.clearOrder(orderId);
    }

    @Override
    public boolean delItem(ItemKey mi) {
        return this.store.delItem(mi);
    }


    @Override
    public int availableQuantity(MetaItem m) {
        return this.store.availableQuantity(m);
    }

    @Override
    public boolean has(ItemKey m, int n) {
        return this.store.has(m,n);
    }

    @Override
    public boolean offset(ItemKey item, int n) {
        return this.store.offset(item,n);
    }

    @Override
    public boolean offset(MetaItem item, int n, UUID pid, int typeId) {
        return this.store.offset(item,n,pid,typeId);
    }


    public void appendDetailProto(Gs.BuildingSet.Builder builder) {
        builder.addHouseRenter(this.toProto());
    }

    @Override
    public boolean delItem(Item item) {
        return this.store.delItem(item);
    }
    public boolean isOverTime() {//Is it overdue
        return System.currentTimeMillis() > beginTs + TimeUnit.HOURS.toMillis(hourToRent);
    }

    @Override
    public boolean shelfSet(Item item, int price,boolean autoRepOn) {
        return false;
    }

    @Override
    public int getItemCount(ItemKey key) {
        return this.store.getItemCount(key);
    }

    @Override
    public int getTotalSaleCount() {
        return this.shelf.getTotalContentNum();
    }
}
