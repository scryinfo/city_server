package Game;

import Game.Meta.MetaData;
import Game.Meta.MetaGood;
import Game.Meta.MetaItem;
import Game.Meta.MetaMaterial;
import Shared.Util;
import gs.Gs;

import javax.persistence.Convert;
import javax.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import Game.BrandManager;

@Embeddable
public class ItemKey implements Serializable {
    public ItemKey(MetaItem meta, UUID producerId, int qty, UUID playerId,int mid) {
        this.meta = meta;
        this.producerId = producerId;
        this.qty = qty;
        //this.brandId = new BrandManager.BrandKey(playerId,mid);
        this.playerId = playerId;
        this.mid = mid;
    }
    public ItemKey(Gs.ItemKey item) throws Exception {
        MetaItem mi = MetaData.getItem(item.getId());
        if(mi == null)
            throw new Exception();
        if(item.hasProducerId()) {
            if(mi instanceof MetaMaterial)
                throw new Exception();
            if(item.getQty() < 0)
                throw new Exception();
            this.producerId = Util.toUuid(item.getProducerId().toByteArray());
            this.qty = item.getQty();
        }
        else {
            if (mi instanceof MetaGood)
                throw new Exception();
        }
        this.meta = mi;
    }
    protected ItemKey() {}

    public ItemKey(MetaItem mi, UUID playerId,int mid) {
        this.meta = mi;
        //this.brandId = new BrandManager.BrandKey(playerId,mid);
        this.playerId = playerId;
        this.mid = mid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemKey itemKey = (ItemKey) o;
        return qty == itemKey.qty &&
                Objects.equals(meta, itemKey.meta) &&
                Objects.equals(producerId, itemKey.producerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(meta, producerId, qty);
    }

    @Convert(converter = MetaItem.Converter.class)
    public MetaItem meta;
    // SQL's composite key can not have NULL column, so give those 2 field a default value
    public UUID producerId = NULL_PRODUCER_ID;
    public int qty = 0;
    //品牌id
    //BrandManager.BrandKey brandId;
    public UUID playerId;
    public int mid;

    public static final UUID NULL_PRODUCER_ID = UUID.nameUUIDFromBytes(new byte[16]);
    public Gs.ItemKey toProto() {
        Gs.ItemKey.Builder builder = Gs.ItemKey.newBuilder();
        builder.setId(meta.id);
        if(!producerId.equals(NULL_PRODUCER_ID)) {
            builder.setProducerId(Util.toByteString(producerId));
            builder.setQty(qty);
        }
        return builder.build();
    }
}
