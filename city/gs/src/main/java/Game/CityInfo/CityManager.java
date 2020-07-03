package Game.CityInfo;

import Game.GameDb;
import Game.Meta.MetaData;
import Game.Meta.MetaGood;
import Game.Meta.MetaMaterial;
import Game.Player;
import gs.Gs;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class CityManager {
    private CityManager() {}
    private static CityManager instance=new CityManager();
    /*Cache raw materials available throughout the city*/
    public  Set<Integer> cityMaterial = new TreeSet<>();
    /*Cache goods available throughout the city*/
    public  Set<Integer> cityGood = new TreeSet<>();

    public static CityManager instance(){
        return instance;
    }

    public void init(){
        Player player = GameDb.queryPlayerForOne();
        if(player!=null){//When there is a player, take it from the player’s available product list
            classifyGood(player.itemIds());
        }else{//No players, get from the list of available products in the configuration table
            classifyGood(MetaData.getAllDefaultToUseItemId());
        }
    }

    /*Classified goods*/
    public void classifyGood(Set<Integer> itemIds){
        itemIds.forEach(id->{
            if(MetaGood.isItem(id)){
                cityGood.add(id);
            }
            if(MetaMaterial.isItem(id)){
                cityMaterial.add(id);
            }
        });
    }

    public Gs.CityGoodInfo toProto(){
        Gs.CityGoodInfo.Builder builder = Gs.CityGoodInfo.newBuilder();
        Gs.Nums.Builder materials = Gs.Nums.newBuilder().addAllNum(cityMaterial);
        Gs.Nums.Builder goods = Gs.Nums.newBuilder().addAllNum(cityGood);
        builder.setGoods(goods).setMaterial(materials);
        return builder.build();
    }

    /*Determine whether a commodity is included in the commodities that can be produced in the city*/
    public boolean usable(int itemId){
        if(this.cityMaterial.contains(itemId)||this.cityGood.contains(itemId)){
            return true;
        }else{
            return false;
        }
    }
}
