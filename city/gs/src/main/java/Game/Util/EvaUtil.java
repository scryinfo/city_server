package Game.Util;

import Game.*;
import Game.Eva.Eva;
import Game.Eva.EvaManager;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaData;
import gs.Gs;

import java.util.*;

public class EvaUtil {


    /*1.获取全城最大和最小的加成信息（找出全城Eva提升最大的那一个，然后进行计算）*/
    public static Map<String, Eva>  getEvaMaxValue(int at,int bt){
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


    //3.计算品质权重(品质权重等等计算)(arg1:当前eva的加成值，arg2:a类型，arg3：b类型,arg4：基础值)
    public static double getItemWeight(double addEva,int at,int bt,int base){
       // 权重 = 当前值 / 全城最大 > 当前值 /全城最低 ?  当前值 / 全城最大 : 当前值 /全城最低
        Map<String, Eva> map = getEvaMaxValue(at,bt);
        if(map==null)
            return 1;
        Eva maxEva = map.get("max");//全城最大Eva
        Eva minEva = map.get("min");//全城最小Eva
        double local = base * (1 + addEva);//当前值
        double maxValue=base * (1 + EvaManager.getInstance().computePercent(maxEva));//全城最大
        double minValue=base * (1 + EvaManager.getInstance().computePercent(minEva));//全城最小
        double weight = (local / maxValue)> (local / minValue) ? local / maxValue : local / minValue;
        return weight;
    }


    /*4.计算知名度权重(全城知名度也在这儿计算)*/
    public static double getBrandWeight(int localBrand,int item){
        //1.获取到全城该属性的最大最小的知名度(如果都为null，那么获取默认的)
        Map<String, BrandManager.BrandInfo> map = getMaxOrMinBrandInfo(item);
        int minBrand=1;
        int maxBrand=1;
        if(map!=null){
            minBrand = map.get("min").getV();
            maxBrand = map.get("max").getV();
        }
        //2.权重
        double weight = (localBrand / maxBrand)> (localBrand / minBrand) ? localBrand / maxBrand : localBrand / minBrand;
        return weight;
    }

    //=======================================================================

    //1.抽取获取加工厂的竞争力
    public static List<Gs.EvaResultInfo.Promote> getProducePromoteInfo(List<Building> buildings,Gs.Eva msEva, Eva newEva) {
        List<Gs.EvaResultInfo.Promote> promotes = new ArrayList<>();
        int at = newEva.getAt();
        int bt = newEva.getBt();
        for (Building b : buildings) {
            Gs.EvaResultInfo.Promote.Builder promote = Gs.EvaResultInfo.Promote.newBuilder();
            ProduceDepartment pro = (ProduceDepartment) b;
            //1.1判断是否该有该上架的商品
            if (!pro.getShelf().has(at))
                continue;
            int price = pro.getShelf().getSellInfo(at).get(0).price;//售价
            int base = MetaData.getGood(at).quality;//商品的品质基础值
            //获取品牌信息
            int brandValue = BrandManager.instance().getBrand(b.ownerId(), at).getV();
            //1.提升前的eva加成信息
            Eva oldEva = new Eva();
            oldEva.setLv(msEva.getLv());
            double oldAdd = EvaManager.getInstance().computePercent(oldEva);
            int old_recommendPrice = GlobalUtil.getRecommendPrice(at, bt, base, oldAdd, brandValue, MetaBuilding.PRODUCE);//推荐价格
            int old_competitive = (int) Math.ceil(old_recommendPrice / price * 100);//老的竞争力
            //2.提升后的eva加成信息
            double newAdd = EvaManager.getInstance().computePercent(newEva);
            int new_recommendPrice = GlobalUtil.getRecommendPrice(at, bt, base, newAdd, brandValue, MetaBuilding.PRODUCE);//推荐价格
            int new_competitive = (int) Math.ceil(new_recommendPrice / price * 100);//新的竞争力
            promote.setName(pro.getName())
                    .setPrice(price)
                    .setBCompetitiveness(old_competitive)
                    .setECompetitiveness(new_competitive);
            promotes.add(promote.build());
        }
        return promotes;
    }

    //2.抽取的推广公司的竞争力
    public  static List<Gs.EvaResultInfo.Promote> getPubPromoteInfo(List<Building> buildings,Gs.Eva msEva, Eva newEva) {
        List<Gs.EvaResultInfo.Promote> promotes = new ArrayList<>();
        //1.全城推广单位定价
        int cityAvgprice = GlobalUtil.getCityAvgPriceByType(MetaBuilding.PUBLIC);
        int cityAvgAbility = GlobalUtil.cityAvgEva(newEva.getAt(), newEva.getBt());
        int cityAbilityPrice = cityAvgprice/cityAvgAbility;

        for (Building b : buildings) {
            if(b.outOfBusiness())
                continue;
            Gs.EvaResultInfo.Promote.Builder promote = Gs.EvaResultInfo.Promote.newBuilder();
            PublicFacility pub = (PublicFacility) b;
            int price = pub.getCurPromPricePerHour();
            //1.(提升前)
            Eva oldEva = new Eva();
            oldEva.setLv(msEva.getLv());
            double oldAdd = EvaManager.getInstance().computePercent(oldEva);
            double old_promoAbility = pub.getCurPromoAbility()*(1+oldAdd);
            double old_recommendPrice = cityAbilityPrice * old_promoAbility;
            int old_competitive = (int) Math.ceil(old_recommendPrice / price * 100);

            //2.提升后
            double newAdd = EvaManager.getInstance().computePercent(newEva);
            double new_promoAbility = pub.getCurPromoAbility()*(1+newAdd);
            double new_recommendPrice = cityAbilityPrice * new_promoAbility;
            int new_competitive = (int) Math.ceil(new_recommendPrice / price * 100);
            promote.setName(pub.getName())
                    .setPrice(price)
                    .setBCompetitiveness(old_competitive)
                    .setECompetitiveness(new_competitive);
            promotes.add(promote.build());
        }
        return promotes;
    }

    //3.抽取的研究所公司的竞争力
    public  static List<Gs.EvaResultInfo.Promote> getLabPromoteInfo(List<Building> buildings,Gs.Eva msEva, Eva newEva) {
        List<Gs.EvaResultInfo.Promote> promotes = new ArrayList<>();
        for (Building building : buildings) {
            if(building.outOfBusiness())
                continue;
            Laboratory lab= (Laboratory) building;
            int playerSuccessOdds = 0;
            Gs.EvaResultInfo.Promote.Builder promote = Gs.EvaResultInfo.Promote.newBuilder();
            int price = lab.getPricePreTime();
            //1.提升前
            //提升前的建筑成功几率
            Eva oldEva = new Eva();
            oldEva.setLv(msEva.getLv());
            double oldAdd = EvaManager.getInstance().computePercent(oldEva);
            if(msEva.getBt().equals(Gs.Eva.Btype.InventionUpgrade)) {//升级的是发明成功几率
                playerSuccessOdds = (int) (lab.getGoodProb() * (1 + oldAdd));
            }else{//研究Eva的成功几率
                playerSuccessOdds = (int) (lab.getEvaProb() * (1 + oldAdd));
            }

            int old_labRecommendPrice = GlobalUtil.getLabRecommendPrice(newEva.getAt(), newEva.getBt(),playerSuccessOdds); //推荐价格
            int old_competitive = (int) Math.ceil(old_labRecommendPrice / price * 100);//竞争力

            //2.提升后
            double newAdd = EvaManager.getInstance().computePercent(newEva);
            if(msEva.getBt().equals(Gs.Eva.Btype.InventionUpgrade)) {//发明成功几率
                playerSuccessOdds = (int) (lab.getGoodProb() * (1 + newAdd));
            }else{
                playerSuccessOdds = (int) (lab.getEvaProb() * (1 + newAdd));
            }
            int new_labRecommendPrice = GlobalUtil.getLabRecommendPrice(newEva.getAt(), newEva.getBt(), playerSuccessOdds);//推荐价格
            int new_competitive = (int) Math.ceil(new_labRecommendPrice / price * 100);//竞争力
            promote.setName(lab.getName())
                    .setPrice(price)
                    .setBCompetitiveness(old_competitive)
                    .setECompetitiveness(new_competitive);
            promotes.add(promote.build());
        }
        return promotes;
    }

}
