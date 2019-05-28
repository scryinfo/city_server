package Game.Util;

import Game.*;
import Game.Eva.Eva;
import Game.Eva.EvaManager;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaData;
import Game.Meta.MetaGood;
import gs.Gs;

import java.util.*;

public class EvaUtil {
    //品质权重(品质权重等等计算)(arg1:当前值，arg2:a类型，arg3：b类型,arg4：品质基础值)
    public static double getItemWeight(int localQuality,int at,int bt,int base){
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

    //知名度权重
    public static double getBrandWeight(int localBrand,int item){
        // 权重 = 当前值 / 全城最大 > 当前值 /全城最低 ?  当前值 / 全城最大 : 当前值 /全城最低
        //1.获取到全城该属性的最大最小的知名度(如果都为null，那么获取默认1)
        Map<String, BrandManager.BrandInfo> map = GlobalUtil.getMaxOrMinBrandInfo(item);
        int minBrand=1;
        int maxBrand=1;
        if(map!=null){
            minBrand = map.get("min").getV();
            maxBrand = map.get("max").getV();
        }
        if(MetaGood.isItem(item)){
            int goodBrand = MetaData.getGood(item).brand;
            minBrand += goodBrand;
            maxBrand += goodBrand;
        }
        double weight = (localBrand / maxBrand)> (localBrand / minBrand) ? localBrand / maxBrand : localBrand / minBrand;
        return weight;
    }

    //获取加工厂建筑的竞争力
    public static Map<UUID,Double> getProductCompetitiveMap(List<Building> buildings, Eva eva){
        Map<UUID, Double> map = new HashMap<>();
        int at = eva.getAt();
        int bt = eva.getBt();
        for (Building b : buildings) {
            Gs.Promote.Builder promote = Gs.Promote.newBuilder();
            ProduceDepartment pro = (ProduceDepartment) b;
            //1.1判断是否该有该上架的商品
            if (!pro.getShelf().has(at)||b.outOfBusiness())
                continue;
            int price = pro.getShelf().getSellInfo(at).get(0).price;//售价
            MetaGood good = MetaData.getGood(at);
            int base = good.quality;//商品的品质基础值
            //获取品牌信息
            int brandValue = BrandManager.instance().getBrand(b.ownerId(),at).getV()+good.brand;
            double evaAdd = EvaManager.getInstance().computePercent(eva);
            int recommendPrice = GlobalUtil.getProduceRecommendPrice(at,bt,base,evaAdd, brandValue, MetaBuilding.PRODUCE);//推荐价格
            int competitive = (int) Math.ceil(recommendPrice / price * 100);//竞争力
            map.put(b.id(), (double) competitive);
        }
        return map;
    }

    //获取推广公司的竞争力
    public static Map<UUID,Double> getPublicCompetitiveMap(List<Building> buildings, Eva eva){
        Map<UUID, Double> map = new HashMap<>();
        //1.全城推广均定价
        int cityAvgprice = GlobalUtil.getCityAvgPriceByType(MetaBuilding.PUBLIC);
        //2.全城的平均推广值
        int cityAvgAbility = GlobalUtil.cityAvgPromotionAbilityValue(eva.getAt());
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
            int competitive = (int) Math.ceil(recommendPrice / price * 100);
            map.put(b.id(), (double) competitive);
        }
        return map;
    }

    //获取研究所公司建筑的竞争力
    public static Map<UUID,Double> getLabCompetitiveMap(List<Building> buildings, Eva eva) {
        Map<UUID, Double> map = new HashMap<>();
        int at = eva.getAt();
        int bt = eva.getBt();
        for (Building building : buildings) {
            if (building.outOfBusiness())
                continue;
            Laboratory lab = (Laboratory) building;
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
            int competitive = (int) Math.ceil(labRecommendPrice / price * 100);//竞争力
            map.put(building.id(), (double) competitive);
        }
        return map;
    }


    //4.抽取住宅和零售店的预期值
    public  static List<Gs.Promote> getApartmentOrRetailShopPromoteInfo(List<Building> buildings,Gs.Eva msEva, Eva newEva){
        int at = msEva.getAt();
        int bt = msEva.getBt().getNumber();
        for (Building b : buildings) {

        }
        return null;
    }

    //TODO:获取住宅npc的预期值
    //参数1：eva a类型 ，2：eva b类型，3：当前品牌值，4：基础品质值（也就是建筑中的品质qty）
    // 5:eva加成，6：npc的预期花费比例，7：玩家定价，8：npc每小时需
    public static  Map<UUID,List<Integer>> getApartmentExpectSpend(List<Building> buildings,Eva eva,double npcSpendRatio) {
        Map<UUID,List<Integer>> expectSpends = new HashMap<>();
        int at = eva.getAt();
        int bt = eva.getBt();
        for (Building building : buildings) {
            if (building.outOfBusiness())
                continue;
            Apartment apartment = (Apartment) building;
            int avgAvgBrand = GlobalUtil.cityAvgBrand(at);//全城知名度
            int cityAvgQuality = GlobalUtil.getCityApartmentOrRetailShopQuality(at, bt);//全城品质
            int localBrand= BrandManager.instance().getBrand(apartment.ownerId(), at).getV();//玩家品牌
            int localQuality = (int) (apartment.quality() * (EvaManager.getInstance().computePercent(eva)));//玩家品质
            double totalWeight = getItemWeight(localQuality, at, bt, apartment.quality()) + getBrandWeight(localBrand, at);
            //玩家预期花费（住宅预期花费 = (总权重 * 200 / 3 + 1) * NPC预期花费比例 * NPC平均工资）
            int expectSpend = (int) ((totalWeight * 200 / 3 + 1) * npcSpendRatio * CityUtil.cityAvgSalary());
            //全城的预期花费
            double cityTotalWeight = getItemWeight(cityAvgQuality, at, bt, apartment.quality()) + getBrandWeight(avgAvgBrand, at);
            int cityExpectSpend = (int) ((cityTotalWeight * 200 / 3 + 1) * npcSpendRatio * CityUtil.cityAvgSalary());
            List<Integer> data=new ArrayList<>();
            data.add(expectSpend);
            data.add(cityExpectSpend);
            expectSpends.put(building.id(), data);
        }
        return expectSpends;
    }
}
