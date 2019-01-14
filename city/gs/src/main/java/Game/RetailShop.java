package Game;

import com.google.protobuf.Message;
import gs.Gs;

import javax.persistence.*;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Entity
public class RetailShop extends PublicFacility implements IShelf, IStorage {
    public RetailShop(MetaRetailShop meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
        this.store = new Storage(meta.storeCapacity);
        this.shelf = new Shelf(meta.shelfCapacity);
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
        builder.setQty(qty);
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
    public Gs.Shelf.Content addshelf(Item mi, int price) {
        if(!this.store.has(mi.key, mi.n))
            return null;
        Gs.Shelf.Content res = this.shelf.add(mi, price);
        if(res != null)
            this.store.lock(mi.key, mi.n);
        return res;
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
    public boolean offset(MetaItem item, int n) { return this.store.offset(item, n); }

    public Collection<Integer> getMetaIdsInShelf(MetaGood.Type type, int lux) {
        return shelf.getMetaIds(type, lux);
    }
    public boolean shelfHas(int metaId) {
        return shelf.has(metaId);
    }

    public List<Shelf.SellInfo> getSellInfo(int metaId) {
        return shelf.getSellInfo(metaId);
    }
}
