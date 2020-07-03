package Game.Util;

import Game.*;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaData;
import Game.Meta.MetaGood;
import Game.Timers.PeriodicTimer;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class BuildingUtil {
    public static final int MAX=1;
    public static final int MIN=2;
    private static BuildingUtil instance = new BuildingUtil();
    private BuildingUtil(){}
    public static BuildingUtil instance(){
        return instance;
    }
    private PeriodicTimer timer = new PeriodicTimer((int) TimeUnit.MINUTES.toMillis(2));
    //Cache all raw materials throughout the city
    private static Map<Integer, Double> materialAvg = new HashMap<>();
    //Caching city-wide residential pricing and scoring
    private static List<Double> apartmentAvg = new ArrayList<>();
    //Caching citywide factory pricing and scoring
    private static Map<Integer, List<Double>> produceAvg = new HashMap<>();
    //Cache product pricing and product ratings for retail stores across the city
    private static Map<Integer, List<Double>> retailGoodsAvg = new HashMap<>();
    //Citywide retail store ratings
    private static double retailScore = 0;
    //Cache citywide promotion average price
    private static double promotionAvg = 0;
    //Cache city average research price
    private static double laboratoryAvg = 0;
    //Cache the largest and smallest basic quality of retail stores and residences
    private Map<Integer, Map<Integer, Double>> maxQtyTotalMap = new HashMap<>();
    //Get the maximum and minimum brand value
    public Map<Integer,Double> getMaxOrMinQty(int type){
        return maxQtyTotalMap.get(type);
    }

    //Update the maximum and minimum quality: update when constructing buildings\demolition buildings and modifying eva
    public void updateMaxOrMinTotalQty(){
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
        }
        if(!apartmentSet.isEmpty()) {
            Double maxApartQty = Collections.max(apartmentSet);
            Double minApartQty = Collections.min(apartmentSet);
            apartmentMap.put(MAX, maxApartQty);
            apartmentMap.put(MIN, minApartQty);
        }
        maxQtyTotalMap.put(MetaBuilding.RETAIL, retailMap);
        maxQtyTotalMap.put(MetaBuilding.APARTMENT,apartmentMap);
        _update();
    }

    /*Get the maximum and minimum visibility value*/
    public Map<Integer,Integer> getMaxAndMinBrand(int item){
        Map<Integer, Integer> map = new HashMap<>();
        Map<String, Double> cityBrandMap = GlobalUtil.getMaxOrMinBrandValue(item);//Query is the maximum and minimum lift ratio of Eva
        double minRatio=cityBrandMap.get("min");
        double maxRatio=cityBrandMap.get("max");
        //If it is a product (use the product's default visibility)
        int maxBrand;
        int minBrand;
        if(MetaGood.isItem(item)){
            MetaGood good = MetaData.getGood(item);
            maxBrand = (int) (good.brand*(1+maxRatio));
            minBrand = (int) (good.brand*(1+minRatio));
        }else{
            maxBrand = (int) (BrandManager.BASE_BRAND * (1 + maxRatio));
            minBrand = (int) (BrandManager.BASE_BRAND * (1 + minRatio));
        }
        map.put(MAX, maxBrand);
        map.put(MIN, minBrand);
        return map;
    }

    public static Map<Integer, Double> getMaterial() {
        return materialAvg;
    }
    public static List<Double> getApartment() {
        return apartmentAvg;
    }
    public static Map<Integer, List<Double>> getProduce() {
        return produceAvg;
    }
    public static Map<Integer, List<Double>> getRetailGood() {
        return retailGoodsAvg;
    }
    public static double getRetail() {
        return retailScore;
    }

    public static double getPromotion() {
        return promotionAvg;
    }
    public static double getLaboratory() {
        return laboratoryAvg;
    }

    public  void update(long diffNano) {
        if (timer.update(diffNano)) {
            apartmentAvg.clear();
            _update();
        }
    }

    public void _update() {
        this.getMaterialInfo();
        this.getApartmentInfo();
        this.getProduceInfo();
        this.getRetailInfo();
        this.retailScore = GlobalUtil.getRetailInfo();
        /*this.promotionAvg = GlobalUtil.getCityAvgPriceByType(MetaBuilding.PUBLIC);
        this.laboratoryAvg = GlobalUtil.getCityAvgPriceByType(MetaBuilding.LAB);*/
    }

    public void getRetailInfo() {
        Set<Integer> ids = MetaData.getAllGoodId();
        for (Object id : ids) {
            int itemId = 0;
            if (id instanceof Integer) {
                itemId = (Integer) id;
            }
            List<Double> info = GlobalUtil.getRetailGoodInfo(itemId);
            retailGoodsAvg.put(itemId, info);
        }

    }
    public void getProduceInfo() {
        Set<Integer> ids = MetaData.getAllGoodId();
        for (Object id : ids) {
            int itemId = 0;
            if (id instanceof Integer) {
                itemId = (Integer) id;
            }
            List<Double> info = GlobalUtil.getproduceInfo(itemId);
            produceAvg.put(itemId, info);
        }

    }

    public void getMaterialInfo() {
        Set ids = MetaData.getAllMaterialId();
        for (Object id : ids) {
            int itemId = 0;
            if (id instanceof Integer) {
                itemId = (Integer) id;
            }
            double price = GlobalUtil.getMaterialInfo(itemId);
            materialAvg.put(itemId, price);
        }
    }
    public  void getApartmentInfo() {
        this.apartmentAvg = GlobalUtil.getApartmentInfo();
    }

}
