package Game;

import Shared.Util;
import gs.Gs;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
    ItemInfo getContent(UUID id) {
        return slots.get(id);
    }
    @Entity
    public static final class ItemInfo {
        @Id
        final UUID id = UUID.randomUUID();
        @Column(name = "itemId")
        @Convert(converter = MetaItem.Converter.class)
        MetaItem item;
        int n;
        int price;
        public ItemInfo(MetaItem item, int n, int price) {
            this.item = item;
            this.n = n;
            this.price = price;
        }

        protected ItemInfo() {
        }
    }

    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKey(name = "id")
    private Map<UUID, ItemInfo> slots = new HashMap<>();

    public UUID add(MetaItem item, int n, int price) {
        if(full())
            return null;
        ItemInfo i = new ItemInfo(item, n, price);
        slots.put(i.id, i);
        return i.id;
    }
    public boolean del(UUID id) {
        if(!slots.containsKey(id))
            return false;
        slots.remove(id);
        return true;
    }
    public boolean full() {
        return slots.size() >= capacity;
    }
    public Gs.Shelf toProto() {
        Gs.Shelf.Builder builder = Gs.Shelf.newBuilder();
        slots.forEach((k,v)->builder.addGood(Gs.Shelf.Content.newBuilder()
                .setId(Util.toByteString(k))
                .setItemId(v.item.id)
                .setNum(v.n)));
        return builder.build();
    }
}
