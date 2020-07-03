package Game;

import Shared.Package;
import Shared.Util;
import gs.Gs;
import gscode.GsCode;
import org.hibernate.annotations.Cascade;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

@Entity(name = "PromotionMgr")
public class PromotionMgr {
    public static final int ID = 0;
    PromotionMgr(){
        int t = 0 ;
    }
    @Id
    private Integer id = ID;

    @OneToMany(mappedBy="promoMgr",fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    private Map<UUID, PromoOrder> promotions = new HashMap<>();

    private static long _elapsedtime = 0 ;    //Last update time
    public static final long _upDeltaNs = TimeUnit.SECONDS.toNanos(10);    //Update interval, the unit is nanoseconds, 3600 is an hour
    //public static final long _upDeltaNs = 10*1000000000;        //Update interval, the unit is nanoseconds, 3600 is an hour Ns
    public static final long _upDeltaMs = _upDeltaNs/1000000;   //Update interval, ms
    private static PromotionMgr instance ;
    public static void init() {
        GameDb.initPromotionMgr();
        instance = GameDb.getPromotionMgr();
    }

    public static PromotionMgr instance() {
        return instance;
    }

    public void getPromotins(List<UUID> ids, List<PromoOrder> proms){
        for (int i = 0; i < ids.size(); i++) {
            proms.add(promotions.get(ids.get(i)));
        }
    }

    public List<PromoOdTs> AdRemovePromoOrder(UUID id, List<UUID> promotionIds, boolean delOrder){
        long nextTs = 0;
        int findPos = -1 ;
        List<PromoOdTs> changed = new ArrayList<>();
        /*
        ts calculation formula: next promoted promStartTs = last promoted promStartTs + last promoted promDuration
         In a loop, you need to set the promDuration of the promotion to be deleted to 0; if the promotion to be deleted is in the first position, then promStartTs is set to 0,
         Subsequent promotion and implementation of the above "ts calculation formula" can be updated correctly.
        */
        for (int i = 0; i < promotionIds.size(); i++) {
            UUID pid = promotionIds.get(i);
            PromoOrder promo = promotions.get(pid);
            if(promo == null){
                continue;
            }
            if(id.equals(promotionIds.get(i))){
                if(i == 0){
                    promo.promStartTs = System.currentTimeMillis();
                }
                promo.promDuration= 0 ;
                findPos = i;
            }else if(findPos >= 0){
                promo.promStartTs = nextTs;
                changed.add(new PromoOdTs(pid,promo.promStartTs));
            }
            nextTs = promo.promStartTs + promo.promDuration;
        }
        //After the update is complete, remove the promotion you want to delete.
        if(delOrder){
            promotions.remove(id);
        }
        return changed;
    }
    public PromoOrder getPromotion(UUID id){
        return  promotions.get(id);
    }

    public void AdAddNewPromoOrder(PromoOrder neworder){
        neworder.setPromoMgr(this);
        promotions.putIfAbsent(neworder.promotionId,neworder);
    }
    public void update(long diffNano) {
        if(_elapsedtime < _upDeltaNs){
            _elapsedtime += diffNano;
            return;
        }else{
            _elapsedtime = 0;
        }
			/*
			* Calculated every hour, the advertiser’s brand value is accumulated based on the current advertising company’s promotion capabilities
				* Basic promotion power = Wage distribution ratio * Number of NPCs in construction * One worker’s increased visibility in 1 hour
				* Single promotion ability = basic promotion ability * (1 +% single eva ability promotion) * (1+% flow promotion)
				* 1 worker can increase visibility in 1 hour (new field added to PublicFacility)
			*Rely on data analysis
				* data
					* Wage distribution ratio, number of construction NPCs
					* 1 worker can increase visibility in 1 hour
					* Single eva capacity improvement
					* Increased traffic
				* some
					* Wage distribution ratio, number of construction NPCs
					* 1 worker can increase visibility in 1 hour
				* Need to determine whether there are
					* Single eva capacity improvement
						Game.Contract.ContractManager#getPlayerADLift
					* Increased traffic
						@Entity(name = "Eva")
						@Table(name = "Eva")
						public class Eva {
						queryMyEva
			* Behavior analysis
				* Update the database directly without notifying the client
			*/
        List<UUID> idToRemove = new ArrayList<>();
        Set<Entry<UUID, PromoOrder>> setOfEntries = promotions.entrySet();
        Iterator<Entry<UUID, PromoOrder>> iterator = setOfEntries.iterator();
        // iterate over map
        while (iterator.hasNext()) {
            Entry<UUID, PromoOrder> entry = iterator.next();
            PromoOrder promotion = (PromoOrder)entry.getValue();
            long curtime = System.currentTimeMillis();
            if(promotion.promStartTs > curtime){ //It's not time to start the study
                continue;
            }
            Building sellerBuilding = City.instance().getBuilding(promotion.sellerBuildingId);
            PublicFacility fcySeller = (PublicFacility) sellerBuilding ;
            int objType = promotion.buildingType > 0 ? promotion.buildingType: promotion.productionType;//Specific commodity or building awareness types
            int promType=promotion.buildingType > 0 ? promotion.buildingType: promotion.productionType/1000;//Large type of promotion ability
            long endTime = promotion.promStartTs + promotion.promDuration;
            long elapsedtime = promotion.promDuration - (endTime - curtime);//Elapsed time  10:00  12:00   2
            float addition = 0;
            long promHour = TimeUnit.MILLISECONDS.toHours(promotion.promDuration);
            if(endTime >= System.currentTimeMillis()){ //Here, even if the conditions are met, it cannot be promoted immediately, and it is necessary to judge whether 1 hour has passed.
                long now = System.currentTimeMillis();
                //long passTime = now - promotion.promStartTs;//This is the remaining time
                long passTime=TimeUnit.MILLISECONDS.toHours(elapsedtime);
                if(passTime-promotion.promoNum==1) {  //Elapsed time-number of promotions (this is equivalent to performing promotion every 1 hour)
                    System.err.println("开始推广");
                    //Calculate the result of each promotion
                    //addition = fcySeller.excutePromotion(promotion);
                    addition = fcySeller.getLocalPromoAbility(promType);
                    //Accumulate boost values to calculate average
                    promotion.promotedTotal += addition;
                    promotion.promProgress = (int) (((float) elapsedtime / (float) promotion.promDuration) * 100);
                    BrandManager.instance().update(promotion.buyerId, objType, (int) addition);
                    promotion.promoNum++;
                }
            }else {
                //Determine whether the promotion bonus is correct after the promotion is completed
                if(promotion.promoNum<promHour){//Dealing with the problem of delay
                    addition= fcySeller.getLocalPromoAbility(promType);
                    float residueProValue= (promHour - promotion.promoNum) * addition;
                    promotion.promotedTotal += residueProValue;
                    BrandManager.instance().update(promotion.buyerId, objType, (int)residueProValue);
                }
                //If the time is exceeded, remove it and notify the player
                Player buyer = GameDb.getPlayer(promotion.buyerId);
                //Update the ad cache in buyer player information
                buyer.delpayedPromotion(promotion.promotionId);
                GameDb.saveOrUpdate(buyer);
                //Update advertiser ad list
                fcySeller.delSelledPromotion(promotion.promotionId, false);
                GameDb.saveOrUpdate(fcySeller);
                idToRemove.add(entry.getKey());
                //Promotion completion notice
                //paras: The first is the advertiser building id, the second is the promotion type, the third bonus value, and the fourth is the promotion duration
                MailBox.instance().sendMail(Mail.MailType.PROMOTE_FINISH.getMailType(), promotion.buyerId, null, new UUID[]{promotion.sellerBuildingId}, new int[]{objType,(int) addition, (int) promHour});
                //Send to advertiser
                GameServer.sendTo(new ArrayList<UUID>(Arrays.asList(promotion.sellerId)) ,
                        Package.create( GsCode.OpCode.adRemovePromoOrder_VALUE,
                                Gs.AdRemovePromoOrder.newBuilder().setPromotionId(Util.toByteString(promotion.promotionId))
                                        .setBuildingId(Util.toByteString(promotion.sellerBuildingId))
                                        .build()));
            }
        }
        if(idToRemove.size() > 0){
            for (int i = 0; i < idToRemove.size(); i++) {
                GameDb.delete(promotions.remove(idToRemove.get(i)));
            }
            GameDb.saveOrUpdate(this);
        }
    }

}
