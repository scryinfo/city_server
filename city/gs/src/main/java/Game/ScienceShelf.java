package Game;

import gs.Gs;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.SelectBeforeUpdate;

import javax.persistence.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/*Research sale (equivalent to previous shelves*/
@Entity
@SelectBeforeUpdate(false)
public class ScienceShelf{
    @Id
    @GeneratedValue
    private UUID id;

    @ElementCollection(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    private Map<ItemKey, ScienceShelf.Content> slots = new HashMap<>();//SINOTECH

    public void updateAutoReplenish(ScienceBuildingBase scienceBuildingBase, ItemKey key) {
        //Update shelf: Perform a shelf operation
        Content content = this.getContent(key);
        if(content!=null&&content.autoReplenish){
            scienceBuildingBase.delshelf(key,content.n, true);
            Item itemInStore = new Item(key, scienceBuildingBase.store.getItemCount(key));
            scienceBuildingBase.addshelf(itemInStore,content.price, content.autoReplenish);
        }
    }


    @Embeddable
    public static final class Content {                 //Save the properties of technology
        public Content(int n, int price,boolean autoReplenish) {
            this.n = n;
            this.price = price;
            this.autoReplenish = autoReplenish;
        }
        public int getCount(){return  n; }
        public int n;
        public int price;
        public boolean autoReplenish;
        protected Content() {}

        public int getN() {
            return n;
        }

        public int getPrice() {
            return price;
        }

        public boolean isAutoReplenish() {
            return autoReplenish;
        }
    }

    private Gs.ScienceShelf.Content toProto(ItemKey k, Content content,ScienceBuildingBase scienceBuildingBase) {
        return Gs.ScienceShelf.Content.newBuilder()
                .setK(k.toProto())
                .setN(content.n)
                .setPrice(content.price)
                .setStoreNum(scienceBuildingBase.getStore().getItemCount(k))
                .setAutoReplenish(content.autoReplenish)
                .build();
    }

    public Gs.ScienceShelf toProto(ScienceBuildingBase scienceBuildingBase){
        Gs.ScienceShelf.Builder builder = Gs.ScienceShelf.newBuilder();
        slots.forEach((k,v)->builder.addGood(toProto(k, v,scienceBuildingBase)));
        return builder.build();
    }

    public int getAllNum(){
        return this.slots.entrySet().stream().mapToInt(e -> e.getValue().n).sum();
    }

    public ScienceShelf.Content getContent(ItemKey itemKey){
        return this.slots.get(itemKey);
    }
    public boolean del(ItemKey k, int n) {
        ScienceShelf.Content i = this.getContent(k);
        if(i == null || i.n < n)
            return false;
        i.n -= n;
        if(i.n == 0 && i.autoReplenish == false)
            slots.remove(k);
        return true;
    }

    public boolean add(Item item, int price, boolean autoReplenish) {
        Content content = slots.get(item.getKey());
        if(content != null) {
            if(content.price != price)
                return false;
            content.n += item.getN();
            content.autoReplenish = autoReplenish;
        }
        else {
            content = new Content(item.getN(), price, autoReplenish);
            slots.put(item.getKey(), content);
        }
        return true;
    }

    public void cleanData(){
        this.slots.clear();
    }

    public int getSaleNum(int itemid) {
        int res = 0;
        for (Map.Entry<ItemKey, Content> entry : slots.entrySet()) {
            if (entry.getKey().meta.id == itemid) {
                res += entry.getValue().n;
            }
        }
        return res;
    }
    public Map<Item, Integer> getSaleDetail(int itemId) {
        Map<Item, Integer> res = new HashMap<>();
        this.slots.forEach((k,v)->{
            if(k.meta.id == itemId)
                res.put(new Item(k, v.n), v.price);
        });
        return res;
    }
    // Get all the quantities on the shelf
    public Integer getTotalContentNum(){
        int sum = this.slots.values().stream().mapToInt(c -> c.n).sum();
        return sum;
    }
}
