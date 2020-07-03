package Game.Util;

import Game.*;
import Game.Eva.Eva;
import Game.Eva.EvaManager;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaData;
import Game.Meta.MetaGood;
import gs.Gs;

import java.util.*;
/*This tool class provides: packaging to obtain the competitiveness and expectations of the building*/
public class CompeteAndExpectUtil {

    //1.Quality weight (calculation of quality weight, etc.)(arg1: current value, arg2: type a, arg3: type b, arg4: basic quality value)
    public static double getItemWeight(double localQuality,int at,int bt,int base){
       // Weights =The current value / The largest in the city > The current value /The lowest in the city ?  The current value / The largest in the city : The current value /The lowest in the city
        Map<String, Eva> map = GlobalUtil.getEvaMaxAndMinValue(at,bt);
        if(map==null)
            return 1;
        Eva maxEva = map.get("max");//The largest in the city Eva
        Eva minEva = map.get("min");//The smallest in the city Eva
        double maxValue=base * (1 + EvaManager.getInstance().computePercent(maxEva));//The largest in the city
        double minValue=base * (1 + EvaManager.getInstance().computePercent(minEva));//The smallest in the city
        double weight = (localQuality / maxValue)> (localQuality / minValue) ? localQuality / maxValue : localQuality / minValue;
        return weight;
    }

    //2.Popularity weight
    public static double getBrandWeight(int localBrand,int item){
        // The current value / The largest in the city > The current value /The lowest in the city ?  The current value / The largest in the city : The current value /The lowest in the city
        //1. Obtain the maximum and minimum visibility of this attribute in the city (if both are null, then get the default 1)
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
        if(minBrand==0&&maxBrand==0){//Set default
            return 0;
        }
        double weight = (localBrand / maxBrand)> (localBrand / minBrand) ? localBrand / maxBrand : localBrand / minBrand;
        return weight;
    }

    //3.Gain the competitiveness of processing plant construction
    public static Map<UUID,Double> getProductCompetitiveMap(List<Building> buildings, Eva eva){
        Map<UUID, Double> map = new HashMap<>();
        int at = eva.getAt();
        int bt = eva.getBt();
        int length = buildings.size();
        for (int i = 0 ; i < length; i++) {
            Building b=buildings.get(i);
            ProduceDepartment pro = (ProduceDepartment) b;
            //1.1 Determine whether there should be the goods on the shelf
            if (!pro.getShelf().has(at)||b.outOfBusiness())
                continue;
            int price = pro.getShelf().getSellInfo(at).get(0).price;//Player pricing
            MetaGood good = MetaData.getGood(at);
            int qtyBase = good.quality;//Basic value of product quality
            //Recommended price
            int brandValue = BrandManager.instance().getBrand(b.ownerId(),at).getV()+good.brand;
            double evaAdd = EvaManager.getInstance().computePercent(eva);
            int recommendPrice = GlobalUtil.getProduceRecommendPrice(at,bt,qtyBase,evaAdd, brandValue, MetaBuilding.PRODUCE);//推荐价格
            double competitive= Math.ceil(recommendPrice / price * 100);//Competitiveness
            map.put(b.id(),competitive);
        }
        return map;
    }

    //4.Gain the competitiveness of the promotion company
    public static Map<UUID,Double> getPublicCompetitiveMap(List<Building> buildings, Eva eva){
        Map<UUID, Double> map = new HashMap<>();
        //1.Pricing for promotion across the city
        int cityAvgprice = GlobalUtil.getCityAvgPriceByType(MetaBuilding.PUBLIC);
        //2.The average promotion value of the whole city
        int cityAvgAbility = GlobalUtil.cityAvgPromotionAbilityValue(eva.getAt(),MetaBuilding.PUBLIC);
        //3.Unit pricing for this type of promotion across the city
        int cityAbilityPrice = cityAvgprice/cityAvgAbility;
        for (Building b : buildings) {
            if(b.outOfBusiness())
                continue;
            PublicFacility pub = (PublicFacility) b;
            int price = pub.getCurPromPricePerHour();//Pricing
            double evaAdd = EvaManager.getInstance().computePercent(eva);//eva addition
            /*Single promotion ability = Basic promotion * （1 + %Single eva capacity improvement） *（1+%Increased traffic）*/
            double promoAbility = pub.getBaseAbility()*(1+evaAdd)*(1 +pub.getFlowPromoCur());
            double recommendPrice = cityAbilityPrice * promoAbility;
            double competitive = Math.ceil(recommendPrice / price * 100);
            map.put(b.id(),competitive);
        }
        return map;
    }

    //5.Gain the competitiveness of the Institute's corporate architecture
    public static Map<UUID,Double> getLabCompetitiveMap(List<Building> buildings, Eva eva) {
        Map<UUID, Double> map = new HashMap<>();
        int at = eva.getAt();
        int bt = eva.getBt();
        for (Building building : buildings) {
            Laboratory lab = (Laboratory) building;
            if (lab.outOfBusiness()||lab.isExclusiveForOwner()||lab.getPricePreTime()==0)
                continue;
            int playerSuccessOdds = 0;//Current probability of invention
            int price = lab.getPricePreTime();
            double evaAdd = EvaManager.getInstance().computePercent(eva);//addition
            if (eva.getBt() == (Gs.Eva.Btype.InventionUpgrade.getNumber())) {//Get chance of success
                playerSuccessOdds = (int) (lab.getGoodProb() * (1 + evaAdd));
            } else {
                playerSuccessOdds = (int) (lab.getEvaProb() * (1 + evaAdd));//Get a chance to succeed in researching Eva
            }
            //Recommended pricing
            int labRecommendPrice = GlobalUtil.getLabRecommendPrice(eva.getAt(), eva.getBt(),playerSuccessOdds); //Recommended price
            double competitive =Math.ceil(labRecommendPrice / price * 100);//Competitiveness
            map.put(building.id(),competitive);
        }
        return map;
    }

    //6.TODO:Expected cost of obtaining residential npc
    //Parameters: arg1: all residential buildings arg2: current eva information arg3: npc cost ratio
    public static  Map<UUID,List<Integer>> getApartmentExpectSpend(List<Building> buildings,Eva eva,double npcSpendRatio) {
        Map<UUID,List<Integer>> expectSpends = new HashMap<>();
        int at = eva.getAt();
        int bt = eva.getBt();
        int avgAvgBrand = GlobalUtil.cityAvgBrand(at);//City-wide visibility
        int cityAvgQuality = GlobalUtil.getCityApartmentOrRetailShopQuality(at, bt,MetaBuilding.APARTMENT);//City-wide quality
        for (Building building : buildings) {
            if (building.outOfBusiness())
                continue;
            Apartment apartment = (Apartment) building;
            int localBrand= BrandManager.instance().getBrand(apartment.ownerId(), at).getV()==0?1:BrandManager.instance().getBrand(apartment.ownerId(), at).getV();//Player Brand
            int localQuality = (int) (apartment.quality() * (1+EvaManager.getInstance().computePercent(eva)));//Player quality
            double totalWeight = getItemWeight(localQuality, at, bt, apartment.quality()) + getBrandWeight(localBrand, at);
            //Player Expected Cost (Residential Expense for Residence = (Total Weight * 200/3 + 1) * NPC Expected Expense Ratio * NPC Average Wage)
            int expectSpend = (int) Math.ceil(((totalWeight * 200 / 3 + 1) * npcSpendRatio * CityUtil.cityAvgSalary()));
            //Expected cost of the city
            double cityTotalWeight = getItemWeight(cityAvgQuality, at, bt, apartment.quality()) + getBrandWeight(avgAvgBrand, at);
            int cityExpectSpend =(int) Math.ceil(((cityTotalWeight * 200 / 3 + 1) * npcSpendRatio * CityUtil.cityAvgSalary()));
            List<Integer> data=new ArrayList<>();
            data.add(expectSpend);
            data.add(cityExpectSpend);
            expectSpends.put(building.id(), data);
        }
        return expectSpends;
    }
    /*Obtaining the competitiveness of the raw material factory has nothing to do with eva*/
    public static Map<UUID,Double> getMaterialCompetitiveMap(List<Building> buildings,int item){
        Map<UUID, Double> map = new HashMap<>();
        //Get raw material pricing across the city
        int avgPrice = GlobalUtil.getCityItemAvgPrice(item, MetaBuilding.MATERIAL);
        for (Building building : buildings) {
            MaterialFactory materialFactory = (MaterialFactory) building;
            if(materialFactory.outOfBusiness() || !materialFactory.getShelf().has(item))
                continue;
            int price = materialFactory.getShelf().getSellInfo(item).get(0).price;
            //Competitiveness = recommended pricing / pricing * 100 (round up)
            double competitive = Math.ceil(avgPrice / price) * 100;
            map.put(building.id(), competitive);
        }
        return map;
    }
}
