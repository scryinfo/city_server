package Game.OffLineInfo;

import Game.*;
import Game.Meta.MetaBuilding;
import Shared.LogDb;
import Shared.Util;
import com.mongodb.Block;
import com.sun.xml.internal.bind.v2.model.core.ID;
import gs.Gs;
import org.bson.Document;

import java.util.*;

import static com.mongodb.client.model.Filters.*;

//离线通知
public class UnLineInformation {

    public Gs.UnLineInformation getPlayerUnLineInformation(UUID playerId){
        Player player = GameDb.getPlayer(playerId);
        Map<Integer,Set<Building>> buildingMap=new HashMap<>();
        //1.获取玩家所有的建筑信息，然后分别筛选
        City.instance().forEachBuilding(playerId,b->{
            buildingMap.computeIfAbsent(b.type(), k -> new HashSet<>()).add(b);
        });
        //2.获取离线期间玩家所有建筑的收益
        Map<Integer, List<IncomeRecord>> recordMap = countBuildingIncome(player.getOfflineTs(), player.getOnlineTs(),playerId);
        Map<Integer, List<Gs.BuildingIncome>> resultMap = totalUnLineBuildingIncome(recordMap, buildingMap);//可以进行数据封装了（包含了研究所，零售店，除了预测）
        return null;
    }

    //开始统计玩家离线状态各个建筑的收入信息
    /*
    * arg0:要统计的建筑
    * arg1:玩家离线时间（作为统计条件的开始时间）
    * arg2:玩家在线时间（做i为统计条件的结束时间）
    */
    public  Map<Integer,List<IncomeRecord>> countBuildingIncome(long unlineTime,long onlineTime,UUID playerId){
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
    public  Map<Integer,List<Gs.BuildingIncome>> totalUnLineBuildingIncome(Map<Integer,List<IncomeRecord>> recordMap,Map<Integer,Set<Building>> buildingMap){
        Map<Integer,List<Gs.BuildingIncome>> map=new HashMap<>();
        //1.筛选出离线期间没有记录的建筑
        //1.原料厂
        Map<UUID, Gs.BuildingIncome> materialMap = getBuildingIncomeInfo(recordMap.get(MetaBuilding.MATERIAL), buildingMap.getOrDefault(MetaBuilding.MATERIAL,new HashSet<>()));
        Map<UUID, Gs.BuildingIncome> produceMap = getBuildingIncomeInfo(recordMap.get(MetaBuilding.PRODUCE), buildingMap.getOrDefault(MetaBuilding.PRODUCE,new HashSet<>()));
        Map<UUID, Gs.BuildingIncome> retailShopMap = getBuildingIncomeInfo(recordMap.get(MetaBuilding.RETAIL), buildingMap.getOrDefault(MetaBuilding.RETAIL,new HashSet<>()));
        Map<UUID, Gs.BuildingIncome> apartmentMap = getBuildingIncomeInfo(recordMap.get(MetaBuilding.APARTMENT), buildingMap.getOrDefault(MetaBuilding.APARTMENT,new HashSet<>()));
        Map<UUID, Gs.BuildingIncome> publicMap = getBuildingIncomeInfo(recordMap.get(MetaBuilding.PUBLIC), buildingMap.getOrDefault(MetaBuilding.PUBLIC,new HashSet<>()));
        Map<UUID, Gs.BuildingIncome> labMap = getBuildingIncomeInfo(recordMap.get(MetaBuilding.LAB), buildingMap.getOrDefault(MetaBuilding.LAB,new HashSet<>()));

        /*现在要统计没有收益的建筑，收益统统设置为0*/
        Set<UUID> recordBId = new HashSet<>();
        recordBId.addAll(materialMap.keySet());
        recordBId.addAll(produceMap.keySet());
        recordBId.addAll(retailShopMap.keySet());
        recordBId.addAll(apartmentMap.keySet());
        recordBId.addAll(publicMap.keySet());
        recordBId.addAll(labMap.keySet());
        //移除统计过的建筑
        Map<Integer, Set<Building>> noIncomeBuild = removeRecordBuilding(recordBId, buildingMap);
        List<Gs.BuildingIncome> noIncomeMaterial = getNoIncomeBuildingInfo(noIncomeBuild.getOrDefault(MetaBuilding.MATERIAL, new HashSet<>()));
        List<Gs.BuildingIncome> noIncomePruduce = getNoIncomeBuildingInfo(noIncomeBuild.getOrDefault(MetaBuilding.PRODUCE, new HashSet<>()));
        List<Gs.BuildingIncome> noIncomeRetail = getNoIncomeBuildingInfo(noIncomeBuild.getOrDefault(MetaBuilding.RETAIL, new HashSet<>()));
        List<Gs.BuildingIncome> noIncomeApartment = getNoIncomeBuildingInfo(noIncomeBuild.getOrDefault(MetaBuilding.APARTMENT, new HashSet<>()));
        List<Gs.BuildingIncome> noIncomePublic = getNoIncomeBuildingInfo(noIncomeBuild.getOrDefault(MetaBuilding.PUBLIC, new HashSet<>()));
        List<Gs.BuildingIncome> noIncomeLab = getNoIncomeBuildingInfo(noIncomeBuild.getOrDefault(MetaBuilding.LAB, new HashSet<>()));
        /*完成统计*/
        noIncomeMaterial.addAll(materialMap.values());
        noIncomePruduce.addAll(produceMap.values());
        noIncomeRetail.addAll(retailShopMap.values());
        noIncomeApartment.addAll(apartmentMap.values());
        noIncomePublic.addAll(publicMap.values());
        noIncomeLab.addAll(labMap.values());
        map.put(MetaBuilding.MATERIAL,noIncomeMaterial);
        map.put(MetaBuilding.PRODUCE,noIncomePruduce);
        map.put(MetaBuilding.RETAIL,noIncomeRetail);
        map.put(MetaBuilding.APARTMENT,noIncomeApartment);
        map.put(MetaBuilding.PUBLIC,noIncomePublic);
        map.put(MetaBuilding.LAB,noIncomeLab);
        return map;
    }

    public Map<Integer,Set<Building>> removeRecordBuilding(Set<UUID> recordBuilding,Map<Integer,Set<Building>> buildingMap){
        Iterator<Set<Building>> types = buildingMap.values().iterator();
        while(types.hasNext()){
            Iterator<Building> buildings = types.next().iterator();
            while(buildings.hasNext()){
                Building building = buildings.next();
                if(recordBuilding.contains(building.id())){
                    buildings.remove();
                }
            }
        }
        return buildingMap;
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

    /*获取没有收益建筑的proto信息*/
    public Gs.BuildingIncome noRecordToProto(Gs.BuildingInfo info){
        Gs.BuildingIncome.Builder builder = Gs.BuildingIncome.newBuilder();
        builder.setBuildingId(info.getId())
                .setIncome(0)
                .setMId(info.getMId())
                .setPos(info.getPos())
                .setBuildingName(info.getName());
        return builder.build();
    }

    public List<Gs.BuildingIncome> getNoIncomeBuildingInfo(Set<Building> buildings){
        List<Gs.BuildingIncome> buildingInfo = new ArrayList<>();
        if(!buildings.isEmpty()){
            buildings.forEach(b->{
                buildingInfo.add(noRecordToProto(b.toProto()));
            });
        }
        return buildingInfo;
    }

}
