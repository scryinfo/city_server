package Game.Action;


import Game.*;
import Game.Meta.AIBuilding;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaData;
import Game.Meta.ProbBase;
import Game.Util.GlobalUtil;
import Shared.LogDb;
import Shared.Package;
import Shared.Util;
import gs.Gs;
import gscode.GsCode;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class GoApartment implements IAction {
    public GoApartment(int buildingType) {
        this.buildingType = buildingType;
    }

    private int buildingType;
    @Override
    public Set<Object> act(Npc npc) {
        logger.info("GoApartment buildingType" + buildingType + " npc id " + npc.id().toString() + " npc type " + npc.type() + " npc located at: " + npc.buildingLocated().coordinate());
        //After the NPC successfully purchases the house, the state of the house is kept for 24 hours, during which time it is not allowed to buy another house
        if(npc.hasApartment()){
            if(System.currentTimeMillis()-npc.getBuyApartmentTs()<TimeUnit.HOURS.toMillis(24)){
                Building b=City.instance().getBuilding(npc.getApartment().id());
                if(b==null||b.getState()== Gs.BuildingState.SHUTDOWN_VALUE){
                    if(npc.getStatus()==0){
                        npc.goWork();
                    }
                }
                return new HashSet<>(Arrays.asList(npc));
            }else{
                if(npc.getStatus()==0){
                    npc.goWork();
                }
            }
            return null;
        }
        List<Building> buildings = npc.buildingLocated().getAllBuildingEffectMe(buildingType);
        if(buildings.isEmpty())
            return null;
        logger.info("GoApartment buildings num: " + buildings.size());

        //Actual residential shopping expectation = residential shopping expectation * (1 + mean value of the city's residential popularity / mean value of the city's popularity) * (1 + mean value of the city's residential quality / mean value of the city's quality)
        double realApartmentSpend=MetaData.getCostSpendRatio(buildingType)* (1 +  AiBaseAvgManager.getInstance().getBrandMapVal(MetaBuilding.APARTMENT) /AiBaseAvgManager.getInstance().getAllBrandAvg()) * (1 +  AiBaseAvgManager.getInstance().getQualityMapVal(MetaBuilding.APARTMENT) / AiBaseAvgManager.getInstance().getAllQualityAvg());
        //Actual total shopping expectation = actual residential shopping expectation + all actual shopping expectation for a certain commodity (invented)
        int allCostSpend=MetaData.getAllCostSpendRatio();
        //NPC residential expected consumption = urban wage standard * (actual residential shopping expectation / actual total shopping expectation)
        final int cost = (int) (npc.salary() * (realApartmentSpend/allCostSpend));

        //Residential mobility options
        Map<Building,Double> moveKnownMap=City.instance().getMoveKnownaApartmentMap();
        //Randomly select 3 homes to add to the candidate list
        Map<Building,Double> moveKnownBak=getRandomN(moveKnownMap,3);
        moveKnownBak.forEach((k,v)->{
            double r= v * (2 - Building.distance(k, npc.buildingLocated())) / 160;
            moveKnownBak.put(k,r);
        });
        //Randomly select one and move to the house
        Map<Building,Double> moveKnownSelect=getRandomN(moveKnownBak,1);
        List<Building> moveKnownList=new ArrayList<>(moveKnownSelect.keySet());
        Building moveKnownChosen=moveKnownList.get(0);
        npc.goFor(moveKnownChosen);

        //Residential purchase options
        Map<Building,Double> buyKnownValueMap=new HashMap<Building,Double>();
        moveKnownMap.forEach((k,v)->{
            //buyKnownValue = NPC residential expected consumption / selling price * 10000
            double buyKnownValue = cost / k.cost() * 10000;
            buyKnownValueMap.put(k,buyKnownValue);
        });
        //Randomly select 3 homes to add to the candidate list
        Map<Building,Double> buyKnownBak=getRandomNN(buyKnownValueMap,3,cost,10);
        //If there is no alternative, then stay in place
        if(buyKnownBak==null||buyKnownBak.size()==0){
            return null;
        }
        buyKnownBak.forEach((k,v)->{
            double r= v * (2 - Building.distance(k, npc.buildingLocated())) / 160;
            buyKnownBak.put(k,r);
        });
        //Otherwise, randomly select one of them
        Map<Building,Double> buyKnownSelect=getRandomN(buyKnownBak,1);

        List<Building> chosenList=new ArrayList<>(buyKnownSelect.keySet());
        Building chosen = chosenList.get(0);
        logger.info("chosen apartment building: " + chosen.id().toString() + " mId: " + chosen.metaId() + " which coord is: " + chosen.coordinate());

        //If the NPC selects a residence other than the current location, the current location of the NPC changes and moves to the residence
        if(!chosen.id().equals(moveKnownChosen.id())){
            npc.goFor(chosen);
        }

        if(npc.money() < chosen.cost()){
            npc.hangOut(chosen);
            chosen.addFlowCount();
            return null;
        }else {
            Player owner = GameDb.getPlayer(chosen.ownerId());
            long income = chosen.cost();//Today's income
            long pay = chosen.cost();

            //TODO:Temporary miner's cost is rounded down, miner's cost (commodity basic cost * miner's cost ratio)
            double minersRatio = MetaData.getSysPara().minersCostRatio;
            long minerCost = (long) Math.floor(chosen.cost() * minersRatio);
            income -= minerCost;
            pay += minerCost;
            Gs.IncomeNotify notify = Gs.IncomeNotify.newBuilder()
                    .setBuyer(Gs.IncomeNotify.Buyer.NPC)
                    .setBuyerId(Util.toByteString(npc.id()))
                    .setCost(chosen.cost())
                    .setType(Gs.IncomeNotify.Type.RENT_ROOM)
                    .setBid(chosen.metaId())
                    .build();
            GameServer.sendIncomeNotity(owner.id(), notify);
            //Miner's fee records
           /* LogDb.minersCost(owner.id(),minerCost,MetaData.getSysPara().minersCostRatio);
            LogDb.npcMinersCost(npc.id(),minerCost,MetaData.getSysPara().minersCostRatio);*/

            owner.addMoney(income);
            npc.decMoney((int) pay);
            npc.setBuyApartmentTs(System.currentTimeMillis());

            GameServer.sendToAll(Package.create(GsCode.OpCode.makeMoneyInform_VALUE,Gs.MakeMoney.newBuilder()
                    .setBuildingId(Util.toByteString(chosen.id()))
                    .setMoney(income)
                    .setPos(chosen.toProto().getPos())
                    .setItemId(0)
                    .build()
            ));

            LogDb.playerIncome(owner.id(), income,chosen.type());
            LogDb.incomeVisit(owner.id(),chosen.type(),income,chosen.id(),npc.id());
            LogDb.buildingIncome(chosen.id(),npc.id(),income,0,0);
            // Building Prosperity Building Rating
            Apartment apartment = (Apartment) chosen;
            int prosperityScore = (int) ProsperityManager.instance().getBuildingProsperityScore(chosen);
            double brandScore = GlobalUtil.getBrandScore(apartment.getTotalBrand(), chosen.type());
            double retailScore = GlobalUtil.getBuildingQtyScore(apartment.getTotalQty(), chosen.type());
            int curRetailScore = (int) ((brandScore + retailScore) / 2);
            LogDb.npcRentApartment(npc.id(), owner.id(), 1, chosen.cost(), chosen.ownerId(),
                    chosen.id(), chosen.type(), chosen.metaId(),curRetailScore,prosperityScore,apartment.getTotalBrand(),apartment.getTotalQty()); //Does not include miner fees
            if(!GameServer.isOnline(owner.id())) {
                LogDb.sellerBuildingIncome(chosen.id(), chosen.type(), owner.id(), 1, chosen.cost(), 0);
            }
            chosen.updateTodayIncome(income);

            //The behavior after payment is randomly selected according to the weight of w3 w4 w5
            int id = npc.chooseId();
            AIBuilding aiBuilding = MetaData.getAIBuilding(id);
            if(aiBuilding == null)
                return null;
            IAction action = aiBuilding.randomAgain(aiBuilding,id);
            action.act(npc);

            return new HashSet<>(Arrays.asList(owner, npc, chosen));
        }
    }
    //Randomly select n homes to add to the candidate list
    private Map<Building,Double> getRandomN(Map<Building,Double> map,int n){
        Map<Building,Double> newMap=new HashMap<>();
        List<Building> keyList=new ArrayList<>(map.keySet());
        List<Double> list= new ArrayList<>(map.values());
        double[] doubles=Util.toDoubleArray(list);
        for (int i=0;i<n;i++){
            int j=Util.randomIdx(doubles);
            newMap.put(keyList.get(j),list.get(j));
        }
        return newMap;
    }
    private Map<Building,Double> getRandomNN(Map<Building,Double> map,int n,int cost,int limit){
        Map<Building,Double> newMap=new HashMap<>();
        List<Building> keyList=new ArrayList<>(map.keySet());
        List<Double> list= new ArrayList<>(map.values());
        double[] doubles=Util.toDoubleArray(list);
        for (int i=0;i<limit;i++){
            int j=Util.randomIdx(doubles);
            //Randomly arrived houses meet the vacancies and the selling price <= NPC residences are expected to be consumed.
            Building b=keyList.get(j);
            Apartment apartment=(Apartment)b;
            logger.info("chosen apartment building: " + b.id().toString() + " renters: " + apartment.getRenterNum() + " capacity: " + apartment.getCapacity()+ " apartment.cost: " + apartment.cost() + " cost: " + cost);
            if((apartment.getRenterNum()<apartment.getCapacity())&&(apartment.cost()<=cost)){
                n--;
                if(n<0){
                    break;
                }
                newMap.put(b,list.get(j));
            }
        }
        return newMap;
    }
}
