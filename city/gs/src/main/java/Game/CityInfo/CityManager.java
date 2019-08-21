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

public class CityManager {
    private CityManager() {}
    private static CityManager instance=new CityManager();
    /*缓存全城可用的原料*/
    public  Set<Integer> cityMaterial = new HashSet<>();
    /*缓存全城可用的商品*/
    public  Set<Integer> cityGood = new HashSet<>();

    public static CityManager instance(){
        return instance;
    }

    public void init(){
        Player player = GameDb.queryPlayerForOne();
        if(player!=null){//有玩家时，从玩家的可用商品列表取
            classifyGood(player.itemIds());
        }else{//无玩家，从配置表的可用商品列表获取
            classifyGood(MetaData.getAllDefaultToUseItemId());
        }
    }

    /*分类商品*/
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

    /*判断某个商品是否包含在全城可生产的商品中*/
    public boolean usable(int itemId){
        if(this.cityMaterial.contains(itemId)||this.cityGood.contains(itemId)){
            return true;
        }else{
            return false;
        }
    }
}
