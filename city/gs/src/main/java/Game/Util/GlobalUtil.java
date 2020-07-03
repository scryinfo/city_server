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

    /*1.Obtain the largest and smallest bonus information in the city (find out the one with the largest increase in Eva in the city, and then calculate)*/
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
        return minOrMaxEva;
    }

    //2.Get the maximum and minimum information of the popularity of the specified option
    public static Map<String, BrandManager.BrandInfo> getMaxOrMinBrandInfo(int item){
        //If it is a building, it needs to be based on item *100
        if(MetaBuilding.isBuildingByBaseType(item)){
            item *= 100;
        }
        //1.Get all the brand information and find the data with the largest v
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

    //Get the maximum and minimum visibility (that is, the highest and lowest level of the screening process)
    public static Map<String,Double> getMaxOrMinBrandValue(int item){
        HashMap<String,Double> map=new HashMap<>();
        //Count the number of players
        /*Get the highest and lowest Eva brand bonus information in the city*/
        EvaKey key = new EvaKey(item,Gs.Eva.Btype.Brand_VALUE);
        Set<Eva> evas = EvaManager.getInstance().typeEvaMap.get(key);
        if(evas!=null){
            // Filter out the highest level
            List<Integer> evaLv = new ArrayList<>();
            for (Eva eva : evas) {
                evaLv.add(eva.getLv());
            }
            Integer maxLv = Collections.max(evaLv);/*Highest level*/
            double maxRation = maxLv > 0 ? MetaData.getAllExperiences().get(maxLv).p / 100000d : 0;
            // Convert to boost ratio
            Integer minLv = Collections.min(evaLv);/*Lowest level*/
            double minRation = minLv > 0 ? MetaData.getAllExperiences().get(minLv).p / 100000d : 0;
            map.put("max", maxRation);
            map.put("min", minRation);
        }else{
            map.put("max",0.0);
            map.put("min",0.0);
        }


        return map;
    }

    //3.Get the citywide average sales price of the product (take the average sales price of the product on all shelves) (*)
    public static int getCityItemAvgPrice(int goodItem, int type) {
        int sumPrice = 0;
        int count = 0;
        Set<Building> buildings = City.instance().typeBuilding.getOrDefault(type,new HashSet<>());
        for (Building building : buildings) {
            //Judgment type
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

    //4.Query the average price set in buildings across the city
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

    //5.Query the success rate of city-wide research according to type, which is still the average value of building success + the average value of eva
    public static int getCityAvgSuccessOdds(int at,int bt){
        int sumBaseOdds = 0;
        int count=0;
        Set<Building> buildings = City.instance().typeBuilding.getOrDefault(MetaBuilding.LAB,new HashSet<>());
        for (Building building : buildings) {
            if (building instanceof Laboratory&&!building.outOfBusiness()) {
                Laboratory lab = (Laboratory) building;
                if(bt==Gs.Eva.Btype.InventionUpgrade.getNumber()){//Average chance of successful invention
                    sumBaseOdds+=lab.getGoodProb();
                    count++;
                }else {//Average Chance of success
                    sumBaseOdds+=lab.getEvaProb();
                    count++;
                }
            }
        }
        //Calculate average Eva bonus information for invention or research
        int avgEvaAdd = cityAvgEva(at, bt);
        return count == 0 ? 1: (sumBaseOdds / count) * (1 + avgEvaAdd);
    }

   //6.Awareness across the city || Awareness across the city
    public static int cityAvgBrand(int item){
       int sum=0;
       int count = 0;
      //Get all brand information
       List<BrandManager.BrandInfo> allBrand = BrandManager.instance().getAllBrandInfoByItem(item);
       for (BrandManager.BrandInfo brandInfo : allBrand) {
               sum += brandInfo.getV();
               count++;
           //If it is a commodity, you need to add the basic brand value
           if(MetaGood.isItem(item)){
               int baseBrand = MetaData.getGood(item).brand;
               sum += baseBrand;
           }
       }
       return  count==0?0: sum / count;
   }

   //7.Get information on the quality of all Eva attributes in the city
    public static int cityAvgEva(int at,int bt){
        //Get the eva value, average it, then add the basic value of the product
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

   //8.Get the average value of the city's ability to promote this type of promotion
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

    //9.Average quality of residential retail stores across the city (*)
    public static int getCityApartmentOrRetailShopQuality(int at,int bt,int buildingType){
        //1.Calculation bonus
        int avgAdd = cityAvgEva(at, bt);
        int quality=0;
        int count = 0;
        //2.Get basic quality
        Set<Building> buildings = City.instance().typeBuilding.getOrDefault(buildingType,new HashSet<>());
        for (Building building : buildings) {
            if(building.type()==buildingType){
                quality+= building.quality();
                count++;
            }
        }
        return  count==0?0:(quality/count) * (1 + avgAdd);
    }

   //10.Get recommended pricing for processing plants
    public static int getProduceRecommendPrice(int at,int bt,int qtyBase,double localEvaAdd,int localBrand,int buildingType){
        //Formula: Recommended pricing = city-wide average sales price of goods * (player popularity weight + player quality weight) / (city awareness weight + city quality weight)

        //1.Average price of goods sold across the city
        int cityItemAvgPrice = getCityItemAvgPrice(at,buildingType);
        //2.Player popularity weight
        double brandWeight = CompeteAndExpectUtil.getBrandWeight(localBrand, at);
        //3.Player quality weight
        double localQuality = (qtyBase * (1 + localEvaAdd));
        double qualityWeight = CompeteAndExpectUtil.getItemWeight(localQuality, at, bt, qtyBase);
        //4.City-wide visibility weight
        int cityAvgBrand = cityAvgBrand(at);
        double cityBrandWeight = CompeteAndExpectUtil.getBrandWeight(cityAvgBrand, at);
        //5.Quality weight across the city
        int cityAvgQuality =(int)(qtyBase * (1+ cityAvgEva(at, bt)));
        double cityQualityWeight = CompeteAndExpectUtil.getItemWeight(cityAvgQuality, at, bt, qtyBase);
        return (int) (cityItemAvgPrice * (brandWeight + qualityWeight) / (cityBrandWeight + cityQualityWeight));
    }

    //11.Institute's recommended pricing
    public static int getLabRecommendPrice(int at,int bt,int playerSuccessOdds){
        //1.Average pricing
        int cityAvgPrice = getCityAvgPriceByType(MetaBuilding.LAB);//All prices for inventions across the city
        //2.All chances of success
        int cityAvgSuccessOdds = getCityAvgSuccessOdds(at, bt);//Probability of invention success across the city
        /*Unit pricing = average pricing for inventions / average probability of successful inventions*/
        int unitPrice = cityAvgPrice /cityAvgSuccessOdds;
        //Recommended pricing = unit pricing * probability of player invention
        int recommendPrice = unitPrice * playerSuccessOdds;
        return recommendPrice;
    }


    //Score extraction ======
    //Awareness score (requires parameters) Awareness score = (current popularity-the lowest visibility in the city) / (highest visibility in the city-lowest visibility in the city)
    public static double getBrandScore(double localBrand,int type){
        //1.Highest and lowest visibility
        Map<Integer, Integer> maxAndMinBrand = BuildingUtil.instance().getMaxAndMinBrand(type);
        int minBrand=maxAndMinBrand.get(BuildingUtil.MIN);//Lowest visibility
        int maxBrand=maxAndMinBrand.get(BuildingUtil.MAX);//Highest visibility
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

    //Get the building's quality score Quality score = (current quality-lowest quality in the city) / (highest quality in the city-lowest quality in the city)
    public static double getBuildingQtyScore(double localQuality,int buildingType){
        //Get the highest and lowest quality in the city (in existing buildings)
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

    //Get the quality score of the product (parameter 1 current total quality value, parameter 2 product id parameter 3 basic quality value of the product)
    public static double getGoodQtyScore(double localQuality,int goodId,int baseQty){
        Map<String, Eva> cityQtyMap = GlobalUtil.getEvaMaxAndMinValue(goodId, Gs.Eva.Btype.Quality_VALUE);
        Eva maxEva = cityQtyMap.get("max");//The largest Eva in the city
        Eva minEva = cityQtyMap.get("min");//The smallest Eva in the city
        double maxAdd=EvaManager.getInstance().computePercent(maxEva);
        double minAdd=EvaManager.getInstance().computePercent(minEva);
        double maxQty = baseQty* (1 + maxAdd);
        double minQty = baseQty* (1 + minAdd);
        double qtyScore=1;//The minimum score is 1 by default
        if(localQuality==minQty){
            qtyScore=1;
            if(localQuality==maxQty){//Both the smallest and the largest, rated 100
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
        //R&D capability = (invention success rate *2 + research success rate)/2
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
        //Citywide promotion capacity = all different types of promotion capacity and / 4
        return sumAbilitys / count / 4;
    }
}
