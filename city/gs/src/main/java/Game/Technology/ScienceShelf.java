package Game.Technology;

import Game.Item;
import Game.ItemKey;
import gs.Gs;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.SelectBeforeUpdate;

import javax.persistence.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/*研究出售（等同于以前的货架）*/
@Entity
@SelectBeforeUpdate(false)
public class ScienceShelf{
    @Id
    @GeneratedValue
    private UUID id;

    @ElementCollection(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    private Map<ItemKey, ScienceShelf.Content> slots = new HashMap<>();//出售中科技

    public void updateAutoReplenish(Technology technology, ItemKey key) {
        //更新货架： 执行一次下架上架操作
        Content content = this.getContent(key);
        if(content!= null){
            technology.delshelf(key,content.n, true);
        }
        Item itemInStore = new Item(key,technology.store.getItemCount(key));
        technology.addshelf(itemInStore,content.price, content.autoReplenish);
    }


    @Embeddable
    public static final class Content {                 //保存科技的属性
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

    private Gs.ScienceShelf.Content toProto(ItemKey k, Content content) {
        return Gs.ScienceShelf.Content.newBuilder()
                .setK(k.toProto())
                .setN(content.n)
                .setPrice(content.price)
                .setAutoReplenish(content.autoReplenish)
                .build();
    }

    public Gs.ScienceShelf toProto(){
        Gs.ScienceShelf.Builder builder = Gs.ScienceShelf.newBuilder();
        slots.forEach((k,v)->builder.addGood(toProto(k, v)));
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

}
