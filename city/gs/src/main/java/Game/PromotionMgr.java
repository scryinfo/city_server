package Game;

import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.*;
import java.util.Map.Entry;

@Entity(name = "PromotionMgr")
public class PromotionMgr {
    public static final int ID = 0;
    PromotionMgr(){
        int t = 0 ;
    }
    @Id
    private Integer id ;

    @OneToMany(mappedBy="promoMgr",fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    private Map<UUID, PromoOrder> promotions = new HashMap<>();

    private static long _elapsedtime = 0 ;    //上次更新时间
    //public static final long _upDelta = 3600*1000000000;   //更新间隔，单位是纳秒, 3600为一个小时
    public static final long _upDelta = 10*1000000000;   //更新间隔，单位是纳秒, 3600为一个小时

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

    public PromoOrder AdRemovePromoOrder(UUID id, List<UUID> promotionIds){
        long nextTs = 0;
        int findPos = -1 ;
        /*
        ts计算公式： 下一个推广的promStartTs = 上一个推广的promStartTs + 上一个推广的 promDuration
        在一个循环中，需要把要删除的推广的promDuration设置为0；如果要删除的推广在第一个位置，那么promStartTs设置为0，
        其后的推广执行上述“ts计算公式”就都能正确更新。
        */
        for (int i = 0; i < promotionIds.size(); i++) {
            PromoOrder promo = promotions.get(promotionIds.get(i));
            if(promo == null){
                continue;
            }
            if(id.equals(promotionIds.get(i))){
                if(i == 0){
                    promo.promStartTs = 0 ;
                }
                promo.promDuration= 0 ;
                findPos = i;
            }else if(findPos >= 0){
                promo.promStartTs = nextTs;
            }
            nextTs = promo.promStartTs + promo.promDuration;
        }
        //更新完之后，移除掉要删除的推广。
        return promotions.remove(id);
    }
    public PromoOrder getPromotion(UUID id){
        return  promotions.get(id);
    }

    public void AdAddNewPromoOrder(PromoOrder neworder){
        neworder.setPromoMgr(this);
        promotions.putIfAbsent(neworder.promotionId,neworder);
    }
    public void update(long diffNano) {

        if(_elapsedtime < _upDelta){
            _elapsedtime += diffNano;
            return;
        }else{
            _elapsedtime = 0;
        }
			/*
			* 每小时计算一次，广告主品牌值根据当前广告公司推广能力进行累计
				* 基础推广力 = 发放工资比例 *建筑NPC数量 * 1个工人1小时能增加的知名度
				* 单项推广能力 = 基础推广力 * （1 + %单项eva能力提升） *（1+%流量提升）
				* 1个工人1小时能增加的知名度（新增字段到PublicFacility）
			* 依赖数据分析
				* 数据
					* 工资发放比例、建筑NPC数量
					* 1个工人1小时能增加的知名度
					* 单项eva能力提升
					* 流量提升
				* 有的
					* 工资发放比例、建筑NPC数量
					* 1个工人1小时能增加的知名度
				* 需确定是否有的
					* 单项eva能力提升
						Game.Contract.ContractManager#getPlayerADLift
					* 流量提升
						@Entity(name = "Eva")
						@Table(name = "Eva")
						public class Eva {
						queryMyEva
			* 行为分析
				* 不用通知客户端，直接更新数据库
			*/
        boolean haschange = false;
        List<UUID> idToRemove = new ArrayList<>();
        Set<Entry<UUID, PromoOrder>> setOfEntries = promotions.entrySet();
        Iterator<Entry<UUID, PromoOrder>> iterator = setOfEntries.iterator();
        // iterate over map
        while (iterator.hasNext()) {
            Entry<UUID, PromoOrder> entry = iterator.next();
            PromoOrder promotion = (PromoOrder)entry.getValue();
            long curtime = System.currentTimeMillis();
            if(promotion.promStartTs > curtime){
                continue;
            }
            Building sellerBuilding = City.instance().getBuilding(promotion.sellerBuildingId);
            PublicFacility fcySeller = (PublicFacility) sellerBuilding ;
            int objType = promotion.buildingType > 0 ? promotion.buildingType: promotion.productionType;
            long endTime = promotion.promStartTs + promotion.promDuration;
            long elapsedtime = promotion.promDuration - (endTime - curtime);
            if(endTime >= System.currentTimeMillis()){
                //计算每个推广的结果
                float addition = fcySeller.excutePromotion(promotion);
                //累加提升值，以便计算平均值
                promotion.promotedTotal += addition;

                promotion.promProgress = (int)(((float)elapsedtime/(float)promotion.promDuration)*100);
                BrandManager.instance().update(promotion.buyerId, objType, (int)addition);
            }else {
                //超出时间的，移除掉，并通知玩家
                Player buyer = GameDb.getPlayer(promotion.buyerId);
                //更新买家玩家信息中的广告缓存
                buyer.delpayedPromotion(promotion.promotionId);
                GameDb.saveOrUpdate(buyer);
                //更新广告商广告列表
                GameDb.delete(fcySeller.delSelledPromotion(promotion.promotionId));
                GameDb.saveOrUpdate(fcySeller);
                //paras: 第一个是广告id，第二个是广告商建筑id
                MailBox.instance().sendMail(Mail.MailType.AD_PROMOTION_EXPIRE.getMailType(), promotion.buyerId, null, new UUID[]{promotion.promotionId, sellerBuilding.id()}, null);
                idToRemove.add(entry.getKey());
                haschange = true;
            }
        }
        for (int i = 0; i < idToRemove.size(); i++) {
            promotions.remove(idToRemove.get(i));
        }
        if(haschange){
            GameDb.saveOrUpdate(this);
        }
    }
}
