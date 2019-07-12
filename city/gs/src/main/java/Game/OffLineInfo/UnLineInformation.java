package Game.OffLineInfo;

import Game.*;
import Game.Meta.MetaBuilding;
import Shared.LogDb;
import Shared.Util;
import com.google.protobuf.InvalidProtocolBufferException;
import com.mongodb.Block;
import gs.Gs;
import org.bson.Document;
import org.bson.types.Binary;

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
        Map<Integer, List<OffLineBuildingRecord>> recordMap = OffLineSummaryUtil.getOffLineBuildingIncome(player.getOfflineTs(), player.getOnlineTs(),playerId);
        Map<Integer,Gs.BuildingIncome> resultMap = totalUnLineBuildingIncome(recordMap, buildingMap);//可以进行数据封装了（包含了研究所，零售店，除了预测）

        //3.获取离线期间玩家预测盈亏
        List<OffLineFlightRecord> playerFlightForecast = OffLineSummaryUtil.getPlayerFlightForecast(playerId, player.getOfflineTs(), player.getOnlineTs());
        //转换为proto
        Gs.ForecastIncome forecastIncome = forecastIncomeToProto(playerFlightForecast);
        Gs.UnLineInformation.Builder builder = Gs.UnLineInformation.newBuilder();

        Gs.BuildingIncome material = resultMap.get(MetaBuilding.MATERIAL);
        if(material!=null&&material.getUnLineIncomeList().size()>0){
            builder.setMaterial(material);
        }
        Gs.BuildingIncome produce = resultMap.get(MetaBuilding.PRODUCE);
        if(produce!=null&&produce.getUnLineIncomeList().size()>0){
            builder.setProduceDepartment(produce);
        }
        Gs.BuildingIncome retailShop = resultMap.get(MetaBuilding.RETAIL);
        if(retailShop!=null&&retailShop.getUnLineIncomeList().size()>0){
            builder.setRetailShop(retailShop);
        }
        Gs.BuildingIncome apartment = resultMap.get(MetaBuilding.APARTMENT);
        if(apartment!=null&&apartment.getUnLineIncomeList().size()>0){
            builder.setApartment(apartment);
        }
        Gs.BuildingIncome publicFacility = resultMap.get(MetaBuilding.PUBLIC);
        if(publicFacility!=null&&publicFacility.getUnLineIncomeList().size()>0){
            builder.setPublicFacility(publicFacility);
        }
        Gs.BuildingIncome lab = resultMap.get(MetaBuilding.LAB);
        if(lab!=null&&lab.getUnLineIncomeList().size()>0){
            builder.setLaboratory(lab);
        }
        return builder.build();
    }


    //统计建筑离线期间的建筑收益数据(参数1，建筑所有的收益记录，参数2，玩家所有建筑)
    public  Map<Integer,Gs.BuildingIncome> totalUnLineBuildingIncome(Map<Integer,List<OffLineBuildingRecord>> recordMap, Map<Integer,Set<Building>> buildingMap){
        /*1.统计所有有收入记录的建筑*/
        Map<Integer, Map<UUID, Gs.BuildingIncome.UnLineIncome>> totalMap = new HashMap<>();
        for (Map.Entry<Integer, List<OffLineBuildingRecord>> entry : recordMap.entrySet()) {
            Map<UUID, Gs.BuildingIncome.UnLineIncome> info = getBuildingIncomeInfo(entry.getValue(), buildingMap.get(entry.getKey()));
            if(!info.isEmpty()){
                //totalMap.put(entry.getKey(),)
            }
        }






        Map<Integer,Gs.BuildingIncome> map=new HashMap<>();
        Map<UUID, Gs.BuildingIncome.UnLineIncome> materialMap = getBuildingIncomeInfo(recordMap.getOrDefault(MetaBuilding.MATERIAL,new ArrayList<>()), buildingMap.getOrDefault(MetaBuilding.MATERIAL,new HashSet<>()));
        Map<UUID, Gs.BuildingIncome.UnLineIncome> produceMap = getBuildingIncomeInfo(recordMap.getOrDefault(MetaBuilding.PRODUCE,new ArrayList<>()), buildingMap.getOrDefault(MetaBuilding.PRODUCE,new HashSet<>()));
        Map<UUID, Gs.BuildingIncome.UnLineIncome> retailShopMap = getBuildingIncomeInfo(recordMap.getOrDefault(MetaBuilding.RETAIL,new ArrayList<>()), buildingMap.getOrDefault(MetaBuilding.RETAIL,new HashSet<>()));
        Map<UUID, Gs.BuildingIncome.UnLineIncome> apartmentMap = getBuildingIncomeInfo(recordMap.getOrDefault(MetaBuilding.APARTMENT,new ArrayList<>()), buildingMap.getOrDefault(MetaBuilding.APARTMENT,new HashSet<>()));
        Map<UUID, Gs.BuildingIncome.UnLineIncome> publicMap = getBuildingIncomeInfo(recordMap.getOrDefault(MetaBuilding.PUBLIC,new ArrayList<>()), buildingMap.getOrDefault(MetaBuilding.PUBLIC,new HashSet<>()));
        Map<UUID, Gs.BuildingIncome.UnLineIncome> labMap = getBuildingIncomeInfo(recordMap.getOrDefault(MetaBuilding.LAB,new ArrayList<>()), buildingMap.getOrDefault(MetaBuilding.LAB,new HashSet<>()));

        //2.移除统计过的建筑
        Set<UUID> recordBId = new HashSet<>();
        recordBId.addAll(materialMap.keySet());
        recordBId.addAll(produceMap.keySet());
        recordBId.addAll(retailShopMap.keySet());
        recordBId.addAll(apartmentMap.keySet());
        recordBId.addAll(publicMap.keySet());
        recordBId.addAll(labMap.keySet());
        Map<Integer, Set<Building>> noIncomeBuild = removeRecordBuilding(recordBId, buildingMap);

        //3.统计没有收入记录的建筑
        List<Gs.BuildingIncome.UnLineIncome> materialCount = getNoIncomeBuildingInfo(noIncomeBuild.getOrDefault(MetaBuilding.MATERIAL, new HashSet<>()));
        List<Gs.BuildingIncome.UnLineIncome> pruduceCount = getNoIncomeBuildingInfo(noIncomeBuild.getOrDefault(MetaBuilding.PRODUCE, new HashSet<>()));
        List<Gs.BuildingIncome.UnLineIncome> retailCount = getNoIncomeBuildingInfo(noIncomeBuild.getOrDefault(MetaBuilding.RETAIL, new HashSet<>()));
        List<Gs.BuildingIncome.UnLineIncome> apartmentCount = getNoIncomeBuildingInfo(noIncomeBuild.getOrDefault(MetaBuilding.APARTMENT, new HashSet<>()));
        List<Gs.BuildingIncome.UnLineIncome> publicCount = getNoIncomeBuildingInfo(noIncomeBuild.getOrDefault(MetaBuilding.PUBLIC, new HashSet<>()));
        List<Gs.BuildingIncome.UnLineIncome> labCount = getNoIncomeBuildingInfo(noIncomeBuild.getOrDefault(MetaBuilding.LAB, new HashSet<>()));

        /*完成所有的建筑的统计数据合并*/
        materialCount.addAll(materialMap.values());
        pruduceCount.addAll(produceMap.values());
        retailCount.addAll(retailShopMap.values());
        apartmentCount.addAll(apartmentMap.values());
        publicCount.addAll(publicMap.values());
        labCount.addAll(labMap.values());
        //统计总收入数值（只统计有收入记录的）
        Integer materialTotalIncome = getTotalIncome(materialMap.values());
        Integer produceTotalIncome = getTotalIncome(produceMap.values());
        Integer retailShopIncome = getTotalIncome(retailShopMap.values());
        Integer apartmentIncome = getTotalIncome(apartmentMap.values());
        Integer publicIncome = getTotalIncome(publicMap.values());
        Integer labTotalIncome = getTotalIncome(labMap.values());

        map.put(MetaBuilding.MATERIAL,Gs.BuildingIncome.newBuilder().setTotalIncom(materialTotalIncome).addAllUnLineIncome(materialCount).build());
        map.put(MetaBuilding.PRODUCE,Gs.BuildingIncome.newBuilder().setTotalIncom(produceTotalIncome).addAllUnLineIncome(pruduceCount).build());
        map.put(MetaBuilding.RETAIL,Gs.BuildingIncome.newBuilder().setTotalIncom(retailShopIncome).addAllUnLineIncome(retailCount).build());
        map.put(MetaBuilding.APARTMENT,Gs.BuildingIncome.newBuilder().setTotalIncom(apartmentIncome).addAllUnLineIncome(apartmentCount).build());
        map.put(MetaBuilding.PUBLIC,Gs.BuildingIncome.newBuilder().setTotalIncom(publicIncome).addAllUnLineIncome(publicCount).build());
        map.put(MetaBuilding.LAB,Gs.BuildingIncome.newBuilder().setTotalIncom(labTotalIncome).addAllUnLineIncome(labCount).build());
        return map;
    }

    public Integer getTotalIncome(Collection<Gs.BuildingIncome.UnLineIncome> values){
        int sum =0;
        if(!(values==null)&&!(values.isEmpty())){
            for (Gs.BuildingIncome.UnLineIncome value : values) {
                sum += value.getIncome();
            }
        }
        return sum;
    }
    //移除已经统计过的建筑
    public Map<Integer,Set<Building>> removeRecordBuilding(Set<UUID> recordId,Map<Integer,Set<Building>> buildingMap){
        Iterator<Set<Building>> types = buildingMap.values().iterator();
        while(types.hasNext()){
            Iterator<Building> buildings = types.next().iterator();
            while(buildings.hasNext()){
                Building building = buildings.next();
                if(recordId.contains(building.id())){
                    buildings.remove();
                    System.err.println("移除了"+building.getName());
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
    public Map<UUID, Gs.BuildingIncome.UnLineIncome> getBuildingIncomeInfo(List<OffLineBuildingRecord> records, Set<Building> buildings){
        Map<UUID, Gs.BuildingIncome.UnLineIncome> incomeMap = new HashMap<>();
        if(records.isEmpty()||buildings.isEmpty()){//没有收益记录
            return incomeMap;
        }
        buildings.forEach(b->{
            for (OffLineBuildingRecord record : records) {
                if(record.bid.equals(b.id())){
                    Gs.BuildingIncome.UnLineIncome buildingIncome=null;
                    if(incomeMap.containsKey(record.bid)){//存在一个建筑多条收入记录(累计收入)
                        Gs.BuildingIncome.UnLineIncome.Builder builder = incomeMap.get(record.bid).toBuilder();
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

    /*获取没有收益建筑的proto信息*/
    public List<Gs.BuildingIncome.UnLineIncome> getNoIncomeBuildingInfo(Set<Building> buildings){
        List<Gs.BuildingIncome.UnLineIncome> buildingInfo = new ArrayList<>();
        if(!buildings.isEmpty()){
            buildings.forEach(b->{
                buildingInfo.add(noRecordToProto(b.toProto()));
            });
        }
        return buildingInfo;
    }
    //收益建筑Proto
    public Gs.BuildingIncome.UnLineIncome recordToProto(OffLineBuildingRecord record, int mid, Gs.MiniIndex miniIndex, String bName){//交易记录转proto
        Gs.BuildingIncome.UnLineIncome.Builder builder = Gs.BuildingIncome.UnLineIncome.newBuilder();
        builder.setBuildingId(Util.toByteString(record.bid))
                .setIncome(record.n * record.price)
                .setMId(mid)
                .setPos(miniIndex)
                .setBuildingName(bName);
        return builder.build();
    }


    //无收益建筑Proto
    public Gs.BuildingIncome.UnLineIncome noRecordToProto(Gs.BuildingInfo info){
        Gs.BuildingIncome.UnLineIncome.Builder builder = Gs.BuildingIncome.UnLineIncome.newBuilder();
        builder.setBuildingId(info.getId())
                .setIncome(0)
                .setMId(info.getMId())
                .setPos(info.getPos())
                .setBuildingName(info.getName());
        return builder.build();
    }

    public Gs.ForecastIncome forecastIncomeToProto( List<OffLineFlightRecord> playerFlightForecast){
        Gs.ForecastIncome.Builder builder = Gs.ForecastIncome.newBuilder();
        long sum=0;
        for (OffLineFlightRecord record : playerFlightForecast) {
            Gs.ForecastIncome.FlightForecast.Builder unLineIncome = Gs.ForecastIncome.FlightForecast.newBuilder();
            unLineIncome.setFlightCompany(record.data.getFlightCompany())
                    .setFlightArrtimeDate(record.data.getFlightDeptimeDate())
                    .setFlightDepcode(record.data.getFlightDepcode())
                    .setProfitOrLoss(record.profitOrLoss)
                    .setWin(record.win);
            builder.addUnLineIncome(unLineIncome);
            if(record.win){
                sum += record.profitOrLoss;
            }else{
                sum -= record.profitOrLoss;
            }
        }
        builder.setTotalIncom(sum);
        return builder.build();
    }


}
