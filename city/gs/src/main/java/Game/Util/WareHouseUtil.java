package Game.Util;

import Game.City;
import Game.WareHouse;
import Game.WareHouseRenter;
import com.google.protobuf.ByteString;
import gs.Gs;

import java.util.List;
import java.util.UUID;

public  class WareHouseUtil {
    //抽取添加所有的商品(添加到商品列表中)
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

    //抽取，获取指定租户信息
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