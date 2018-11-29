package Game;

import gs.Gs;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@Entity
public class Shelf {
    @Id
    private final UUID id = UUID.randomUUID();
    @Transient
    private int capacity;

    public Shelf(int shelfCapacity) {
        this.capacity = shelfCapacity;
    }

    protected Shelf() {}

    void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int size() {
        return slots.size();
    }
    Content getContent(ItemKey id) {
        return slots.get(id);
    }
    public final static class SellInfo {
        public UUID producerId;
        public int qty;
        public int price;
        public MetaItem meta;
    }
    public List<SellInfo> getSellInfo(int metaId) {
        return slots.entrySet().stream().filter(e->e.getKey().meta.id==metaId).map(e->{
            SellInfo s = new SellInfo();
            s.producerId = e.getKey().producerId;
            s.qty = e.getKey().qty;
            s.price = e.getValue().price;
            s.meta = e.getKey().meta;
            return s;
        }).collect(Collectors.toList());
    }
    public Collection<Integer> getMetaIds(MetaGood.Type type, int lux) {
        return this.slots.entrySet().stream().filter(e-> {
            if(e.getKey().meta instanceof MetaGood) {
                MetaGood mg = (MetaGood)e.getKey().meta;
                return mg.lux == lux && MetaGood.goodType(mg.id) == type;
            }
            return false;
        }).map(e->e.getKey().meta.id).collect(Collectors.toList());
        // .mapToInt(e->e.getKey().meta.id).boxed().collect(Collectors.toList());
        // .mapToInt(e->e.getKey().meta.id).toArray();
        // .mapToInt(e->e.getKey().meta.id).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        // this is most stupid api design what I have ever seen
    }

    @Embeddable
    public static final class Content {
        public Content(int n, int price) {
            this.n = n;
            this.price = price;
        }

        int n;
        int price;

        protected Content() {}
    }

    @ElementCollection(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    private Map<ItemKey, Content> slots = new HashMap<>();

    public Gs.Shelf.Content add(Item item, int price) {
        if(full())
            return null;
        Content content = slots.get(item.key);
        if(content != null) {
            if(content.price != price)
                return null;
            content.n += item.n;
        }
        else {
            content = new Content(item.n, price);
            slots.put(item.key, content);
        }
        return toProto(item.key, content);
    }
    private Gs.Shelf.Content toProto(ItemKey k, Content content) {
        return Gs.Shelf.Content.newBuilder()
                .setK(k.toProto())
                .setN(content.n)
                .setPrice(content.price)
                .build();
    }
    public boolean del(ItemKey k, int n) {
        Shelf.Content i = this.getContent(k);
        if(i == null || i.n < n)
            return false;
        i.n -= n;
        if(i.n == 0)
            slots.remove(k);
        return true;
    }
    public boolean full() {
        return slots.size() >= capacity;
    }
    public boolean has(int mId) {
        return this.slots.keySet().stream().anyMatch(k->k.meta.id == mId);
    }
    public Gs.Shelf toProto() {
        Gs.Shelf.Builder builder = Gs.Shelf.newBuilder();
        slots.forEach((k,v)->builder.addGood(toProto(k, v)));
        return builder.build();
    }
}
