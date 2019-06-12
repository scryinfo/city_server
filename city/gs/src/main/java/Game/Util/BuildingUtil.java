package Game.Util;

import Game.Meta.MetaApartment;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaData;
import Game.Meta.MetaRetailShop;

import java.util.*;

public class BuildingUtil {

    private static BuildingUtil instance = new BuildingUtil();
    private BuildingUtil(){}
    public static BuildingUtil instance(){
        return instance;
    }

    //缓存零售店和住宅最大最小的基础品质
    private Map<Integer, Map<String, Integer>> maxQtyMap = new HashMap<>();

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
        Map<String, Integer> apartMap = getMaxOrMinQty(apartQty, araptmentWorkers);
        Map<String, Integer> retailMap = getMaxOrMinQty(retailQty, retailShopWorkers);
        maxQtyMap.put(MetaBuilding.APARTMENT, apartMap);
        maxQtyMap.put(MetaBuilding.RETAIL, retailMap);
    }


    private Map<String, Integer> getMaxOrMinQty(int qty,List<Integer> list){
        Map<String, Integer> map = new HashMap<>();
        Integer max = Collections.max(list);
        Integer min = Collections.min(list);
        map.put("max", qty * max);
        map.put("min", qty * min);
        return map;
    }

    public Map<String,Integer> getMaxOrMinQty(int type){
        return maxQtyMap.get(type);
    }

}
