package Game.Util;

import Game.*;
import Game.Eva.Eva;
import Game.Eva.EvaManager;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaData;
import Game.Meta.MetaGood;
import gs.Gs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GlobalUtil {

    //1.获取商品的全城销售均价(获取所有花架上该商品的销售价格取平均值)
    public static int getCityItemAvgPrice(int goodItem, int type) {
        int sumPrice = 0;
        int count = 0;
        List<Building> buildings = City.instance().getAllBuilding();
        for (Building building : buildings) {
            //判断类型
            if (!(building instanceof IShelf) || building.type() != type)
                continue;
            IShelf shelf = (IShelf) building;
            Map<Item, Integer> saleDetail = shelf.getSaleDetail(goodItem);
            if (saleDetail != null) {
                Integer price = new ArrayList<>(saleDetail.values()).get(0);
                count++;
            }
        }
        return sumPrice / count;
    }

    //2.查询全城建筑中设置的平均价格
    public static int getCityAvgPriceByType(int type) {
        int sumPrice = 0;
        int count = 0;
        List<Building> buildings = City.instance().getAllBuilding();
        for (Building building : buildings) {
            if (building.type() == type && !building.outOfBusiness()) {
               switch (type){
                   case MetaBuilding.APARTMENT:
                       Apartment apartment = (Apartment) building;
                       sumPrice += apartment.cost();
                       count++;
                       break;
                   case MetaBuilding.PUBLIC:
                       PublicFacility facility = (PublicFacility) building;
                       sumPrice += facility.getCurPromPricePerHour();
                       count++;
                       break;
                   case MetaBuilding.LAB:
                       Laboratory lab = (Laboratory) building;
                       sumPrice += lab.getPricePreTime();
                       count++;
                       break;
               }
            }
        }
        if(count!=0)
            return sumPrice / count;
        else
            return 1;
    }

    //3.查询根据类型研究成功几率，依然是建筑的成功平均值+eva的平均提升值
    public static int getCityAvgSuccessOdds(int at,int bt){
        int sumBaseOdds = 0;
        int count=0;
        List<Building> buildings = City.instance().getAllBuilding();
        for (Building building : buildings) {
            if (building instanceof Laboratory&&!building.outOfBusiness()) {
                Laboratory lab = (Laboratory) building;
                if(bt==Gs.Eva.Btype.InventionUpgrade.getNumber()){//均发明成功几率
                    sumBaseOdds+=lab.getGoodProb();
                    count++;
                }else {//均研究成功几率
                    sumBaseOdds+=lab.getEvaProb();
                    count++;
                }
            }
        }
        //计算发明或研究的的平均Eva加成信息
        int avgEvaAdd = cityAvgEva(at, bt);
        if(count==0)
            return 1;
        else{
            return (sumBaseOdds / count) * (1 + avgEvaAdd);
        }
    }

   //4.全城商品均知名度 || 全城住宅均知名度
   public static int cityAvgBrand(int item){
       int sum=0;
       int count = 0;
      //首先获取所有的品牌信息
       List<BrandManager.BrandInfo> allBrand = BrandManager.instance().getAllBrandInfoByItem(item);
       for (BrandManager.BrandInfo brandInfo : allBrand) {
           if(brandInfo.getKey().getMid()==item){
               sum += brandInfo.getV();
               count++;
           }
       }
       if(sum==0){
           return 1;
       }else {
           return sum / count;
       }
   }

   //5.获取全城Eva属性商品品质均加成信息
   public static int cityAvgEva(int at,int bt){
        //获取eva值，求平均，然后加上商品的基础值即可
       Set<Eva> allEvas = EvaManager.getInstance().getAllEvas();
       double evaSum=0;
       int count = 0;
       for (Eva eva : allEvas) {
           if(eva.getAt()==at&&eva.getBt()==bt){
               evaSum+=EvaManager.getInstance().computePercent(eva);
               count++;
           }
       }
       if(count==0)
           return 1;
       else {
           return (int) (evaSum / count);
       }
   }

   //获取全城推广该类型的推广能力平均值
   /* public static int cityAvgPromotionValue(int type){

    }*/


   //6.获取推荐定价
    public static int getRecommendPrice(int at,int bt,int base,double localEvaAdd,int localBrand,int buildingType){
        //公式：推荐定价 = 全城商品销售均价 * (玩家知名度权重 + 玩家品质权重) / (全城知名度权重 + 全城品质权重)

        //1.全城商品销售均价
        int cityItemAvgPrice = getCityItemAvgPrice(at,buildingType);
        //2.玩家知名度权重
        double brandWeight = EvaUtil.getBrandWeight(localBrand, at);
        //3.玩家品质权重
        double qualityWeight = EvaUtil.getItemWeight(localEvaAdd, at, bt, base);
        //4.全城知名度权重
        int cityAvgBrand = cityAvgBrand(at);
        double cityBrandWeight = EvaUtil.getBrandWeight(cityAvgBrand, at);
        //5.全城品质权重
        int cityAvgQuality = cityAvgEva(at, bt);
        double cityQualityWeight = EvaUtil.getItemWeight(cityAvgQuality, at, bt, base);
        return (int) (cityItemAvgPrice * (brandWeight + qualityWeight) / (cityBrandWeight + cityQualityWeight));
    }

    //7.研究所的推荐定价
    public static int getLabRecommendPrice(int at,int bt,int playerSuccessOdds){
        int cityAvgPrice = getCityAvgPriceByType(MetaBuilding.LAB);
        //1.全城xx概率单位定价
        int cityAvgSuccessOdds = getCityAvgSuccessOdds(at, bt);
        int successPrice = cityAvgPrice * cityAvgSuccessOdds;
        //推荐定价
        int recommendPrice = successPrice * playerSuccessOdds;
        return recommendPrice;
    }
    
}
