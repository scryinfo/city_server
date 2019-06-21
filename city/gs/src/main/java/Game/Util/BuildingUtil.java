package Game.Util;

import Game.Apartment;
import Game.Building;
import Game.City;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaData;
import Game.Meta.MetaGood;
import Game.RetailShop;

import java.util.*;

public class BuildingUtil {
    public static final int MAX=1;
    public static final int MIN=2;
    private static BuildingUtil instance = new BuildingUtil();
    private BuildingUtil(){}
    public static BuildingUtil instance(){
        return instance;
    }

    //缓存零售店和住宅最大最小的基础品质
    private Map<Integer, Map<Integer, Double>> maxQtyTotalMap = new HashMap<>();
    //获取最大最小品牌值
    public Map<Integer,Double> getMaxOrMinQty(int type){
        return maxQtyTotalMap.get(type);
    }

    //更新的时机，建造建筑\拆除建筑和修改eva时更新
    public void updateMaxOrMinTotalQty(){
        System.err.println("开始更新全城最高最低建筑品质");
        Set<Double> retailSet = new HashSet<>();
        Set<Double> apartmentSet = new HashSet<>();
        Set<Building> retailShops = City.instance().typeBuilding.getOrDefault(MetaBuilding.RETAIL,new HashSet<>());
        Set<Building> apartments = City.instance().typeBuilding.getOrDefault(MetaBuilding.APARTMENT,new HashSet<>());
        for (Building b : retailShops) {
            RetailShop retailShop = (RetailShop) b;
            retailSet.add(retailShop.getTotalQty());
        }
        for (Building b : apartments) {
            Apartment apartment = (Apartment) b;
            apartmentSet.add(apartment.getTotalQty());
        }
        HashMap<Integer,Double> retailMap = new HashMap<>();
        retailMap.put(MAX,0d);
        retailMap.put(MIN,0d);
        HashMap<Integer,Double> apartmentMap = new HashMap<>();
        apartmentMap.put(MAX, 0d);
        apartmentMap.put(MIN, 0d);
        if(!retailSet.isEmpty()) {
            Double maxRetailQty = Collections.max(retailSet);
            Double minRetailQty = Collections.min(retailSet);
            retailMap.put(MAX, maxRetailQty);
            retailMap.put(MIN, minRetailQty);
            System.err.println("零售店最高品质"+maxRetailQty+"  最低品质"+minRetailQty);
        }
        if(!apartmentSet.isEmpty()) {
            Double maxApartQty = Collections.max(apartmentSet);
            Double minApartQty = Collections.min(apartmentSet);
            apartmentMap.put(MAX, maxApartQty);
            apartmentMap.put(MIN, minApartQty);
            System.err.println("住宅最高品质"+maxApartQty+"  最低品质"+minApartQty);
        }
        maxQtyTotalMap.put(MetaBuilding.RETAIL, retailMap);
        maxQtyTotalMap.put(MetaBuilding.APARTMENT,apartmentMap);
    }

    /*获取最大最小知名度值*/
    public Map<Integer,Integer> getMaxAndMinBrand(int item){
        Map<Integer, Integer> map = new HashMap<>();
        Map<String, Integer> cityBrandMap = GlobalUtil.getMaxOrMinBrandValue(item);//如果没有推广过品牌，返回的最大最小值都是0，没有包含基础值
        int minBrand=cityBrandMap.get("min");
        int maxBrand=cityBrandMap.get("max");
        //如果是商品(使用商品的默认知名度)
        if(MetaGood.isItem(item)){
            MetaGood good = MetaData.getGood(item);
            minBrand += good.brand;
            maxBrand += good.brand;
        }else{
            minBrand +=1;// 添加默认值
            maxBrand +=1;// 添加基础值
        }
        map.put(MAX, maxBrand);
        map.put(MIN, minBrand);
        return map;
    }
}
