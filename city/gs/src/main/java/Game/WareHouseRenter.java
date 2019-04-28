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
import java.util.function.Consumer;

/**
 * @Description:仓库租户表（实体映射）
 * @Author: yty
 * @CreateDate: 2019/4/8 10:17
 * @UpdateRemark: 更新内容：
 * @Version: 1.0
 */
@Entity
public class WareHouseRenter implements Serializable, IStorage, IShelf {
    @Id
    private Long orderId; //订单编号

    private UUID renterId;    //租户id

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name="wareHouse_id")
    private WareHouse wareHouse;//建筑

    @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "store_id")
    protected Storage store;   //仓库

    @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "shelf_id")
    protected Shelf shelf; //货架

    @Column(nullable = false)
    private long todayIncome = 0;

    @Column(nullable = false)
    private long todayIncomeTs = 0;//一天已经过去的时间

    private static final long DAY_MILLISECOND = 1000 * 3600 * 24;
    private Integer rentCapacity;//租用的库存
    private Long beginTs;    //租用的开始时间
    private Integer hourToRent; //租赁时间
    private Integer rent;       //租金
    public WareHouseRenter(
            UUID renterId,
            WareHouse wareHouse,
            Integer rentCapacity,
            Long beginTs,
            Integer hourToRent,
            Integer rent) {
        this.orderId = OrderCodeFactory.getOrderId(this.wareHouse.metaId());//自动生成订单号
        this.renterId = renterId;
        this.wareHouse = wareHouse;
        this.rentCapacity = rentCapacity;
        this.beginTs = beginTs;
        this.hourToRent = hourToRent;
        this.rent = rent;
        this.store = new Storage(rentCapacity);
        this.shelf = new Shelf();
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
        if (System.currentTimeMillis() - todayIncomeTs >= DAY_MILLISECOND)//超过一天
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
        //开始赋值
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

    //获取所有的租户信息
    public void forEachRenderByBuildId(UUID buildId, Consumer<WareHouseRenter> f) {
        List<WareHouseRenter> renter = GameDb.getAllRenterByBuilderId(buildId);
        if(renter != null&&renter.size()>0) {
            renter.forEach(f);
        }
    }

    //上架功能
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
        this.shelf.add(new Item(id,i.n),i.price,autoRepOn);
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
    public boolean offset(MetaItem item, int n) {
        return this.store.offset(item,n);
    }


    public void appendDetailProto(Gs.BuildingSet.Builder builder) {
        builder.addHouseRenter(this.toProto());
    }

    public static void test(){
        System.out.println("开始执行++++++++++WareHourseRenter"+System.currentTimeMillis());
    }

    @Override
    public boolean delItem(Item item) {
        return this.store.delItem(item);
    }
}
