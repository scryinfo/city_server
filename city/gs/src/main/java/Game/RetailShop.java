package Game;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.PostLoad;
import javax.persistence.Transient;

import Game.Eva.Eva;
import Game.Eva.EvaManager;
import Game.Meta.*;
import com.google.protobuf.Message;

import Game.Contract.BuildingContract;
import Game.Contract.IBuildingContract;
import gs.Gs;

@Entity
public class RetailShop extends PublicFacility implements IShelf, IStorage,IBuildingContract
{
    public RetailShop(MetaRetailShop meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
        this.store = new Storage(meta.storeCapacity);
        this.shelf = new Shelf(meta.shelfCapacity);
        this.buildingContract = new BuildingContract(0, 0, false);
    }

    @Override
    public Map<Item, Integer> getSaleDetail(int itemId) {
        return this.shelf.getSaleDetail(itemId);
    }

    @Override
    public int getSaleCount(int itemId) {
        return this.shelf.getSaleNum(itemId);
    }

    @Transient
    private MetaRetailShop meta;

    @OneToOne(cascade= CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "store_id")
    private Storage store;

    @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "shelf_id")
    private Shelf shelf;

    protected RetailShop() {}

    @Override
    public int quality() {
        return this.qty;
    }

    @Column(nullable = false)
    @Embedded
    private BuildingContract buildingContract;

    @PostLoad
    protected void _1() {
        super._1();
        this.meta = (MetaRetailShop) super.metaBuilding;
        this.shelf.setCapacity(this.meta.shelfCapacity);
        this.store.setCapacity(this.meta.storeCapacity);
    }

    @Override
    public Message detailProto() {
        Gs.RetailShop.Builder builder = Gs.RetailShop.newBuilder();
        builder.setShelf(this.shelf.toProto());
        builder.setStore(this.store.toProto());
        builder.setInfo(this.toProto());
        builder.setAd(genAdPart());
        builder.setQty(this.quality());
        builder.setLift(getLift());
        builder.setContractInfo(this.buildingContract.toProto());
        return builder.build();
    }

    @Override
    public void appendDetailProto(Gs.BuildingSet.Builder builder) {
        builder.addRetailShop((Gs.RetailShop) this.detailProto());
    }

    @Override
    protected void enterImpl(Npc npc) {

    }

    @Override
    protected void leaveImpl(Npc npc) {

    }

    @Override
    protected void _update(long diffNano) {

    }

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
    public boolean setAutoReplenish(ItemKey id, boolean autoRepOn){
        Shelf.Content i = this.shelf.getContent(id);
        if(i == null)
            return false;
        this.shelf.add(new Item(id,0),i.price,autoRepOn);
        return  true;
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
    public boolean reserve(MetaItem m, int n) {
        return store.reserve(m, n);
    }

    @Override
    public boolean lock(ItemKey m, int n) {
        return store.lock(m, n);
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
    public boolean delItem(ItemKey k) { return this.store.delItem(k); }

    @Override
    public int availableQuantity(MetaItem m) { return this.store.availableQuantity(m); }

    @Override
    public boolean has(ItemKey m, int n) { return this.store.has(m, n); }

    @Override
    public boolean offset(ItemKey item, int n) { return this.store.offset(item, n); }

    @Override
    public boolean offset(MetaItem item, int n, UUID pid, int typeId) { return this.store.offset(item, n,pid,typeId); }

    @Override
    public boolean delItem(Item item) {
        return store.delItem(item);
    }

    public Collection<Integer> getMetaIdsInShelf(MetaGood.Type type, int lux) {
        return shelf.getMetaIds(type, lux);
    }
    
    public boolean shelfHas(int metaId) {
        return shelf.has(metaId);
    }

    public List<Shelf.SellInfo> getSellInfo(int metaId) {
        return shelf.getSellInfo(metaId);
    }

    @Override
    public BuildingContract getBuildingContract()
    {
        return buildingContract;
    }


    @Override
    public boolean shelfSet(Item item, int price,boolean autoRepOn) {
        Shelf.Content content = this.shelf.getContent(item.key);
        //The number of warehouses needs to be changed
        if(content == null)
            return false;
        if(!autoRepOn) {//Non-automatic replenishment
            int updateNum = content.n - item.n;//Increase or decrease: current number of shelves-current number of shelves
            if(content.n==0&&item.n==0){//If it is not automatic replenishment, and the number of shelves is 0, delete it directly
                content.autoReplenish=autoRepOn;
                IShelf shelf=this;
                shelf.delshelf(item.key, content.n, true);
                return true;
            }
            boolean lock = false;
            if (updateNum < 0) {
                lock = this.store.lock(item.key, Math.abs(updateNum));
            } else {
                lock = this.store.unLock(item.key, updateNum);
            }
            if (lock) {
                content.price = price;
                content.n = item.n;
                content.autoReplenish = autoRepOn;
                return true;
            } else {
                return false;
            }
        }else {
            //1.Determine whether the capacity is full
            if(this.shelf.full())
                return false;
            //2.Set price
            content.price = price;
            IShelf shelf=this;
            //delete
            shelf.delshelf(item.key, content.n, true);
            //3.Modify the quantity on the shelf
            Item itemInStore = new Item(item.key,this.store.getItemCount(item.key));
            //Relist
            shelf.addshelf(itemInStore,price,autoRepOn);
            return true;
        }
    }

    public Storage getStore() {
        return store;
    }

    public Shelf getShelf() {
        return shelf;
    }

    public double getTotalQty(){
        Eva eva = EvaManager.getInstance().getEva(ownerId(), type(), Gs.Eva.Btype.Quality_VALUE);
        double qty = meta.qty * (1 + EvaManager.getInstance().computePercent(eva));
        return qty;
    }
    //Get total visibility
    public double getTotalBrand(){
        Eva eva = EvaManager.getInstance().getEva(ownerId(), type(), Gs.Eva.Btype.Brand_VALUE);
        return BrandManager.BASE_BRAND *(1 + EvaManager.getInstance().computePercent(eva));
    }

    public void cleanData(){
        this.store.clearData();
        this.shelf.clearData();
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
