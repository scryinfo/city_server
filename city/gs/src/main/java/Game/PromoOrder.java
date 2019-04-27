package Game;

import Game.Meta.MetaItem;
import Game.Meta.MetaPublicFacility;
import Game.Timers.PeriodicTimer;
import Shared.Package;
import Shared.Util;
import com.google.protobuf.Message;
import gs.Gs;
import gscode.GsCode;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
@Embeddable
class PromoOdTs{
    PromoOdTs(UUID pid, long startTs){
        promotionId = pid;
        promStartTs = startTs;
    }
    Gs.PromoOdTs toProto(){
        return Gs.PromoOdTs.newBuilder().setPromotionId(Util.toByteString(promotionId)).setPromStartTs(promStartTs).build();
    }
    UUID promotionId;
    long promStartTs;	//推广开始时间
}

@Entity(name = "PromoOrder")
public class PromoOrder {
    PromoOrder(){}
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    UUID promotionId;	//这个id只在本建筑内起效
    UUID sellerId;		//广告商的 playerid
    UUID sellerBuildingId;//广告商的建筑id，同一个玩家可能有多个推广公司建筑
    UUID buyerId;		//广告主的 playerid
    int buildingType;	//建筑类型
    int productionType;	//产品id， 表字段定义.txt 道具类ID一共7位
    long promStartTs;	//推广开始时间
    long promDuration;	//推广时长
    int promProgress;	//推广进度
    int promotedTotal;  //总的推广力增加值，用来计算平均值
    int transactionPrice; //成交价， 成交时，广告商 PublicFacility 中 curPromPricePerHour


    public void setPromoMgr(PromotionMgr promoMgr) {
        this.promoMgr = promoMgr;
    }

    public PromotionMgr getPromoMgr() {
        return promoMgr;
    }
    @ManyToOne
    private PromotionMgr promoMgr;


    public void setTransactionPrice(int transactionPrice) {
        this.transactionPrice = transactionPrice;
    }

    public Gs.Promotion toProto()
    {
        Gs.Promotion.Builder builder = Gs.Promotion.newBuilder();
        builder.setPromotionId(Util.toByteString(promotionId))
                .setSellerId(Util.toByteString(sellerId))
                .setSellerBuildingId(Util.toByteString(sellerBuildingId))
                .setBuyerId(Util.toByteString(buyerId))
                .setBuildingType(buildingType)
                .setProductionType(productionType)
                .setPromStartTs(promStartTs)
                .setPromDuration(promDuration)
                .setTransactionPrice(transactionPrice)
                .setPromProgress(promProgress);
        return builder.build();
    }
}
