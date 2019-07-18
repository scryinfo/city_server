package Game.OffLineInfo;

import Game.*;
import Game.Meta.MetaBuilding;
import Shared.Util;
import gs.Gs;

import java.util.*;

//离线通知
public class OffLineInformation {

    private OffLineInformation(){}
    private static OffLineInformation instance=new OffLineInformation();
    public static OffLineInformation instance(){
        return instance;
    }

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

        Gs.UnLineInformation.Builder builder = Gs.UnLineInformation.newBuilder();
        //转换为proto
        if(!playerFlightForecast.isEmpty()){
            Gs.ForecastIncome forecastIncome = forecastIncomeToProto(playerFlightForecast);
            builder.setForecast(forecastIncome);
        }
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


    //统计建筑离线期间所有建筑收益数据(参数1，建筑所有的收益记录，参数2，玩家所有建筑)
    public  Map<Integer,Gs.BuildingIncome> totalUnLineBuildingIncome(Map<Integer,List<OffLineBuildingRecord>> recordMap, Map<Integer,Set<Building>> buildingMap){
        /*1.统计所有有收入记录的建筑*/
        Map<Integer, Map<UUID, Gs.BuildingIncome.UnLineIncome>> unLineIncomeMap = new HashMap<>();

        for (Map.Entry<Integer, List<OffLineBuildingRecord>> entry : recordMap.entrySet()) {
            Map<UUID, Gs.BuildingIncome.UnLineIncome> info = getBuildingIncomeInfo(entry.getValue(), buildingMap.get(entry.getKey()));
            if(!info.isEmpty()){
                unLineIncomeMap.put(entry.getKey(),info);
            }
        }
        Map<Integer,Gs.BuildingIncome> resultMap=new HashMap<>();
        //2.移除统计过的建筑
        Set<UUID> recordBId = new HashSet<>();
        for (Map<UUID, Gs.BuildingIncome.UnLineIncome> incomeMap : unLineIncomeMap.values()) {
            recordBId.addAll(incomeMap.keySet());
        }
        Map<Integer, Set<Building>> noIncomeBuild = removeRecordBuilding(recordBId, buildingMap);

        //3.统计没有收入记录的建筑
        Map<Integer,List<Gs.BuildingIncome.UnLineIncome> > allIncomeMap =new HashMap<>();
        for (Map.Entry<Integer, Set<Building>> noIncomeEntry : noIncomeBuild.entrySet()) {
            List<Gs.BuildingIncome.UnLineIncome> info = getNoIncomeBuildingInfo(noIncomeEntry.getValue());
            allIncomeMap.put(noIncomeEntry.getKey(),info);
        }
        /*4.完成所有的建筑的统计数据合并*/
        for (Map.Entry<Integer, Map<UUID, Gs.BuildingIncome.UnLineIncome>> mapEntry : unLineIncomeMap.entrySet()) {
            allIncomeMap.getOrDefault(mapEntry.getKey(), new ArrayList<>()).addAll(mapEntry.getValue().values());
        }
        //5.完成统计后加入总收入
        for (Map.Entry<Integer, List<Gs.BuildingIncome.UnLineIncome>> totalEntry : allIncomeMap.entrySet()) {
            Integer totalIncome = getTotalIncome(totalEntry.getValue());
            resultMap.put(totalEntry.getKey(),Gs.BuildingIncome.newBuilder().setTotalIncome(totalIncome).addAllUnLineIncome(totalEntry.getValue()).build());
        }
        return resultMap;
    }

    /*统计某一类型建筑总收入*/
    public Integer getTotalIncome(Collection<Gs.BuildingIncome.UnLineIncome> values){
        int sum =0;
        if(!(values==null)&&!(values.isEmpty())){
            for (Gs.BuildingIncome.UnLineIncome value : values) {
                sum += value.getIncome();
            }
        }
        return sum;
    }

    //移除已经统计过的建筑（参数1：要移除的建筑id,参数2：玩家所有的建筑）
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
     * 统计有收益记录的建筑
     * 参数1：收入记录
     * 参数2：建筑
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




    /*航班预测结果proto*/
    public Gs.ForecastIncome forecastIncomeToProto( List<OffLineFlightRecord> playerFlightForecast){
        Gs.ForecastIncome.Builder builder = Gs.ForecastIncome.newBuilder();
        long sum=0;
        for (OffLineFlightRecord record : playerFlightForecast) {
            Gs.ForecastIncome.FlightForecast.Builder unLineIncome = Gs.ForecastIncome.FlightForecast.newBuilder();
            unLineIncome.setFlightCompany(record.data.getFlightCompany())
                    .setFlightArrtimeDate(record.data.getFlightDeptimeDate())
                    .setFlightDepcode(record.data.getFlightDepcode())
                    .setWin(record.win);
            if(record.win){
                unLineIncome.setProfitOrLoss(record.profitOrLoss);
                sum += record.profitOrLoss;//盈利则是正数
            }else{
                unLineIncome.setProfitOrLoss(-record.profitOrLoss);
                sum -= record.profitOrLoss;
            }
            builder.addFlightIncome(unLineIncome);//亏损是负数
        }
        builder.setTotalIncome(sum);
        return builder.build();
    }
}
