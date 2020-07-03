package Game;

import Game.Eva.Eva;
import Game.Eva.EvaManager;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaItem;
import Game.Meta.MetaWarehouse;
import Shared.Util;
import gs.Gs;

import javax.persistence.*;
import java.util.*;

@Entity(name = "WareHouse")
public class WareHouse extends Building implements IStorage, IShelf {
    @Transient
    public MetaWarehouse metaWarehouse;//Distribution center prototype

    @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "shelf_id")
    protected Shelf shelf; //Shelf

    @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "store_id")
    protected Storage store;//in stock

    @Column(nullable = false)
    private int rent=0;//Rent (unit price per hour, 99/1)
    private int minHourToRent=0;//Minimum rental time
    private int maxHourToRent=0;//Maximum rental time
    private int rentCapacity=0;//Rental capacity
    private int rentUsedCapacity=0;//Rental capacity
    private int rentIncome=0;//Warehouse revenue
    private boolean enableRent=false;//Whether to rent
    private static final long DAY_MILLISECOND = 1000 * 3600 * 24;

    @OneToMany(mappedBy = "wareHouse",cascade ={CascadeType.ALL},fetch = FetchType.EAGER)//Let tenants maintain relationships
    protected Set<WareHouseRenter> renters = new HashSet<>();//Tenant information

    //Initialize prototype, building coordinates, building owner id
    public WareHouse(MetaWarehouse meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
        //eva bonus calculation
        Eva eva=EvaManager.getInstance().getEva(ownerId(), MetaBuilding.WAREHOUSE, Gs.Eva.Btype.WarehouseUpgrade_VALUE);
        int storeCapacity = (int) (this.metaWarehouse.storeCapacity * (1 + EvaManager.getInstance().computePercent(eva)));
        this.metaWarehouse = meta;
        this.shelf = new Shelf(meta.shelfCapacity);
        this.store = new Storage(storeCapacity);
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
    public int quality() {//Building quality (not for now)
        return 0;
    }

    @Override
    public Gs.WareHouse detailProto()//Added eva attribute
    {
        Eva eva=EvaManager.getInstance().getEva(ownerId(), MetaBuilding.WAREHOUSE, Gs.Eva.Btype.WarehouseUpgrade_VALUE);
        int storeCapacity = (int) (this.metaWarehouse.storeCapacity * (1 + EvaManager.getInstance().computePercent(eva)));
        //Detailed prototype)
        Gs.WareHouse.Builder builder = Gs.WareHouse.newBuilder();
        builder.setInfo(super.toProto());//Building information initialization
        builder.setStore(this.store.toProto());//warehouse
        builder.setShelf(this.shelf.toProto());//Listed goods
        builder.setMaxHourToRent(this.maxHourToRent);//Maximum rental hours (interval)
        builder.setMinHourToRent(this.minHourToRent);
        builder.setAvailableCapacity(this.store.availableSize());//Available capacity
        builder.setStoreCapacity(storeCapacity);//Warehouse capacity
        builder.setRentCapacity(this.rentCapacity);//Rental capacity
        builder.setRent(this.rent);//rent
        builder.setRentUsedCapacity(this.rentUsedCapacity);
        //Encapsulate the information of the tenants of the distribution center
        if(renters!=null&&renters.size()>0){
            renters.forEach(p->builder.addRenter(p.toProto()));
        }
        return builder.build();
    }

    @PostLoad
    protected void _1() {
        this.metaWarehouse = (MetaWarehouse) super.metaBuilding;
        Eva eva=EvaManager.getInstance().getEva(ownerId(), MetaBuilding.WAREHOUSE, Gs.Eva.Btype.WarehouseUpgrade_VALUE);
        int storeCapacity = (int) (this.metaWarehouse.storeCapacity * (1 + EvaManager.getInstance().computePercent(eva)));
        this.shelf.setCapacity(this.metaWarehouse.shelfCapacity);
        this.store.setCapacity(storeCapacity);
    }

    @Override
    public void appendDetailProto(Gs.BuildingSet.Builder builder) {//Add a prototype, that is to add a building
        builder.addWareHouse(this.detailProto());
    }

    @Override
    protected void enterImpl(Npc npc) { //Enter the building
    }

    @Override
    protected void leaveImpl(Npc npc) {//go away

    }

    @Override
    protected void _update(long diffNano) {

    }

    @Override
    public boolean addshelf(Item mi, int price, boolean autoReplenish) { //Put on shelf
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
    public boolean delshelf(ItemKey id, int n, boolean unLock) {//Off shelf
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
    public Shelf.Content getContent(ItemKey id) {//Get shelf catalog
        return this.shelf.getContent(id);
    }

    @Override
    public boolean setPrice(ItemKey id, int price) {//Set price
        Shelf.Content i = this.shelf.getContent(id);
        if(i == null)
            return false;
        i.price = price;
        return true;
    }

    @Override
    public boolean setAutoReplenish(ItemKey id, boolean autoRepOn) {//Set whether the goods are automatically replenished and transfer the replenishment
        Shelf.Content i = this.shelf.getContent(id);
        if(i == null)
            return false;
        this.shelf.add(new Item(id,0),i.price,autoRepOn);
        return  true;
    }

    @Override
    public int getSaleCount(int itemId) {//Get the sales quantity, you need to give me the id of the goods and check it out
        return this.shelf.getSaleNum(itemId);
    }

    @Override
    public Map<Item, Integer> getSaleDetail(int itemId) {//Get the sales list, which is the detailed information of what I sold, how many I bought
        return this.shelf.getSaleDetail(itemId);
    }

    @Override
    public boolean reserve(MetaItem m, int n) { //To reserve, you give me a cargo option and quantity (if I can’t store it anymore, return false, otherwise, store it in the original position)
        return store.reserve(m, n);
    }

    @Override
    public boolean lock(ItemKey m, int n) { //Lock the goods (the goods you want to transport must be locked)
        return store.lock(m, n);
    }

    @Override
    public boolean unLock(ItemKey m, int n) {//Unlock cargo
        return store.unLock(m, n);
    }

    @Override
    public Storage.AvgPrice consumeLock(ItemKey m, int n) {//Use locked items
        return store.consumeLock(m, n);
    }

    @Override
    public void consumeReserve(ItemKey m, int n, int price) {//Use stored goods
        store.consumeReserve(m, n, price);
    }

    @Override
    public void markOrder(UUID orderId) {//Purchase order
        store.markOrder(orderId);
    }

    @Override
    public void clearOrder(UUID orderId) {//Clear order
        store.clearOrder(orderId);
    }

    @Override
    public boolean delItem(ItemKey mi) {//Delete (destroy) the goods
        return this.store.delItem(mi);
    }

    @Override
    public int availableQuantity(MetaItem m) {//The number of vacancies, that is, the number of vacancies in my warehouse
        return this.store.availableQuantity(m);
    }

    @Override
    public boolean has(ItemKey m, int n) {//Is there my designated goods
        return this.store.has(m,n);
    }

    @Override
    public boolean offset(ItemKey item, int n) {//offset
        return this.store.offset(item,n);
    }

    @Override
    public boolean offset(MetaItem item, int n, UUID pid, int typeId) {
        return this.store.offset(item,n,pid,typeId);
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

    //Delete tenant
    public void removeRenter(WareHouseRenter renter){
        renters.remove(renter);
    }
    //Add tenant
    public void addRenter(WareHouseRenter renter){
        renters.add(renter);
    }
    @Override
    public boolean delItem(Item item) {
        return this.store.delItem(item);
    }
    //Delete tenants at expiration
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
        this.store.setOtherUseSize(this.rentUsedCapacity);//Set the other used capacity to the currently rented capacity
    }
    public void updateOtherSize(){
        this.store.setOtherUseSize(this.rentUsedCapacity);
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
