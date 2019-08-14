package Game;

import Game.Eva.Eva;
import Game.Eva.EvaManager;
import Game.Meta.MetaData;
import Game.Meta.MetaGood;
import Game.Meta.MetaItem;
import Game.Meta.MetaMaterial;
import Game.Util.GlobalUtil;
import Shared.Util;
import gs.Gs;

import javax.persistence.Convert;
import javax.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ItemKey implements Serializable {
    public ItemKey(MetaItem meta, UUID producerId, int qty, UUID playerId) {
        this.meta = meta;
        this.producerId = producerId;
        this.qty = qty;
    }
    public ItemKey(Gs.ItemKey item) throws Exception {
        MetaItem mi = MetaData.getItem(item.getId());
        if(mi == null)
            throw new Exception();
        if(item.hasProducerId()) {
            if(mi instanceof MetaMaterial)
                throw new Exception();
            if(item.getQty()< 0)
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

    public ItemKey(MetaItem mi, UUID playerId) {
        this.meta = mi;
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

    public static final UUID NULL_PRODUCER_ID = UUID.nameUUIDFromBytes(new byte[16]);
    public Gs.ItemKey toProto() {
        Gs.ItemKey.Builder builder = Gs.ItemKey.newBuilder();
        builder.setId(meta.id);
        if(!producerId.equals(NULL_PRODUCER_ID)) {
            builder.setProducerId(Util.toByteString(producerId));

            Eva brandEva=EvaManager.getInstance().getEva(producerId, meta.id,Gs.Eva.Btype.Brand_VALUE);
            Eva qualityEva=EvaManager.getInstance().getEva(producerId, meta.id,Gs.Eva.Btype.Quality_VALUE);
            MetaGood goods=MetaData.getGood(meta.id);
            double b=EvaManager.getInstance().computePercent(brandEva);
            double q=EvaManager.getInstance().computePercent(qualityEva);
            double totalBrand=goods.brand*(1+b);
            double totalQuality=goods.quality*(1+q);
            //品牌评分
            double brandScore = GlobalUtil.getBrandScore(totalBrand, meta.id);
            //品质评分
            double goodQtyScore = GlobalUtil.getGoodQtyScore(totalQuality, meta.id, goods.quality);
            builder.setBrandScore((int) brandScore);
            builder.setQualityScore((int) goodQtyScore);

            BrandManager.BrandInfo info = BrandManager.instance().getBrand(producerId, meta.id);
            if (info.hasBrandName()) {
                builder.setBrandName(info.getBrandName());
            } else{
                builder.setBrandName(GameDb.getPlayer(producerId).getCompanyName());
            }
        }
        return builder.build();
    }

    //获取商品的总品质
    public double getTotalQty(){
        if(MetaGood.isItem(this.meta.id)){
            Eva eva = EvaManager.getInstance().getEva(this.producerId, meta.id,Gs.Eva.Btype.Quality_VALUE);
            MetaGood good = MetaData.getGood(meta.id);
            return good.quality * (1 + EvaManager.getInstance().computePercent(eva));
        }
        return 0;
    }
    //获取商品的总知名度
    public double getTotalBrand(){
        if(MetaGood.isItem(this.meta.id)){
            MetaGood good = MetaData.getGood(meta.id);
            /*获取Eva加成信息*/
            Eva eva = EvaManager.getInstance().getEva(this.producerId, this.meta.id, Gs.Eva.Btype.Brand_VALUE);
            return good.brand*(1 + EvaManager.getInstance().computePercent(eva));
        }
        return 0;
    }
}
