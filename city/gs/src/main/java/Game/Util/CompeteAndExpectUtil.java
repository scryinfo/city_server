package Game.Util;

import Game.*;
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
        double maxValue=base ;//全城最大
        double minValue=base ;//全城最小
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
