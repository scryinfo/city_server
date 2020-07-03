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
    long promStartTs;	//Promotion start time
}

@Entity(name = "PromoOrder")
public class PromoOrder {
    PromoOrder(){}
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    UUID promotionId;	//This id only works in this building
    UUID sellerId;		//Advertiser playerid
    UUID sellerBuildingId;//Advertiser's building id, the same player may have multiple promotion company buildings
    UUID buyerId;		//Advertiser playerid
    int buildingType;	//Building type xxx
    int productionType;	//Product id, table field definition. txt Prop class ID total 7 digits
    long promStartTs;	//Promotion start time
    long promDuration;	//Promotion time
    int promProgress;	//Promotion progress
    int promotedTotal;  //The added value of the total promotion power, used to calculate the average
    int transactionPrice; //The transaction price, at the time of the transaction, in the advertiser's PublicFacility curPromPricePerHour
    @Transient
    int promoNum=0;

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
