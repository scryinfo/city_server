package Game.Util;

import Game.*;
import Game.Eva.Eva;
import Game.Eva.EvaKey;
import Game.Eva.EvaManager;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaData;
import Game.Meta.MetaGood;
import gs.Gs;

import java.util.*;

public class GlobalUtil {

    /*1.获取全城最大和最小的加成信息（找出全城Eva提升最大的那一个，然后进行计算）*/
    public static Map<String, Eva>  getEvaMaxAndMinValue(int at,int bt){
        EvaKey key = new EvaKey(at,bt);
        Set<Eva> evas = EvaManager.getInstance().typeEvaMap.getOrDefault(key,new HashSet<>());
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
        return minOrMaxEva==null||maxEva==null?null: minOrMaxEva;
    }

    //2.获取指定选项知名度的最大最小信息
    public static Map<String, BrandManager.BrandInfo> getMaxOrMinBrandInfo(int item){
        //如果是建筑，需要再item基础上*100
        if(MetaBuilding.isBuildingByBaseType(item)){
            item *= 100;
        }
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

    //获取最大最小的知名度（就是帅选全程最高最低等级）
    public static Map<String,Double> getMaxOrMinBrandValue(int item){
        HashMap<String,Double> map=new HashMap<>();
        //统计玩家人数
        /*获取全城最高和最低的Eva品牌加成信息*/
        EvaKey key = new EvaKey(item,Gs.Eva.Btype.Brand_VALUE);
        Set<Eva> evas = EvaManager.getInstance().typeEvaMap.get(key);
        // 筛选出等级最大的
        List<Integer> evaLv = new ArrayList<>();
        for (Eva eva : evas) {
            evaLv.add(eva.getLv());
        }
        Integer maxLv = Collections.max(evaLv);/*最高等级*/
        double maxRation = maxLv > 0 ? MetaData.getAllExperiences().get(maxLv).p / 100000d : 0;
        // 转换为提升比例
        Integer minLv = Collections.min(evaLv);/*最低等级*/
        double minRation = minLv > 0 ? MetaData.getAllExperiences().get(minLv).p / 100000d : 0;
        map.put("max", maxRation);
        map.put("min", minRation);
        return map;
    }

    //3.获取商品的全城销售均价(获取所有货架上该商品的销售价格取平均值)(*)
    public static int getCityItemAvgPrice(int goodItem, int type) {
        int sumPrice = 0;
        int count = 0;
        Set<Building> buildings = City.instance().typeBuilding.getOrDefault(type,new HashSet<>());
        for (Building building : buildings) {
            //判断类型
            if (building.outOfBusiness()||!(building instanceof  IShelf))
                continue;
            IShelf shelf = (IShelf) building;
            Map<Item, Integer> saleDetail = shelf.getSaleDetail(goodItem);
            if (saleDetail!=null&&saleDetail.size()>0) {
                Integer price = saleDetail.values().stream().findFirst().orElse(0);
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
        Set<Building> buildings = City.instance().typeBuilding.getOrDefault(type,new HashSet<>());
        for (Building building : buildings) {
            if (!building.outOfBusiness()) {
               switch (type){
                  /* case MetaBuilding.RETAIL:
                       RetailShop retailShop = (RetailShop) building;
                       int retailPrice = retailShop.getCurPromPricePerHour();
                       sumPrice += retailPrice;
                       count++;
                       break;*/
                   case MetaBuilding.APARTMENT:
                       Apartment apartment = (Apartment) building;
                       int aparPrice = apartment.cost();
                       sumPrice += aparPrice;
                       count++;
                       break;
                  /* case MetaBuilding.LAB:
                       Laboratory lab = (Laboratory) building;
                       int labPrice = lab.getPricePreTime();
                       sumPrice += labPrice;
                       count++;
                       break;
                   case MetaBuilding.PUBLIC:
                       PublicFacility facility = (PublicFacility) building;
                       int facPrice = facility.getCurPromPricePerHour();
                       sumPrice += facPrice;
                       count++;
                       break;*/
               }
            }
        }
        if(count!=0)
            return sumPrice / count;
        else
            return 0;
    }

    //5.查询根据类型查询全城研究成功几率，依然是建筑的成功平均值+eva的平均提升值
    public static int getCityAvgSuccessOdds(int at,int bt){
        int sumBaseOdds = 0;
        int count=0;
        Set<Building> buildings = City.instance().typeBuilding.getOrDefault(MetaBuilding.LAB,new HashSet<>());
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
       return  count==0?0: sum / count;
   }

   //7.获取全城Eva属性商品品质均加成信息
    public static int cityAvgEva(int at,int bt){
        //获取eva值，求平均，然后加上商品的基础值即可
       EvaKey key = new EvaKey(at, bt);
       Set<Eva> allEvas = EvaManager.getInstance().typeEvaMap.get(key);
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
    public static int cityAvgPromotionAbilityValue(int type,int buildingType){
        int sum=0;
        int count = 0;
        Set<Building> buildings = City.instance().typeBuilding.getOrDefault(buildingType,new HashSet<>());
        for (Building b: buildings) {
            if(b instanceof PublicFacility&&!b.outOfBusiness()){
                PublicFacility facility = (PublicFacility) b;
                sum+=facility.getLocalPromoAbility(type);
                count++;
            }
        }
        return count==0?1:sum / count;
    }

    //9.全城住宅零售店均品质(*)
    public static int getCityApartmentOrRetailShopQuality(int at,int bt,int buildingType){
        //1.计算均加成
        int avgAdd = cityAvgEva(at, bt);
        int quality=0;
        int count = 0;
        //2.获得基本品质
        Set<Building> buildings = City.instance().typeBuilding.getOrDefault(buildingType,new HashSet<>());
        for (Building building : buildings) {
            if(building.type()==buildingType){
                quality+= building.quality();
                count++;
            }
        }
        return  count==0?0:(quality/count) * (1 + avgAdd);
    }

   //10.获取加工厂推荐定价
    public static int getProduceRecommendPrice(int at,int bt,int qtyBase,double localEvaAdd,int localBrand,int buildingType){
        //公式：推荐定价 = 全城商品销售均价 * (玩家知名度权重 + 玩家品质权重) / (全城知名度权重 + 全城品质权重)

        //1.全城商品销售均价
        int cityItemAvgPrice = getCityItemAvgPrice(at,buildingType);
        //2.玩家知名度权重
        double brandWeight = CompeteAndExpectUtil.getBrandWeight(localBrand, at);
        //3.玩家品质权重
        double localQuality = (qtyBase * (1 + localEvaAdd));
        double qualityWeight = CompeteAndExpectUtil.getItemWeight(localQuality, at, bt, qtyBase);
        //4.全城知名度权重
        int cityAvgBrand = cityAvgBrand(at);
        double cityBrandWeight = CompeteAndExpectUtil.getBrandWeight(cityAvgBrand, at);
        //5.全城品质权重
        int cityAvgQuality =(int)(qtyBase * (1+ cityAvgEva(at, bt)));
        double cityQualityWeight = CompeteAndExpectUtil.getItemWeight(cityAvgQuality, at, bt, qtyBase);
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


    //评分抽取======
    //知名度评分(需要参数)  知名度评分 = (当前知名度 - 全城最低知名度) / (全城最高知名度 - 全城最低知名度)
    public static double getBrandScore(double localBrand,int type){
        //1.最高最低知名度
        Map<Integer, Integer> maxAndMinBrand = BuildingUtil.instance().getMaxAndMinBrand(type);
        int minBrand=maxAndMinBrand.get(BuildingUtil.MIN);//最低知名度
        int maxBrand=maxAndMinBrand.get(BuildingUtil.MAX);//最高知名度
        double brandScore =1;
        if(localBrand==minBrand){
            brandScore=1;
            if(localBrand==maxBrand){
                brandScore=100;
            }
        }else if(localBrand>minBrand&&maxBrand!=minBrand) {
            double local = localBrand - minBrand;
            int max=maxBrand - minBrand;
            brandScore = Math.ceil((local/max)*100);
        }
        return brandScore;
    }

    //获取建筑的品质评分  品质评分 = (当前品质 - 全城最低品质) / (全城最高品质 - 全城最低品质)
    public static double getBuildingQtyScore(double localQuality,int buildingType){
        //获取全城最高最低品质（已存在的建筑中）
        Map<Integer, Double> maxOrMinQty = BuildingUtil.instance().getMaxOrMinQty(buildingType);
        double maxQty = maxOrMinQty.get(BuildingUtil.MAX);
        double minQty = maxOrMinQty.get(BuildingUtil.MIN);
        double qtyScore=1;
        if(localQuality==minQty){
            qtyScore=1;
            if(localQuality==maxQty){
                qtyScore=100;
            }
        }else if(localQuality>minQty) {
            double result = ((localQuality - minQty) / (maxQty - minQty)) * 100;
            qtyScore = Math.ceil(result);
        }
        return qtyScore;
    }

    //获取商品的品质评分(参数1 当前品质总值，参数2 商品id 参数3 商品的基础品质值)
    public static double getGoodQtyScore(double localQuality,int goodId,int baseQty){
        Map<String, Eva> cityQtyMap = GlobalUtil.getEvaMaxAndMinValue(goodId, Gs.Eva.Btype.Quality_VALUE);
        Eva maxEva = cityQtyMap.get("max");//全城最大Eva
        Eva minEva = cityQtyMap.get("min");//全城最小Eva
        double maxAdd=EvaManager.getInstance().computePercent(maxEva);
        double minAdd=EvaManager.getInstance().computePercent(minEva);
        double maxQty = baseQty* (1 + maxAdd);
        double minQty = baseQty* (1 + minAdd);
        double qtyScore=1;//最小评分默认为1
        if(localQuality==minQty){
            qtyScore=1;
            if(localQuality==maxQty){//既是最小也是最大，评分100
                qtyScore=100;
            }
        }else if(localQuality>minQty) {
            qtyScore = Math.ceil(((localQuality - minQty) / (maxQty - minQty))*100);
        }
        return qtyScore;
    }

    public static List<Double> getproduceInfo(int itemId) {
        List<Double> list = new ArrayList<>();
        int sumPrice = 0;
        double sumScore = 0;
        int count = 0;
        Collection<Building> allBuilding =  City.instance().typeBuilding.getOrDefault(MetaBuilding.PRODUCE,new HashSet<>());
        for (Building b : allBuilding) {
            ProduceDepartment department = (ProduceDepartment) b;
            Map<Item, Integer> saleDetail = department.getSaleDetail(itemId);
            for (Item item : saleDetail.keySet()) {
                sumPrice += saleDetail.get(item);
                double brandScore = getBrandScore(item.getKey().getTotalBrand(), itemId);
                double goodQtyScore = getGoodQtyScore(item.getKey().getTotalQty(), itemId, MetaData.getGoodQuality(itemId));
                sumScore += (brandScore + goodQtyScore) / 2;
                count++;
            }
        }
        list.add((double) (count == 0 ? 0 : sumPrice / count));
        list.add(count == 0 ? 1 : sumScore / count);
        return list;
    }

    public static List<Double> getRetailGoodInfo(int itemId) {
        List<Double> list = new ArrayList<>();
        int sumPrice = 0;
        double sumGoodScore = 0;
        int count = 0;
        Collection<Building> allBuilding = City.instance().typeBuilding.getOrDefault(MetaBuilding.RETAIL, new HashSet<>());
        for (Building b : allBuilding) {
            if (!b.outOfBusiness()) {
                RetailShop retailShop = (RetailShop) b;
                Map<Item, Integer> saleDetail = retailShop.getSaleDetail(itemId);
                for (Item item : saleDetail.keySet()) {
                    sumPrice += saleDetail.get(item);
                    double brandScore = getBrandScore(item.getKey().getTotalBrand(), itemId);
                    double goodQtyScore = getGoodQtyScore(item.getKey().getTotalQty(), itemId, MetaData.getGoodQuality(itemId));
                    sumGoodScore += (brandScore + goodQtyScore) / 2;
                    count++;
                }

            }
        }
        list.add((double) (count == 0 ? 0 : sumPrice / count));
        list.add(count == 0 ? 1 : sumGoodScore / count);
        return list;
    }

    public static double getRetailInfo() {
        double sumRetailScore = 0;
        int count = 0;
        Collection<Building> allBuilding = City.instance().typeBuilding.getOrDefault(MetaBuilding.RETAIL, new HashSet<>());
        for (Building b : allBuilding) {
            if (!b.outOfBusiness()) {
                RetailShop retailShop = (RetailShop) b;
                double brandScore = getBrandScore(retailShop.getTotalBrand(), retailShop.type());
                double retailScore = getBuildingQtyScore(retailShop.getTotalQty(), retailShop.type());
                sumRetailScore += (brandScore + retailScore) / 2;
                count++;
            }

        }
        return count == 0 ? 1 : sumRetailScore / count;
    }

    public static double getMaterialInfo(int itemId) {
        double sumPrice = 0;
        int count = 0;
        Collection<Building> allBuilding = City.instance().typeBuilding.getOrDefault(MetaBuilding.MATERIAL, new HashSet<>());
        for (Building b : allBuilding) {
            if (!b.outOfBusiness()) {
                MaterialFactory mf = (MaterialFactory) b;
                Map<Item, Integer> saleInfo = mf.getSaleDetail(itemId);
                if (saleInfo != null && saleInfo.size() != 0) {
                    sumPrice += new ArrayList<>(saleInfo.values()).get(0);
                    count++;
                }
            }
        }
        return count == 0 ? 0 : sumPrice / count;
    }

    public static List<Double> getApartmentInfo() {
        List<Double> list = new ArrayList<>();
        int sumPrice = 0;
        int count = 0;
        double sumScore = 0;
        Collection<Building> allBuilding =  City.instance().typeBuilding.getOrDefault(MetaBuilding.APARTMENT,new HashSet<>());
        for (Building b : allBuilding) {
            Apartment apartment = (Apartment) b;
            if (!apartment.outOfBusiness()) {
                sumScore += GlobalUtil.getBuildingQtyScore(apartment.getTotalQty(), apartment.type());
                sumPrice += apartment.cost();
                count++;
            }
        }
        list.add((double) (count == 0 ? 0 : sumPrice / count));
        list.add(count == 0 ? 1 : sumScore / count);
        return list;
    }

    public static double getLaboratoryInfo() {
        double sumEvaProb = 0;
        double sumGoodProb = 0;
        int count = 0;
        Collection<Building> allBuilding = City.instance().typeBuilding.getOrDefault(MetaBuilding.LAB, new HashSet<>());
        for (Building b : allBuilding) {
            if (!b.outOfBusiness()) {
                Laboratory laboratory = (Laboratory) b;
                Map<Integer, Double> prob = laboratory.getTotalSuccessProb();
                sumGoodProb += prob.get(Gs.Eva.Btype.InventionUpgrade_VALUE);
                sumEvaProb += prob.get(Gs.Eva.Btype.EvaUpgrade_VALUE);
                count++;
            }
        }
        //研发能力 = (发明成功率 *2 + 研究成功率)/2
        return (sumGoodProb * 2 + sumEvaProb) / 2 / count;
    }
    public static double getPromotionInfo() {
        int count = 0;
        double sumAbilitys = 0;
        Set<Integer> proIds = MetaData.getAllBuildingTech(MetaBuilding.PUBLIC);
        Collection<Building> allBuilding = City.instance().typeBuilding.getOrDefault(MetaBuilding.PUBLIC, new HashSet<>());
        for (Building b : allBuilding) {
            if (!b.outOfBusiness()) {
                PublicFacility facility = (PublicFacility) b;
                for (Integer typeId : proIds) {
                    sumAbilitys += facility.getLocalPromoAbility(typeId);
                }
                count++;
            }
        }
        //全城推广能力 = 所有不同类型推广能力和 / 4
        return sumAbilitys / count / 4;
    }
}
