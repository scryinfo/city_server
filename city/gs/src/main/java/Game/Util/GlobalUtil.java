package Game.Util;

import Game.*;
import Game.Eva.Eva;
import Game.Eva.EvaManager;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaData;
import Game.Meta.MetaGood;
import gs.Gs;

import java.util.*;

public class GlobalUtil {

    /*1.获取全城最大和最小的加成信息（找出全城Eva提升最大的那一个，然后进行计算）*/
    public static Map<String, Eva>  getEvaMaxAndMinValue(int at,int bt){
        Set<Eva> evas = EvaManager.getInstance().getAllEvas();
        Eva maxEva = null;
        Eva minEva=null;
        Map<String, Eva> minOrMaxEva = new HashMap<>();
        int init=0;
        for (Eva eva : evas) {
            if(eva.getAt()==at&&eva.getBt()==bt){
                if(maxEva==null&&minEva==null) {
                    maxEva = eva;
                    minEva = eva;
                }else {
                    if(eva.getLv()>maxEva.getLv()) {
                        maxEva = eva;
                    }
                    if(eva.getLv()<maxEva.getLv()) {
                        minEva = eva;
                    }
                }
            }
        }
        minOrMaxEva.put("max", maxEva);
        minOrMaxEva.put("min", minEva);
        return minOrMaxEva;
    }

    //2.获取指定选项知名度的最大最小信息
    public static Map<String, BrandManager.BrandInfo> getMaxOrMinBrandInfo(int item){
        //1.获取到所有的品牌信息，找出其中v最大的数据
        BrandManager.BrandInfo maxBrand= null;
        BrandManager.BrandInfo minBrand= null;
        HashMap<String, BrandManager.BrandInfo> map=new HashMap<>();
        List<BrandManager.BrandInfo> brandInfos = BrandManager.instance().getAllBrandInfoByItem(item);
        for (BrandManager.BrandInfo brandInfo : brandInfos) {
            if(maxBrand==null&&minBrand==null){
                maxBrand = brandInfo;
                minBrand = brandInfo;
            }else{
                if(brandInfo.getV()>maxBrand.getV()){
                    maxBrand = brandInfo;
                }
                if(brandInfo.getV()<minBrand.getV()){
                    minBrand = brandInfo;
                }
            }
        }if(maxBrand!=null&&minBrand!=null) {
            map.put("max", maxBrand);
            map.put("min", minBrand);
            return map;
        }else
            return null;
    }

    //3.获取商品的全城销售均价(获取所有货架上该商品的销售价格取平均值)(*)
    public static int getCityItemAvgPrice(int goodItem, int type) {
        int sumPrice = 0;
        int count = 0;
        List<Building> buildings = City.instance().getAllBuilding();
        for (Building building : buildings) {
            //判断类型
            if (building.type()!= type||building.outOfBusiness()||!(building instanceof  IShelf))
                continue;
            IShelf shelf = (IShelf) building;
            Map<Item, Integer> saleDetail = shelf.getSaleDetail(goodItem);
            if (saleDetail != null) {
                Integer price = new ArrayList<>(saleDetail.values()).get(0);
                sumPrice += price;
                count++;
            }
        }
        return count == 0 ? 0 : sumPrice / count;
    }

    //4.查询全城建筑中设置的平均价格
    public static int getCityAvgPriceByType(int type) {
        int sumPrice = 0;
        int count = 0;
        List<Building> buildings = City.instance().getAllBuilding();
        for (Building building : buildings) {
            if (building.type() == type && !building.outOfBusiness()) {
               switch (type){
                   case MetaBuilding.RETAIL:
                       RetailShop retailShop = (RetailShop) building;
                       sumPrice += retailShop.getCurPromPricePerHour();
                       count++;
                       break;
                   case MetaBuilding.APARTMENT:
                       Apartment apartment = (Apartment) building;
                       sumPrice += apartment.cost();
                       count++;
                       break;
                   case MetaBuilding.LAB:
                       Laboratory lab = (Laboratory) building;
                       sumPrice += lab.getPricePreTime();
                       count++;
                       break;
                   case MetaBuilding.PUBLIC:
                       PublicFacility facility = (PublicFacility) building;
                       sumPrice += facility.getCurPromPricePerHour();
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

    //5.查询根据类型研究成功几率，依然是建筑的成功平均值+eva的平均提升值
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
        return count == 0 ? 1: (sumBaseOdds / count) * (1 + avgEvaAdd);
    }

   //6.全城商品均知名度 || 全城住宅均知名度
    public static int cityAvgBrand(int item){
       int sum=0;
       int count = 0;
      //获取所有的品牌信息
       List<BrandManager.BrandInfo> allBrand = BrandManager.instance().getAllBrandInfoByItem(item);
       for (BrandManager.BrandInfo brandInfo : allBrand) {
               sum += brandInfo.getV();
               count++;
           //如果是商品，还需要加上基础品牌值
           if(MetaGood.isItem(item)){
               int baseBrand = MetaData.getGood(item).brand;
               sum += baseBrand;
           }
       }
       return  count==0?1: sum / count;
   }

   //7.获取全城Eva属性商品品质均加成信息
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
       return count==0?0: (int) (evaSum / count);
   }

   //8.获取全城推广该类型的推广能力平均值
    public static int cityAvgPromotionAbilityValue(int type){
        int sum=0;
        int count = 0;
        List<Building> buildings = City.instance().getAllBuilding();
        for (Building b: buildings) {
            if(b instanceof PublicFacility&&!b.outOfBusiness()){
                PublicFacility facility = (PublicFacility) b;
                sum+=facility.getAllPromoTypeAbility(type);
                count++;
            }
        }
        return count==0?1:sum / count;
    }

    //9.全城住宅零售店均品质(*)
    public static int getCityApartmentOrRetailShopQuality(int at,int bt){
        //1.计算均加成
        int avgAdd = cityAvgEva(at, bt);
        int quality=0;
        int count = 0;
        //2.获得基本品质
        List<Building> buildings = City.instance().getAllBuilding();
        for (Building building : buildings) {
            if(building.type()==at){
                quality+= building.quality();
                count++;
            }
        }
        return (quality/count) * (1 + avgAdd);
    }

   //10.获取加工厂推荐定价
    public static int getProduceRecommendPrice(int at,int bt,int base,double localEvaAdd,int localBrand,int buildingType){
        //公式：推荐定价 = 全城商品销售均价 * (玩家知名度权重 + 玩家品质权重) / (全城知名度权重 + 全城品质权重)

        //1.全城商品销售均价
        int cityItemAvgPrice = getCityItemAvgPrice(at,buildingType);
        //2.玩家知名度权重
        double brandWeight = EvaUtil.getBrandWeight(localBrand, at);
        //3.玩家品质权重
        int localQuality = (int) (base * (1 + localEvaAdd));
        double qualityWeight = EvaUtil.getItemWeight(localQuality, at, bt, base);
        //4.全城知名度权重
        int cityAvgBrand = cityAvgBrand(at);
        double cityBrandWeight = EvaUtil.getBrandWeight(cityAvgBrand, at);
        //5.全城品质权重
        int cityAvgQuality =(int)(base * (1+ cityAvgEva(at, bt)));
        double cityQualityWeight = EvaUtil.getItemWeight(cityAvgQuality, at, bt, base);
        return (int) (cityItemAvgPrice * (brandWeight + qualityWeight) / (cityBrandWeight + cityQualityWeight));
    }

    //11.研究所的推荐定价
    public static int getLabRecommendPrice(int at,int bt,int playerSuccessOdds){
        //1.均定价
        int cityAvgPrice = getCityAvgPriceByType(MetaBuilding.LAB);//全城发明均定价
        //2..均成功几率
        int cityAvgSuccessOdds = getCityAvgSuccessOdds(at, bt);//全城发明成功几率
        /*单位定价 = 发明均定价 / 均发明成功几率*/
        int unitPrice = cityAvgPrice /cityAvgSuccessOdds;
        //推荐定价=单位定价 * 玩家发明概率
        int recommendPrice = unitPrice * playerSuccessOdds;
        return recommendPrice;
    }
}
