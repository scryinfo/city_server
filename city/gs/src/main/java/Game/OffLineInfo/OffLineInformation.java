package Game.OffLineInfo;

import Game.Building;
import Game.City;
import Game.GameDb;
import Game.Meta.MetaBuilding;
import Game.Player;
import Shared.Util;
import gs.Gs;

import java.util.*;

//Offline notification
public class OffLineInformation {

    private OffLineInformation(){}
    private static OffLineInformation instance=new OffLineInformation();
    public static OffLineInformation instance(){
        return instance;
    }

    public Gs.UnLineInformation getPlayerUnLineInformation(UUID playerId){
        Player player = GameDb.getPlayer(playerId);
        Map<Integer,Set<Building>> buildingMap=new HashMap<>();
        //1.Get all the building information of the player, and then filter them separately
        City.instance().forEachBuilding(playerId,b->{
            buildingMap.computeIfAbsent(b.type(), k -> new HashSet<>()).add(b);
        });
        //2.Get the revenue of all the players' buildings while offline
        Map<Integer, List<OffLineBuildingRecord>> recordMap = OffLineSummaryUtil.getOffLineBuildingIncome(player.getOfflineTs(), player.getOnlineTs(),playerId);
        Map<Integer,Gs.BuildingIncome> resultMap = totalUnLineBuildingIncome(recordMap, buildingMap);//Data can be encapsulated (including research institutes, retail stores, except prediction)

        //3.Get player prediction profit and loss during offline
        List<OffLineFlightRecord> playerFlightForecast = OffLineSummaryUtil.getPlayerFlightForecast(playerId, player.getOfflineTs(), player.getOnlineTs());

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
        Gs.BuildingIncome publicFacility = resultMap.get(MetaBuilding.PROMOTE);
        if(publicFacility!=null&&publicFacility.getUnLineIncomeList().size()>0){
            builder.setPublicFacility(publicFacility);
        }
        Gs.BuildingIncome lab = resultMap.get(MetaBuilding.TECHNOLOGY);
        if(lab!=null&&lab.getUnLineIncomeList().size()>0){
            builder.setLaboratory(lab);
        }
        //Convert to proto
        if(!playerFlightForecast.isEmpty()){
            Gs.ForecastIncome forecastIncome = forecastIncomeToProto(playerFlightForecast);
            builder.setForecast(forecastIncome);
        }
        /*Delete statistical offline data*/
        OffLineSummaryUtil.delUnLineData(player.getOfflineTs(), player.getOnlineTs(), playerId);
        return builder.build();
    }


    //Statistics of all building revenue data during the offline period of the building (parameter 1, all building revenue records, parameter 2, player all buildings)
    public  Map<Integer,Gs.BuildingIncome> totalUnLineBuildingIncome(Map<Integer,List<OffLineBuildingRecord>> recordMap, Map<Integer,Set<Building>> buildingMap){
        /*1.Count all buildings with income records*/
        Map<Integer, Map<UUID, Gs.BuildingIncome.UnLineIncome>> unLineIncomeMap = new HashMap<>();
        for (Map.Entry<Integer, List<OffLineBuildingRecord>> record : recordMap.entrySet()) {
            Map<UUID, Gs.BuildingIncome.UnLineIncome> info = getBuildingIncomeInfo(record.getValue(), buildingMap.get(record.getKey()));
            if(!info.isEmpty()){
                unLineIncomeMap.put(record.getKey(),info);
            }
        }
        Map<Integer,Gs.BuildingIncome> resultMap=new HashMap<>();
        //2.Remove statistics on buildings
        Set<UUID> recordBId = new HashSet<>();
        for (Map<UUID, Gs.BuildingIncome.UnLineIncome> incomeMap : unLineIncomeMap.values()) {
            recordBId.addAll(incomeMap.keySet());
        }
        Map<Integer, Set<Building>> noIncomeBuild = removeRecordBuilding(recordBId, buildingMap);

        //3.Statistics for buildings with no income records
        Map<Integer,List<Gs.BuildingIncome.UnLineIncome>> allIncomeMap =new HashMap<>();
        for (Map.Entry<Integer, Set<Building>> noIncomeEntry : noIncomeBuild.entrySet()) {
            List<Gs.BuildingIncome.UnLineIncome> info = getNoIncomeBuildingInfo(noIncomeEntry.getValue());
            allIncomeMap.put(noIncomeEntry.getKey(),info);
        }
        /*4.Complete the statistical data merge of all buildings*/
        for (Map.Entry<Integer, Map<UUID, Gs.BuildingIncome.UnLineIncome>> mapEntry : unLineIncomeMap.entrySet()) {
            allIncomeMap.computeIfAbsent(mapEntry.getKey(),k-> new ArrayList<>()).addAll(mapEntry.getValue().values());
        }
        //5.Add the total income after completing the statistics
        for (Map.Entry<Integer, List<Gs.BuildingIncome.UnLineIncome>> totalEntry : allIncomeMap.entrySet()) {
            Integer totalIncome = getTotalIncome(totalEntry.getValue());
            resultMap.put(totalEntry.getKey(),Gs.BuildingIncome.newBuilder().setTotalIncome(totalIncome).addAllUnLineIncome(totalEntry.getValue()).build());
        }
        return resultMap;
    }

    /*Statistics the total income of a certain type of construction*/
    public Integer getTotalIncome(Collection<Gs.BuildingIncome.UnLineIncome> values){
        int sum =0;
        if(!(values==null)&&!(values.isEmpty())){
            for (Gs.BuildingIncome.UnLineIncome value : values) {
                sum += value.getIncome();
            }
        }
        return sum;
    }

    //Remove buildings that have been counted (parameter 1: building id to be removed, parameter 2: all buildings owned by the player)
    public Map<Integer,Set<Building>> removeRecordBuilding(Set<UUID> recordId,Map<Integer,Set<Building>> buildingMap){
        Iterator<Set<Building>> types = buildingMap.values().iterator();
        while(types.hasNext()){
            Iterator<Building> buildings = types.next().iterator();
            while(buildings.hasNext()){
                Building building = buildings.next();
                if(recordId.contains(building.id())){
                    buildings.remove();
                    System.err.println("Removed"+building.getName());
                }
            }
        }
        return buildingMap;
    }

    /*
     * Statistics of buildings with record of income
     * Parameter 1: Income record
     * Parameter 2: Construction
     * */
    public Map<UUID, Gs.BuildingIncome.UnLineIncome> getBuildingIncomeInfo(List<OffLineBuildingRecord> records, Set<Building> buildings){
        Map<UUID, Gs.BuildingIncome.UnLineIncome> incomeMap = new HashMap<>();
        if(records.isEmpty()||buildings.isEmpty()){//No revenue record
            return incomeMap;
        }
        buildings.forEach(b->{
            for (OffLineBuildingRecord record : records) {
                if(record.bid.equals(b.id())){
                    Gs.BuildingIncome.UnLineIncome buildingIncome=null;
                    if(incomeMap.containsKey(record.bid)){//There are multiple income records for a building (cumulative income)
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

    /*Get proto information for unprofitable buildings*/
    public List<Gs.BuildingIncome.UnLineIncome> getNoIncomeBuildingInfo(Set<Building> buildings){
        List<Gs.BuildingIncome.UnLineIncome> buildingInfo = new ArrayList<>();
        if(!buildings.isEmpty()){
            buildings.forEach(b->{
                buildingInfo.add(noRecordToProto(b.toProto()));
            });
        }
        return buildingInfo;
    }

    //Non-Profit Construction Proto
    public Gs.BuildingIncome.UnLineIncome noRecordToProto(Gs.BuildingInfo info){
        Gs.BuildingIncome.UnLineIncome.Builder builder = Gs.BuildingIncome.UnLineIncome.newBuilder();
        builder.setBuildingId(info.getId())
                .setIncome(0)
                .setMId(info.getMId())
                .setPos(info.getPos())
                .setBuildingName(info.getName());
        return builder.build();
    }

    //Proto Building
    public Gs.BuildingIncome.UnLineIncome recordToProto(OffLineBuildingRecord record, int mid, Gs.MiniIndex miniIndex, String bName){//Transaction records transferred to proto
        Gs.BuildingIncome.UnLineIncome.Builder builder = Gs.BuildingIncome.UnLineIncome.newBuilder();
        builder.setBuildingId(Util.toByteString(record.bid))
                .setIncome(record.n * record.price)
                .setMId(mid)
                .setPos(miniIndex)
                .setBuildingName(bName);
        return builder.build();
    }

    /*Flight prediction results proto*/
    public Gs.ForecastIncome forecastIncomeToProto( List<OffLineFlightRecord> playerFlightForecast){
        Gs.ForecastIncome.Builder builder = Gs.ForecastIncome.newBuilder();
        long sum=0;
        for (OffLineFlightRecord record : playerFlightForecast) {
            Gs.ForecastIncome.FlightForecast.Builder unLineIncome = Gs.ForecastIncome.FlightForecast.newBuilder();
            unLineIncome.setFlightCompany(record.data.getFlightCompany())
                    .setFlightDeptimeDate(record.data.getFlightDeptimeDate())
                    .setFlightNo(record.data.getFlightNo())
                    .setWin(record.win)
                    .setProfitOrLoss(record.profitOrLoss);
            sum += record.profitOrLoss;//Profit is positive and loss is negative
            builder.addFlightIncome(unLineIncome);
        }
        builder.setTotalIncome(sum);
        return builder.build();
    }
}
