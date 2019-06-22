package Game;

import Game.Eva.Eva;
import Game.Eva.EvaManager;
import Game.Meta.MetaData;
import Game.Meta.MetaGood;
import Game.Meta.MetaItem;
import gs.Gs;

import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import java.util.Objects;

@Embeddable
public class Item {
    @EmbeddedId
    ItemKey key;
    int n;
    public Item(ItemKey key, int n) {
        this.key = key;
        this.n = n;
    }

    protected Item() {}

    public Item(Gs.Item item) throws Exception {
        this.key = new ItemKey(item.getKey());
        this.n = item.getN();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Item item = (Item) o;
        return Objects.equals(key, item.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    Gs.Item toProto() {
        Gs.Item.Builder builder = Gs.Item.newBuilder();
        builder.setKey(key.toProto());
        builder.setN(n);
        return builder.build();
    }
}
