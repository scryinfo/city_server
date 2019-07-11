package Game;

import Game.Meta.MetaBuilding;
import Shared.LogDb;
import Shared.Util;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import gs.Gs;
import org.bson.Document;

import java.util.*;

import static com.mongodb.client.model.Filters.*;

//离线通知
public class UnLineInformation {

    public static Gs.UnLineInformation getPlayerUnLineInformation(UUID playerId){
        Player player = GameDb.getPlayer(playerId);
        List<MaterialFactory> materialFactories = new ArrayList<>();
        List<ProduceDepartment> produceDepartments = new ArrayList<>();
        List<RetailShop> retailShops = new ArrayList<>();
        List<Apartment> apartments = new ArrayList<>();
        List<PublicFacility> publicFacilities = new ArrayList<>();
        List<Laboratory> laboratories = new ArrayList<>();
        //1.获取玩家所有的建筑信息，然后分别筛选
        City.instance().forEachBuilding(playerId,b->{
            int type = b.type();
            switch (type){
                case MetaBuilding.MATERIAL:
                    materialFactories.add((MaterialFactory) b);
                    break;
                case MetaBuilding.PRODUCE:
                    produceDepartments.add((ProduceDepartment)b);
                    break;
                case MetaBuilding.RETAIL:
                    retailShops.add((RetailShop) b);
                    break;
                case MetaBuilding.APARTMENT:
                    apartments.add((Apartment) b);
                    break;
                case MetaBuilding.PUBLIC:
                    publicFacilities.add((PublicFacility) b);
                    break;
                case MetaBuilding.LAB:
                    laboratories.add((Laboratory) b);
                    break;
            }
        });
        Map<Integer, List<IncomeRecord>> listMap = countBuildingIncome(0, System.currentTimeMillis(),playerId);
        return null;
    }


    //开始统计玩家离线状态各个建筑的收入信息
    /*
    * arg0:要统计的建筑
    * arg1:玩家离线时间（作为统计条件的开始时间）
    * arg2:玩家在线时间（做i为统计条件的结束时间）
    */
    public static Map<Integer,List<IncomeRecord>> countBuildingIncome(long unlineTime,long onlineTime,UUID playerId){
        Map<Integer,List<IncomeRecord>> incomeMap=new HashMap<>();
        //首先筛选出玩家离线期间的所有数据
        //原料厂的货架收入
       LogDb.getSellerBuildingIncome().find(and(
                eq("pid", playerId)
               /* gte("time", unlineTime),
                lte("time", onlineTime)*/
        )).forEach((Block<? super Document>) document ->{
           System.err.println(document.get("bid"));
           System.err.println(document.get("pid"));
           UUID bid= (UUID) document.get("bid");
           UUID pid = (UUID) document.get("pid");
           Integer n = document.getInteger("n");
           Integer price = document.getInteger("price");
           IncomeRecord record = new IncomeRecord(bid, pid, n, price);
           incomeMap.computeIfAbsent(document.getInteger("bt"), k -> new ArrayList<>()).add(record);
        });
       return incomeMap;
    }


}
