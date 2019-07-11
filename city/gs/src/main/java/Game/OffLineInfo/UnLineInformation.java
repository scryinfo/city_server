package Game.OffLineInfo;

import Game.*;
import Game.Meta.MetaBuilding;
import Shared.LogDb;
import Shared.Util;
import com.mongodb.Block;
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
        Map<Integer,Set<Building>> buildingMap=new HashMap<>();
        //1.获取玩家所有的建筑信息，然后分别筛选
        City.instance().forEachBuilding(playerId,b->{
            /*int type = b.type();
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
            }*/
            buildingMap.computeIfAbsent(b.type(), k -> new HashSet<>()).add(b);
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
                eq("pid", playerId),
                gte("time", unlineTime),
                lte("time", onlineTime)
        )).forEach((Block<? super Document>) document ->{
           UUID bid= (UUID) document.get("bid");
           UUID pid = (UUID) document.get("pid");
           Integer n = document.getInteger("n");
           Integer price = document.getInteger("price");
           IncomeRecord record = new IncomeRecord(bid, pid, n, price);
           incomeMap.computeIfAbsent(document.getInteger("bt"), k -> new ArrayList<>()).add(record);
        });
       return incomeMap;
    }

    //统计建筑离线期间的数据
    public void totalUnLineBuildingIncome(Map<Integer,List<IncomeRecord>> recordMap,Map<Integer,Set<Building>> buildingMap){
        List<Gs.BuildingIncome> material = new ArrayList<>();
        List<Gs.BuildingIncome> produceDepartment= new ArrayList<>();
        List<Gs.BuildingIncome> retailShop = new ArrayList<>();
        List<Gs.BuildingIncome> apartment = new ArrayList<>();
        List<Gs.BuildingIncome> publicFacility = new ArrayList<>();
        List<Gs.BuildingIncome> laboratory = new ArrayList<>();
        //1.筛选出离线期间没有记录的建筑
        //1.原料厂
        Map<UUID, Gs.BuildingIncome> materialMap = getBuildingIncomeInfo(recordMap.get(MetaBuilding.MATERIAL), buildingMap.getOrDefault(MetaBuilding.MATERIAL,new HashSet<>()));
        Map<UUID, Gs.BuildingIncome> produceMap = getBuildingIncomeInfo(recordMap.get(MetaBuilding.PRODUCE), buildingMap.getOrDefault(MetaBuilding.PRODUCE,new HashSet<>()));
        Map<UUID, Gs.BuildingIncome> retailShopMap = getBuildingIncomeInfo(recordMap.get(MetaBuilding.RETAIL), buildingMap.getOrDefault(MetaBuilding.RETAIL,new HashSet<>()));
        Map<UUID, Gs.BuildingIncome> apartmentMap = getBuildingIncomeInfo(recordMap.get(MetaBuilding.APARTMENT), buildingMap.getOrDefault(MetaBuilding.APARTMENT,new HashSet<>()));
        Map<UUID, Gs.BuildingIncome> publicMap = getBuildingIncomeInfo(recordMap.get(MetaBuilding.PUBLIC), buildingMap.getOrDefault(MetaBuilding.PUBLIC,new HashSet<>()));
        Map<UUID, Gs.BuildingIncome> labMap = getBuildingIncomeInfo(recordMap.get(MetaBuilding.LAB), buildingMap.getOrDefault(MetaBuilding.LAB,new HashSet<>()));
    }

    /*
     * 参数1：记录
     * 参数2：建筑
     * 参数3：要统计的类型
     * */
    public Map<UUID, Gs.BuildingIncome> getBuildingIncomeInfo(List<IncomeRecord> records,Set<Building> buildings){
        Map<UUID, Gs.BuildingIncome> incomeMap = new HashMap<>();
        if(records.isEmpty()){//没有收益记录
            return incomeMap;
        }
        if(buildings.isEmpty()){//没有建筑
            return null;
        }
        buildings.forEach(b->{
            for (IncomeRecord record : records) {
                if(record.bid.equals(b.id())){
                    Gs.BuildingIncome buildingIncome=null;
                    if(incomeMap.containsKey(record.bid)){//存在一个建筑多条收入记录(累计收入)
                        Gs.BuildingIncome.Builder builder = incomeMap.get(record.bid).toBuilder();
                        buildingIncome=builder.setIncome(builder.getIncome() + (record.n * record.price)).build();
                    }else {
                        Gs.MiniIndex miniIndex = b.getMiniIndex();
                        buildingIncome = recordToProto(record, b.metaId(), miniIndex, b.getName());
                    }
                    incomeMap.put(b.id(), buildingIncome);
                }
            }
        });
        return incomeMap;
    }

    public Gs.BuildingIncome recordToProto(IncomeRecord record,int mid,Gs.MiniIndex miniIndex,String bName){//交易记录转proto
        Gs.BuildingIncome.Builder builder = Gs.BuildingIncome.newBuilder();
        builder.setBuildingId(Util.toByteString(record.bid))
                .setIncome(record.n * record.price)
                .setMId(mid)
                .setPos(miniIndex)
                .setBuildingName(bName);
        return builder.build();
    }




}
