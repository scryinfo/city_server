package Game.Util;

import Game.Apartment;
import Game.Building;
import Game.City;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaData;
import Game.Meta.MetaGood;
import Game.Meta.MetaItem;
import Game.RetailShop;
import Game.Timers.PeriodicTimer;
import io.grpc.Metadata;

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
    private PeriodicTimer timer = new PeriodicTimer((int) TimeUnit.SECONDS.toMillis(10));
    //缓存全城原料均定价
    private static Map<Integer, Double> materialAvg = new HashMap<>();
    //缓存全城住宅均定价和评分
    private static List<Double> apartmentAvg = new ArrayList<>();
    //缓存全城加工厂定价和评分
    private static Map<Integer, List<Double>> produceAvg = new HashMap<>();
    //缓存全城零售店定价和评分
    private static Map<Integer, List<Double>> retailAvg = new HashMap<>();
    //缓存全城推广能力
    private static List<Double> promotionAvg = new ArrayList<>();
    //缓存全城研究概率
    private static List<Double> laboratoryAvg = new ArrayList<>();
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
        _update();
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
            minBrand +=100;// 添加默认值
            maxBrand +=100;// 添加基础值
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
    public static Map<Integer, List<Double>> getRetail() {
        return retailAvg;
    }

    public static List<Double> getPromotion() {
        return promotionAvg;
    }
    public static List<Double> getLaboratory() {
        return laboratoryAvg;
    }

    public  void update(long diffNano) {
        if (timer.update(diffNano)) {
            System.out.println("--------------走位.走位--------------");
            apartmentAvg.clear();
            promotionAvg.clear();
            laboratoryAvg.clear();
            _update();
        }
    }

    public void _update() {
        this.getMaterialInfo();
        this.getApartmentInfo();
        this.getProduceInfo();
        this.getRetailInfo();
        this.getPromotionInfo();
        this.getLaboratoryInfo();
    }
    public void getPromotionInfo() {
        Set<Integer> promotionIds = MetaData.getAllPromotionId(MetaBuilding.PUBLIC);
        double sumAbility = 0;
        for (Object id : promotionIds) {
            int typeId = 0;
            if (id instanceof Integer) {
                typeId = (Integer) id;
                sumAbility += GlobalUtil.cityAvgPromotionAbilityValue(typeId, MetaBuilding.PUBLIC);
            }
        }
        //所有不同类型推广能力和 / 4
        double ability = sumAbility / 4;
        double promotionPrice = GlobalUtil.getCityAvgPriceByType(MetaBuilding.PUBLIC);
        promotionAvg.add(promotionPrice);
        promotionAvg.add(ability);
    }
    public void getLaboratoryInfo() {
        // 价格
        double labPrice = GlobalUtil.getCityAvgPriceByType(MetaBuilding.LAB);
        // 研发能力
        double abilitys = GlobalUtil.getLaboratoryInfo();
        laboratoryAvg.add(labPrice);
        laboratoryAvg.add(abilitys);
    }

    public void getRetailInfo() {
        Set<Integer> ids = MetaData.getAllGoodId();
        for (Object id : ids) {
            int itemId = 0;
            if (id instanceof Integer) {
                itemId = (Integer) id;
            }
            List<Double> info = GlobalUtil.getRetailInfo(itemId);
            retailAvg.put(itemId, info);
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
        List<Double> info = GlobalUtil.getApartmentInfo();
        this.apartmentAvg = info;
    }

}
