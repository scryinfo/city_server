package Game;

import gs.Gs;
import org.hibernate.annotations.Cascade;

import javax.persistence.ElementCollection;
import javax.persistence.Embeddable;
import javax.persistence.FetchType;
import javax.persistence.Transient;
import java.util.HashMap;
import java.util.Map;

@Embeddable
public class Shelf {
    @Transient
    private int capacity;

    public Shelf(int shelfCapacity) {
        this.capacity = shelfCapacity;
    }

    public Shelf() {
    }

    void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int size() {
        return slots.size();
    }
    Content getContent(ItemKey id) {
        return slots.get(id);
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
        return true;
    }
    public boolean full() {
        return slots.size() >= capacity;
    }
    public Gs.Shelf toProto() {
        Gs.Shelf.Builder builder = Gs.Shelf.newBuilder();
        slots.forEach((k,v)->builder.addGood(toProto(k, v)));
        return builder.build();
    }
}
