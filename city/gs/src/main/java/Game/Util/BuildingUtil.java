package Game.Util;

import Game.Building;
import Game.City;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaData;
import Game.Meta.MetaGood;

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
    private Map<Integer, Map<Integer, Integer>> maxQtyMap = new HashMap<>();

    //初始化最大最小值品质
    public void init(){
        int apartQty=0;
        int retailQty=0;
        //初始基础品质
        apartQty=new ArrayList<>(MetaData.getApartment().values()).get(0).qty;
        retailQty=new ArrayList<>(MetaData.getRetailShop().values()).get(0).qty;
        //初始全城最大最小的建筑规模人数
        List<Integer> apartmentWorkers = updateBuildingNumByType(MetaBuilding.APARTMENT);
        List<Integer> retailShopWorkers=updateBuildingNumByType(MetaBuilding.RETAIL);
        Map<Integer, Integer> apartMap = getMaxOrMinQty(apartQty, apartmentWorkers);
        Map<Integer, Integer> retailMap = getMaxOrMinQty(retailQty, retailShopWorkers);
        maxQtyMap.put(MetaBuilding.APARTMENT, apartMap);
        maxQtyMap.put(MetaBuilding.RETAIL, retailMap);
    }

    public List<Integer> updateBuildingNumByType(int type){
        List<Integer> list = new ArrayList<>();
        Set<Building> building = City.instance().typeBuilding.getOrDefault(type,new HashSet<>());
        building.forEach(b->{
            if(!list.contains(b.getWorkerNum())) {
                list.add(b.getWorkerNum());
            }
        });
        return list;
    }

    private Map<Integer, Integer> getMaxOrMinQty(int qty,List<Integer> list){
        Map<Integer, Integer> map = new HashMap<>();
        Integer max = 0;
        Integer min =0;
        if(!list.isEmpty()) {
            max=Collections.max(list);
            min=Collections.min(list);
        }
        map.put(MAX, qty * max);
        map.put(MIN, qty * min);
        return map;
    }

    public Map<Integer,Integer> getMaxOrMinQty(int type){
        return maxQtyMap.get(type);
    }
    /*获取最大最小品牌值*/
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
