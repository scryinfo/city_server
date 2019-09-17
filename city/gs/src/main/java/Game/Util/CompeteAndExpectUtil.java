package Game.Util;

import Game.*;
import Game.Eva.Eva;
import Game.Eva.EvaManager;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaData;
import Game.Meta.MetaGood;
import gs.Gs;

import java.util.*;
/*此工具类提供了：获取建筑的竞争力和期望值的封装*/
public class CompeteAndExpectUtil {

    //1.品质权重(品质权重等等计算)(arg1:当前值，arg2:a类型，arg3：b类型,arg4：品质基础值)
    public static double getItemWeight(double localQuality,int at,int bt,int base){
       // 权重 = 当前值 / 全城最大 > 当前值 /全城最低 ?  当前值 / 全城最大 : 当前值 /全城最低
        Map<String, Eva> map = GlobalUtil.getEvaMaxAndMinValue(at,bt);
        if(map==null)
            return 1;
        Eva maxEva = map.get("max");//全城最大Eva
        Eva minEva = map.get("min");//全城最小Eva
        double maxValue=base * (1 + EvaManager.getInstance().computePercent(maxEva));//全城最大
        double minValue=base * (1 + EvaManager.getInstance().computePercent(minEva));//全城最小
        double weight = (localQuality / maxValue)> (localQuality / minValue) ? localQuality / maxValue : localQuality / minValue;
        return weight;
    }

    //2.知名度权重
    public static double getBrandWeight(int localBrand,int item){
        // 权重 = 当前值 / 全城最大 > 当前值 /全城最低 ?  当前值 / 全城最大 : 当前值 /全城最低
        //1.获取到全城该属性的最大最小的知名度(如果都为null，那么获取默认1)
        Map<String, BrandManager.BrandInfo> map = GlobalUtil.getMaxOrMinBrandInfo(item);
        int minBrand=0;
        int maxBrand=0;
        if(map!=null){
            minBrand = map.get("min").getV();
            maxBrand = map.get("max").getV();
        }
        if(MetaGood.isItem(item)){
            int goodBrand = MetaData.getGood(item).brand;
            minBrand += goodBrand;
            maxBrand += goodBrand;
        }
        if(minBrand==0&&maxBrand==0){//设置默认值
            return 0;
        }
        double weight = (localBrand / maxBrand)> (localBrand / minBrand) ? localBrand / maxBrand : localBrand / minBrand;
        return weight;
    }

    //3.获取加工厂建筑的竞争力
    public static Map<UUID,Double> getProductCompetitiveMap(List<Building> buildings, Eva eva){
        Map<UUID, Double> map = new HashMap<>();
        int at = eva.getAt();
        int bt = eva.getBt();
        int length = buildings.size();
        for (int i = 0 ; i < length; i++) {
            Building b=buildings.get(i);
            ProduceDepartment pro = (ProduceDepartment) b;
            //1.1判断是否该有该上架的商品
            if (!pro.getShelf().has(at)||b.outOfBusiness())
                continue;
            int price = pro.getShelf().getSellInfo(at).get(0).price;//玩家定价
            MetaGood good = MetaData.getGood(at);
            int qtyBase = good.quality;//商品的品质基础值
            //推荐价格
            int brandValue = BrandManager.instance().getBrand(b.ownerId(),at).getV()+good.brand;
            double evaAdd = EvaManager.getInstance().computePercent(eva);
            int recommendPrice = GlobalUtil.getProduceRecommendPrice(at,bt,qtyBase,evaAdd, brandValue, MetaBuilding.PRODUCE);//推荐价格
            double competitive= Math.ceil(recommendPrice / price * 100);//竞争力
            map.put(b.id(),competitive);
        }
        return map;
    }

    //4.获取推广公司的竞争力
    public static Map<UUID,Double> getPublicCompetitiveMap(List<Building> buildings, Eva eva){
        Map<UUID, Double> map = new HashMap<>();
        //1.全城推广均定价
        int cityAvgprice = GlobalUtil.getCityAvgPriceByType(MetaBuilding.PUBLIC);
        //2.全城的平均推广值
        int cityAvgAbility = GlobalUtil.cityAvgPromotionAbilityValue(eva.getAt(),MetaBuilding.PUBLIC);
        //3.全城该类型推广均单位定价
        int cityAbilityPrice = cityAvgprice/cityAvgAbility;
        for (Building b : buildings) {
            if(b.outOfBusiness())
                continue;
            PublicFacility pub = (PublicFacility) b;
            int price = pub.getCurPromPricePerHour();//定价
            double evaAdd = EvaManager.getInstance().computePercent(eva);//eva加成
            /*单项推广能力 = 基础推广力 * （1 + %单项eva能力提升） *（1+%流量提升）*/
            double promoAbility = pub.getBaseAbility()*(1+evaAdd)*(1 +pub.getFlowPromoCur());
            double recommendPrice = cityAbilityPrice * promoAbility;
            double competitive = Math.ceil(recommendPrice / price * 100);
            map.put(b.id(),competitive);
        }
        return map;
    }

    //5.获取研究所公司建筑的竞争力
    public static Map<UUID,Double> getLabCompetitiveMap(List<Building> buildings, Eva eva) {
        Map<UUID, Double> map = new HashMap<>();
        int at = eva.getAt();
        int bt = eva.getBt();
        for (Building building : buildings) {
            Laboratory lab = (Laboratory) building;
            if (lab.outOfBusiness()||lab.isExclusiveForOwner()||lab.getPricePreTime()==0)
                continue;
            int playerSuccessOdds = 0;//当前的发明概率
            int price = lab.getPricePreTime();
            double evaAdd = EvaManager.getInstance().computePercent(eva);//加成
            if (eva.getBt() == (Gs.Eva.Btype.InventionUpgrade.getNumber())) {//获取发明成功几率
                playerSuccessOdds = (int) (lab.getGoodProb() * (1 + evaAdd));
            } else {
                playerSuccessOdds = (int) (lab.getEvaProb() * (1 + evaAdd));//获取研究Eva的成功几率
            }
            //推荐定价
            int labRecommendPrice = GlobalUtil.getLabRecommendPrice(eva.getAt(), eva.getBt(),playerSuccessOdds); //推荐价格
            double competitive =Math.ceil(labRecommendPrice / price * 100);//竞争力
            map.put(building.id(),competitive);
        }
        return map;
    }
    /*获取原料厂的竞争力,和eva无关*/
    public static Map<UUID,Double> getMaterialCompetitiveMap(List<Building> buildings,int item){
        Map<UUID, Double> map = new HashMap<>();
        //获取全城的原料定价
        int avgPrice = GlobalUtil.getCityItemAvgPrice(item, MetaBuilding.MATERIAL);
        for (Building building : buildings) {
            MaterialFactory materialFactory = (MaterialFactory) building;
            if(materialFactory.outOfBusiness() || !materialFactory.getShelf().has(item))
                continue;
            int price = materialFactory.getShelf().getSellInfo(item).get(0).price;
            //竞争力 = 推荐定价 / 定价 * 100 (向上取整)
            double competitive = Math.ceil(avgPrice / price) * 100;
            map.put(building.id(), competitive);
        }
        return map;
    }
}
