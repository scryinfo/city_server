package Game.CityInfo;

import Game.GameDb;
import Game.Meta.GoodFormula;
import Game.Meta.MetaData;
import Game.Player;
import Game.Prob;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CityGoodInfo {
    /*获取可发明的商品列表*/
    public static Set<Integer> getCityInventGoodByType(List<Integer> types){
        Set<Integer> inventGoodList = new HashSet<>();
        /*1.全城所有商品*/
        Set<Integer> allGoodId = new HashSet<>();
        allGoodId.addAll(MetaData.getAllGoodId());
        /*2.获取其中一个玩家的所有可用商品（并移除掉）*/
        Player player = GameDb.queryPlayerForOne();
        if(player==null){
            return null;
        }
        Set<Integer> ids = player.itemIds();
        allGoodId.removeAll(ids);
        for (Integer type : types) {
            /*tp都是5位*/
            for (Integer itemId : allGoodId) {
                int itemType = itemId /100;
                if(itemType==type) {
                    inventGoodList.add(itemId);
                }
            }
        }
        return inventGoodList;
    }
    /*获取新发明商品产生的新原料*/
    public static Set<Integer> getNewMaterialId(int newGoodId){
        Set<Integer> requiredMaterial = new HashSet<>();
        GoodFormula formula = MetaData.getFormula(newGoodId);
        /*获取新商品需要的原料*/
        for (GoodFormula.Info info : formula.material) {
            if(info.item!=null) {
                requiredMaterial.add(info.item.id);
            }
        }
        /*移除已有的原料*/
        Player player = GameDb.queryPlayerForOne();
        if(player==null){
            return new HashSet<>();
        }
        requiredMaterial.removeAll(player.itemIds());
        return requiredMaterial;
    }
    /*随机从发明的商品列表中筛选一个*/
    public static Integer  randomGood(Set<Integer> itemIds) {
        List<Integer> candicate = new ArrayList<>();
        candicate.addAll(itemIds);
        if(candicate.isEmpty())
            return null;
        return candicate.get(Prob.random(0, candicate.size()));
    }
    /*更新玩家的商品列表*/
    public static void updatePlayerGoodList(List<Player> players,Set<Integer> goodIds){
        players.forEach(p->{
           goodIds.forEach(good->{
               p.addItem(good,0);
           });
        });
        GameDb.saveOrUpdate(players);
    }
}
