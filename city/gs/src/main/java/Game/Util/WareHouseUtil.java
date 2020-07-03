package Game.Util;

import Game.City;
import Game.WareHouse;
import Game.WareHouseRenter;
import com.google.protobuf.ByteString;
import gs.Gs;

import java.util.List;
import java.util.UUID;

public  class WareHouseUtil {
    //Extract and add all products (add to the product list)
    public static void addGoodInfo(List<Gs.Shelf.Content> goods, List<Gs.GoodInfo> shelfItemList, ByteString bid, Long orderId) {
        Gs.GoodInfo.Builder goodInfo = Gs.GoodInfo.newBuilder();
        goods.forEach(p -> {
            Gs.Item.Builder item = Gs.Item.newBuilder();
            int price = p.getPrice();
            item.setN(p.getN());
            item.setKey(p.getK());
            goodInfo.setItem(item);
            goodInfo.setPrice(price);
            goodInfo.setBuildingId(bid);
            if(orderId!=null){
                goodInfo.setOrderid(orderId);
            }
            shelfItemList.add(goodInfo.build());
        });
    }

    //Extract, get the information of the specified tenant
    public static WareHouseRenter getWareRenter(UUID bid, Long orderId){
        WareHouse building = (WareHouse) City.instance().getBuilding(bid);
        WareHouseRenter renter = null;
        for (WareHouseRenter r : building.getRenters()) {
            if(r.getOrderId()==orderId&&null!=orderId){
                renter = r;
                break;
            }
        }
        return renter;
    }
}