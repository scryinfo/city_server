package Game.Util;

import Game.BrandManager;
import Game.Meta.MetaApartment;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaData;
import Game.Meta.MetaRetailShop;

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

    public void init(){
        int apartQty=0;
        int retailQty=0;
        List<Integer> araptmentWorkers = new ArrayList<>();
        List<Integer> retailShopWorkers = new ArrayList<>();
        for (MetaApartment a : MetaData.getApartment().values()) {
            araptmentWorkers.add(a.workerNum);
            if(apartQty==0)
                apartQty = a.qty;
        }
        for (MetaRetailShop r : MetaData.getRetailShop().values()) {
            retailShopWorkers.add(r.workerNum);
            if(retailQty==0)
                retailQty = r.qty;
        }
        Map<Integer, Integer> apartMap = getMaxOrMinQty(apartQty, araptmentWorkers);
        Map<Integer, Integer> retailMap = getMaxOrMinQty(retailQty, retailShopWorkers);
        maxQtyMap.put(MetaBuilding.APARTMENT, apartMap);
        maxQtyMap.put(MetaBuilding.RETAIL, retailMap);
    }


    private Map<Integer, Integer> getMaxOrMinQty(int qty,List<Integer> list){
        Map<Integer, Integer> map = new HashMap<>();
        Integer max = Collections.max(list);
        Integer min = Collections.min(list);
        map.put(MAX, qty * max);
        map.put(MIN, qty * min);
        return map;
    }

    public Map<Integer,Integer> getMaxOrMinQty(int type){
        return maxQtyMap.get(type);
    }

    public Map<Integer,Integer> getMaxAndMinBrand(int item){
        Map<Integer, Integer> map = new HashMap<>();
        Map<String, BrandManager.BrandInfo> cityBrandMap = GlobalUtil.getMaxOrMinBrandInfo(item);
        int minBrand=1;
        int maxBrand=1;
        if(cityBrandMap!=null){
            minBrand = cityBrandMap.get("min").getV();
            maxBrand = cityBrandMap.get("max").getV();
        }
        map.put(MAX, maxBrand);
        map.put(MIN, minBrand);
        return map;
    }

}
