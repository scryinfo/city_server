package Game.Util;

import Game.*;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaData;
import gs.Gs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/*次类用于抽取和封装数据*/
public class ProtoUtil {

    /*获取竞争力信息封装,参数1：玩家对应的类型建筑,参数2：修改前的玩家竞争力，参数2：后改后的玩家竞争力，参数3 建筑类型，参数4 商品id（可选,只要是封装加工厂的，必须要填此参数）*/
    public static List<Gs.EvaResultInfo.Promote> getPromoteList(List<Building> buildings, Map<UUID, Double> old,Map<UUID, Double> news,int type,Integer at){
        List<Gs.EvaResultInfo.Promote> promotes = new ArrayList<>();
        for (Building building : buildings) {
            UUID bid = building.id();
            Double old_competitive = old.get(bid);
            Double new_competitive = news.get(bid);
            if(old_competitive==null||new_competitive==null)
                continue;
            Gs.EvaResultInfo.Promote.Builder promote = Gs.EvaResultInfo.Promote.newBuilder();
            int price=0;
            String name=building.getName();
            switch (type){
                case MetaBuilding.PRODUCE:
                    ProduceDepartment pro = (ProduceDepartment) building;
                    price = pro.getShelf().getSellInfo(at).get(0).price;//售价
                    break;
                case  MetaBuilding.PUBLIC:
                    PublicFacility pub = (PublicFacility) building;
                    price = pub.getCurPromPricePerHour();
                    break;
                case  MetaBuilding.LAB:
                    Laboratory lab = (Laboratory) building;
                    price = lab.getPricePreTime();
                    break;
            }
            promote.setName(name)
                    .setPrice(price)
                    .setOldCompetitiveness(old_competitive)
                    .setNewCompetitiveness(new_competitive);
            promotes.add(promote.build());
        }
        return promotes;
    }

    public static List<Gs.EvaResultInfo.RetailOrApartmentData> getRetailOrApartmentDataList(List<Building> buildings, Map<UUID,List<Integer>> old,Map<UUID,List<Integer>> news,int type){
        List<Gs.EvaResultInfo.RetailOrApartmentData> retailOrApartmentData = new ArrayList<>();
        for (Building building : buildings) {
            UUID bid = building.id();
            List<Integer> oldExpect = old.get(bid);
            List<Integer> newExpect = news.get(bid);
            if(oldExpect==null||newExpect==null)
                continue;
            Gs.EvaResultInfo.RetailOrApartmentData.Builder ra = Gs.EvaResultInfo.RetailOrApartmentData.newBuilder();
            int price=0;
            String name=building.getName();
            switch (type){
                case MetaBuilding.APARTMENT:
                    Apartment apartment = (Apartment) building;
                    price = apartment.cost();//售价
                    break;
                case MetaBuilding.RETAIL:
                    RetailShop retailShop = (RetailShop) building;
                    price=retailShop.getCurPromPricePerHour();
                    break;
            }
            int cityAvgPrice = GlobalUtil.getCityAvgPriceByType(type);//全城定价
            int buildingRich=0;//Todo ：繁荣度
            //建筑获取开放数量
            int opentNum = City.instance().getOpentNumByType(type);
            Gs.EvaResultInfo.RetailOrApartmentData.ExpectSpend.Builder oldExpectSpend = Gs.EvaResultInfo.RetailOrApartmentData.ExpectSpend.newBuilder();
            Gs.EvaResultInfo.RetailOrApartmentData.ExpectSpend.Builder newExpectSpend = Gs.EvaResultInfo.RetailOrApartmentData.ExpectSpend.newBuilder();
            oldExpectSpend.setExpectSpend(oldExpect.get(0)).setCityExpectSpend(oldExpect.get(1));
            newExpectSpend.setExpectSpend(newExpect.get(0)).setCityExpectSpend(newExpect.get(1));
            ra.setName(name)
                    .setPrice(price)
                    .setCityPrice(cityAvgPrice)
                    .setBuildingRich(buildingRich)
                    .setOpenNum(opentNum)
                    .setOldExpectSpend(oldExpectSpend)
                    .setNewExpectSpend(oldExpectSpend);
            retailOrApartmentData.add(ra.build());
        }
        return retailOrApartmentData;
    }
}

