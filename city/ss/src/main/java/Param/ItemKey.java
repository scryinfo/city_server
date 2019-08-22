package Param;

import java.util.Objects;
import java.util.UUID;

public class ItemKey {
    int itemId;
    UUID producerId;

    public ItemKey(int itemId, UUID producerId) {
        this.itemId = itemId;
        this.producerId = producerId;
    }

    public ItemKey() { }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemKey itemKey = (ItemKey) o;
        return this.producerId.equals(itemKey.producerId)
                &&this.itemId==itemKey.itemId;

    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId,producerId);
    }
}
