package Game;

import Game.Meta.MetaItem;
import Game.Meta.MetaWarehouse;
import Shared.Util;
import gs.Gs;

import javax.persistence.*;
import java.util.*;
import java.util.function.Consumer;

@Entity(name = "WareHouse")
public class WareHouse extends Building implements IStorage, IShelf {
    @Transient
    public MetaWarehouse metaWarehouse;//集散中心原型

    @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "shelf_id")
    protected Shelf shelf; //货架

    @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "store_id")
    protected Storage store;//库存

    @Column(nullable = false)
    private int rent=0;//租金(1个小时的单价，99/1)
    private int minHourToRent=0;//最小出租时间
    private int maxHourToRent=0;//最大出租时间
    private int rentCapacity=0;//出租的容量
    private int rentUsedCapacity=0;//已出租的容量
    private int rentIncome=0;//仓库收入
    private boolean enableRent=false;//是否出租
    private static final long DAY_MILLISECOND = 1000 * 3600 * 24;

    @OneToMany(mappedBy = "wareHouse",cascade ={CascadeType.ALL},fetch = FetchType.EAGER)//让租户维护关系
    protected Set<WareHouseRenter> renters = new HashSet<>();//租户信息

    //初始化原型、建筑坐标、建筑拥有者id
    public WareHouse(MetaWarehouse meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.metaWarehouse = meta;
        this.shelf = new Shelf(meta.shelfCapacity);
        this.store = new Storage(meta.storeCapacity);
    }

    public WareHouse() {}

    @Override
    public Gs.PrivateBuildingInfo getPrivateBuildingInfo() {
        return super.getPrivateBuildingInfo();
    }

    public void updateTodayRentIncome(int rent){
        if (System.currentTimeMillis() - getTodayIncomeTs() >= DAY_MILLISECOND)
        {
            rentIncome = rent;
            setTodayIncomeTs(Util.getTodayStartTs());
        }
        else
        {
            rentIncome += rent;
        }
    }

    public Gs.GetWareHouseIncomeInfo getPrivateWareHouseInfo(){
        long now = System.currentTimeMillis();
        Gs.GetWareHouseIncomeInfo.Builder builder = Gs.GetWareHouseIncomeInfo.newBuilder()
                .setBuildId(Util.toByteString(id()))
                .setTime(now);
        if (now - getTodayIncomeTs() >= DAY_MILLISECOND)
        {
            builder.setTodayIncome(0);
            builder.setRentIncome(0);
        }
        else
        {
            builder.setTodayIncome(getTodayIncome());
            builder.setRentIncome(this.rentIncome);
        }
        return builder.build();
    }

    @Override
    public int quality() {//建筑品质(暂时不做)
        return 0;
    }

    @Override
    public Gs.WareHouse detailProto()
    {
        //详细原型）
        Gs.WareHouse.Builder builder = Gs.WareHouse.newBuilder();
        builder.setInfo(super.toProto());//建筑信息初始化
        builder.setStore(this.store.toProto());//仓库
        builder.setShelf(this.shelf.toProto());//上架商品
        builder.setMaxHourToRent(this.maxHourToRent);//最大出租小时(区间)
        builder.setMinHourToRent(this.minHourToRent);
        builder.setAvailableCapacity(this.store.availableSize());//可用容量
        builder.setStoreCapacity(this.metaWarehouse.storeCapacity);//仓库容量
        builder.setRentCapacity(this.rentCapacity);//出租的容量
        builder.setRent(this.rent);//租金
        builder.setRentUsedCapacity(this.rentUsedCapacity);
        //封装集散中心的租户的信息
        if(renters!=null&&renters.size()>0){
            renters.forEach(p->builder.addRenter(p.toProto()));
        }
        return builder.build();
    }

    @PostLoad
    protected void _1() {
        this.metaWarehouse = (MetaWarehouse) super.metaBuilding;
        this.shelf.setCapacity(this.metaWarehouse.shelfCapacity);
        this.store.setCapacity(this.metaWarehouse.storeCapacity);
    }

    @Override
    public void appendDetailProto(Gs.BuildingSet.Builder builder) {//追加原型，也就是要添加一个建筑
        builder.addWareHouse(this.detailProto());
    }

    @Override
    protected void enterImpl(Npc npc) { //进入建筑
    }

    @Override
    protected void leaveImpl(Npc npc) {//离开

    }

    @Override
    protected void _update(long diffNano) {

    }

    @Override
    public boolean addshelf(Item mi, int price, boolean autoReplenish) { //上架
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
    public boolean delshelf(ItemKey id, int n, boolean unLock) {//下架
        if(this.shelf.del(id, n)) {
            if(unLock)
                this.store.unLock(id, n);
            return true;
        }
        return false;
    }

    @Override
    public Shelf.Content getContent(ItemKey id) {//获取货架目录
        return this.shelf.getContent(id);
    }

    @Override
    public boolean setPrice(ItemKey id, int price) {//设置价格
        Shelf.Content i = this.shelf.getContent(id);
        if(i == null)
            return false;
        i.price = price;
        return true;
    }

    @Override
    public boolean setAutoReplenish(ItemKey id, boolean autoRepOn) {//设置商品是否自动补货，传递补货
        Shelf.Content i = this.shelf.getContent(id);
        if(i == null)
            return false;
        this.shelf.add(new Item(id,i.n),i.price,autoRepOn);
        return  true;
    }

    @Override
    public int getSaleCount(int itemId) {//获取销售数量，你需要给我货物的id然后查询出来
        return this.shelf.getSaleNum(itemId);
    }

    @Override
    public Map<Item, Integer> getSaleDetail(int itemId) {//获取销售清单，也就是我卖出去的详细信息，买了多少个
        return this.shelf.getSaleDetail(itemId);
    }

    @Override
    public boolean reserve(MetaItem m, int n) { //进行储备，你给我一个货物选项和 数量（如果我已经存不下了，返回false，否则，存储到原先的位置上）
        return store.reserve(m, n);
    }

    @Override
    public boolean lock(ItemKey m, int n) { //锁住货物（你要运输的货物，必须要锁住）
        return store.lock(m, n);
    }

    @Override
    public boolean unLock(ItemKey m, int n) {//解锁货物
        return store.unLock(m, n);
    }

    @Override
    public Storage.AvgPrice consumeLock(ItemKey m, int n) {//使用已锁所物品
        return store.consumeLock(m, n);
    }

    @Override
    public void consumeReserve(ItemKey m, int n, int price) {//使用存储的货物
        store.consumeReserve(m, n, price);
    }

    @Override
    public void markOrder(UUID orderId) {//订购单
        store.markOrder(orderId);
    }

    @Override
    public void clearOrder(UUID orderId) {//清除订单
        store.clearOrder(orderId);
    }

    @Override
    public boolean delItem(ItemKey mi) {//删除(销毁)货物
        return this.store.delItem(mi);
    }

    @Override
    public int availableQuantity(MetaItem m) {//空闲数量，也就我仓库中空闲的数目
        return this.store.availableQuantity(m);
    }

    @Override
    public boolean has(ItemKey m, int n) {//是否有我指定货物
        return this.store.has(m,n);
    }

    @Override
    public boolean offset(ItemKey item, int n) {//抵消
        return this.store.offset(item,n);
    }

    @Override
    public boolean offset(MetaItem item, int n) {
        return this.store.offset(item,n);
    }


    public int getRent() {
        return rent;
    }

    public void setRent(int rent) {
        this.rent = rent;
    }

    public int getMinHourToRent() {
        return minHourToRent;
    }

    public void setMinHourToRent(int minHourToRent) {
        this.minHourToRent = minHourToRent;
    }

    public int getMaxHourToRent() {
        return maxHourToRent;
    }

    public void setMaxHourToRent(int maxHourToRent) {
        this.maxHourToRent = maxHourToRent;
    }

    public int getRentCapacity() {
        return rentCapacity;
    }

    public void setRentCapacity(int rentCapacity) {
        this.rentCapacity = rentCapacity;
    }

    public int getRentUsedCapacity() {
        return rentUsedCapacity;
    }

    public void setRentUsedCapacity(int rentUsedCapacity) {
        this.rentUsedCapacity = rentUsedCapacity;
    }

    public Set<WareHouseRenter> getRenters() {
        return renters;
    }

    public void setRenters(Set<WareHouseRenter> renters) {
        this.renters = renters;
    }


    public Shelf getShelf() {
        return this.shelf;
    }

    //删除租户
    public void removeRenter(WareHouseRenter renter){
        renters.remove(renter);
    }
    //添加租户
    public void addRenter(WareHouseRenter renter){
        renters.add(renter);
    }
    @Override
    public boolean delItem(Item item) {
        return this.store.delItem(item);
    }
    //到期删除租户
    public Set<WareHouseRenter> rentersOverdueAndRemove(){
        Set<WareHouseRenter> set = new HashSet<>();
        Iterator<WareHouseRenter> iterator = renters.iterator();
        while (iterator.hasNext())
        {
            WareHouseRenter renter = iterator.next();
            if (renter.isOverTime())
            {
                set.add(renter);
                iterator.remove();
            }
        }
        return set;
    }
    public boolean isRent(){
        return this.enableRent;
    }

    public void openRent(){
        this.enableRent = true;
    }

    public void closeRent(){
        this.enableRent = false;
        this.store.setOtherUseSize(this.rentUsedCapacity);//设置其他使用容量为当前已经租出去的容量
    }

    public void updateOtherSize(){
        this.store.setOtherUseSize(this.rentUsedCapacity);
    }
}
