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
    //缓存全城原料均定价
    private static Map<Integer, Double> materialAvg = new HashMap<>();
    //缓存全城住宅均定价和评分
    private static List<Double> apartmentAvg = new ArrayList<>();
    //缓存全城加工厂定价和评分
    private static Map<Integer, List<Double>> produceAvg = new HashMap<>();
    //缓存全城零售店商品定价和商品评分
    private static Map<Integer, List<Double>> retailGoodsAvg = new HashMap<>();
    //全城零售店评分
    private static double retailScore = 0;
    //缓存全城推广均价
    private static double promotionAvg = 0;
    //缓存全城研究均价
    private static double laboratoryAvg = 0;
    //缓存零售店和住宅最大最小的基础品质
    private Map<Integer, Map<Integer, Double>> maxQtyTotalMap = new HashMap<>();
    //获取最大最小品牌值
    public Map<Integer,Double> getMaxOrMinQty(int type){
        return maxQtyTotalMap.get(type);
    }

    //更新最大最小品质：建造建筑\拆除建筑和修改eva时更新
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

    /*获取最大最小知名度值*/
    public Map<Integer,Integer> getMaxAndMinBrand(int item){
        Map<Integer, Integer> map = new HashMap<>();
        Map<String, Double> cityBrandMap = GlobalUtil.getMaxOrMinBrandValue(item);//查询的是Eva最大最小的提升比例
        double minRatio=cityBrandMap.get("min");
        double maxRatio=cityBrandMap.get("max");
        //如果是商品(使用商品的默认知名度)
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
