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
    /*Get a list of inventable products*/
    public static Set<Integer> getCityInventGoodByType(List<Integer> types){
        Set<Integer> inventGoodList = new HashSet<>();
        /*1.All goods in the city*/
        Set<Integer> allGoodId = new HashSet<>();
        allGoodId.addAll(MetaData.getAllGoodId());
        /*2.Get all available products for one player (and remove them)*/
        Player player = GameDb.queryPlayerForOne();
        if(player==null){
            return null;
        }
        Set<Integer> ids = player.itemIds();
        allGoodId.removeAll(ids);
        for (Integer type : types) {
            /*tp is 5 digits*/
            for (Integer itemId : allGoodId) {
                int itemType = itemId /100;
                if(itemType==type) {
                    inventGoodList.add(itemId);
                }
            }
        }
        return inventGoodList;
    }
    /*Obtain new raw materials produced by new invention goods*/
    public static Set<Integer> getNewMaterialId(int newGoodId){
        Set<Integer> requiredMaterial = new HashSet<>();
        GoodFormula formula = MetaData.getFormula(newGoodId);
        /*Get raw materials for new products*/
        for (GoodFormula.Info info : formula.material) {
            if(info.item!=null) {
                requiredMaterial.add(info.item.id);
            }
        }
        /*Remove existing ingredients*/
        Player player = GameDb.queryPlayerForOne();
        if(player==null){
            return new HashSet<>();
        }
        requiredMaterial.removeAll(player.itemIds());
        return requiredMaterial;
    }
    /*Randomly select one from the list of invented goods*/
    public static Integer  randomGood(Set<Integer> itemIds) {
        List<Integer> candicate = new ArrayList<>();
        candicate.addAll(itemIds);
        if(candicate.isEmpty())
            return null;
        return candicate.get(Prob.random(0, candicate.size()));
    }
    /*Update player's product list*/
    public static void updatePlayerGoodList(List<Player> players,Set<Integer> goodIds){
        players.forEach(p->{
           goodIds.forEach(good->{
               p.addItem(good,0);
           });
        });
        GameDb.saveOrUpdate(players);
    }
}
