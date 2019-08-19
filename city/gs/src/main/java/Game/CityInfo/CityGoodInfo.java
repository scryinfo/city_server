package Game.CityInfo;

import Game.GameDb;
import Game.GameSession;
import Game.Meta.MetaData;
import Game.Player;
import ga.Ga;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CityGoodInfo {
    public List<Integer> getCityInventGoodByType(List<Integer> types){
        Set<Integer> inventGoodList = new HashSet<>();
        /*1.护球全程所有商品*/
        Set<Integer> allGoodId = MetaData.getAllGoodId();
        /*2.获取其中一个玩家的所有可用商品（并移除掉）*/
        Player player = GameDb.queryPlayerForOne();
        if(player==null){
            return null;
        }
        Set<Integer> ids = player.itemIds();
        allGoodId.removeAll(ids);
        /*剩余的都是没有被发明出来的*/
        types.forEach(tp->{
            /*tp都是5位*/
            for (Integer itemId : allGoodId) {
            }
        });

        return null;
    }
}
