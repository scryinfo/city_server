package Game;

import Shared.Util;
import gs.Gs;

import javax.persistence.*;
import java.util.*;

public class Shelf {
    private int capacity;

    public Shelf(int shelfCapacity) {
        this.capacity = shelfCapacity;
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
    @Embeddable
    public class ItemInfo {
        UUID id = UUID.randomUUID();
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

        private ItemInfo() {
        }
    }

    @ElementCollection
    @MapKey(name = "id")
    private Map<UUID, ItemInfo> slots = new TreeMap<>();

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
