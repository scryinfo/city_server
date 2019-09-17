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

    private static long _elapsedtime = 0 ;    //上次更新时间
    public static final long _upDeltaNs = TimeUnit.SECONDS.toNanos(10);    //更新间隔，单位是纳秒, 3600为一个小时
    //public static final long _upDeltaNs = 10*1000000000;        //更新间隔，单位是纳秒, 3600为一个小时Ns
    public static final long _upDeltaMs = _upDeltaNs/1000000;   //更新间隔,毫秒
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
        ts计算公式： 下一个推广的promStartTs = 上一个推广的promStartTs + 上一个推广的 promDuration
        在一个循环中，需要把要删除的推广的promDuration设置为0；如果要删除的推广在第一个位置，那么promStartTs设置为0，
        其后的推广执行上述“ts计算公式”就都能正确更新。
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
        //更新完之后，移除掉要删除的推广。
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
        List<UUID> idToRemove = new ArrayList<>();
        Set<Entry<UUID, PromoOrder>> setOfEntries = promotions.entrySet();
        Iterator<Entry<UUID, PromoOrder>> iterator = setOfEntries.iterator();
        // iterate over map
        while (iterator.hasNext()) {
            Entry<UUID, PromoOrder> entry = iterator.next();
            PromoOrder promotion = (PromoOrder)entry.getValue();
            long curtime = System.currentTimeMillis();
            if(promotion.promStartTs > curtime){ //没到研究开始时间
                continue;
            }
            Building sellerBuilding = City.instance().getBuilding(promotion.sellerBuildingId);
            PublicFacility fcySeller = (PublicFacility) sellerBuilding ;
            int objType = promotion.buildingType > 0 ? promotion.buildingType: promotion.productionType;//具体的商品或建筑知名度类型
            int promType=promotion.buildingType > 0 ? promotion.buildingType: promotion.productionType/1000;//推广能力大类型
            long endTime = promotion.promStartTs + promotion.promDuration;
            long elapsedtime = promotion.promDuration - (endTime - curtime);//已经过的时间  10:00  12:00   2
            float addition = 0;
            long promHour = TimeUnit.MILLISECONDS.toHours(promotion.promDuration);
            if(endTime >= System.currentTimeMillis()){ //此处即便条件成立也不能立刻推广，还需要判断是否经过1小时。
                long now = System.currentTimeMillis();
                //long passTime = now - promotion.promStartTs;//这是剩余时间
                long passTime=TimeUnit.MILLISECONDS.toHours(elapsedtime);
                if(passTime-promotion.promoNum==1) {  //经过时间-推广次数（这就相当于每经过1小时就执行一次推广）
                    System.err.println("开始推广");
                    //计算每个推广的结果
                    //addition = fcySeller.excutePromotion(promotion);
                    addition = fcySeller.getLocalPromoAbility(promType);
                    //累加提升值，以便计算平均值
                    promotion.promotedTotal += addition;
                    promotion.promProgress = (int) (((float) elapsedtime / (float) promotion.promDuration) * 100);
                    promotion.promoNum++;
                }
            }else {
                //判断推广完成后推广加成是正确
                if(promotion.promoNum<promHour){//处理延时的问题
                    addition= fcySeller.getLocalPromoAbility(promType);
                    float residueProValue= (promHour - promotion.promoNum) * addition;
                    promotion.promotedTotal += residueProValue;
                }
                //超出时间的，移除掉，并通知玩家
                Player buyer = GameDb.getPlayer(promotion.buyerId);
                //更新买家玩家信息中的广告缓存
                buyer.delpayedPromotion(promotion.promotionId);
                GameDb.saveOrUpdate(buyer);
                //更新广告商广告列表
                fcySeller.delSelledPromotion(promotion.promotionId, false);
                GameDb.saveOrUpdate(fcySeller);
                idToRemove.add(entry.getKey());
                //推广完成通知
                //paras: 第一个是广告商建筑id，第二个是推广类型，第三个加成值,第四个是推广时长
                MailBox.instance().sendMail(Mail.MailType.PROMOTE_FINISH.getMailType(), promotion.buyerId, null, new UUID[]{promotion.sellerBuildingId}, new int[]{objType,(int) addition, (int) promHour});
                //发送给广告商
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
