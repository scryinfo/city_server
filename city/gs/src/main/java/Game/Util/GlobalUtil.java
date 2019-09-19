package Game.Util;

import Game.*;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaData;
import Game.Meta.MetaGood;
import gs.Gs;

import java.util.*;

public class GlobalUtil {

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
               }
            }
        }
        if(count!=0)
            return sumPrice / count;
        else
            return 0;
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
                sumPrice += apartment.cost();
                count++;
            }
        }
        list.add((double) (count == 0 ? 0 : sumPrice / count));
        list.add(count == 0 ? 1 : sumScore / count);
        return list;
    }
}
