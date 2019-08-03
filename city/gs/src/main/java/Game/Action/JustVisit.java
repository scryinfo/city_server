package Game.Action;


import Game.*;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaData;
import Game.Meta.ProbBase;
import Shared.LogDb;
import Shared.Package;
import Shared.Util;
import gs.Gs;
import gscode.GsCode;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class JustVisit implements IAction {
    public JustVisit(int buildingType) {
        this.buildingType = buildingType;
    }

    private int buildingType;
    @Override
    public Set<Object> act(Npc npc) {
        logger.info("npc " + npc.id().toString() + " type " + npc.type() + " just visit building type " + buildingType + " located at: " + npc.buildingLocated().coordinate());
        //NPC成功购买住宅后,住宅状态保存24小时,在此期间不允许再次购买住宅
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
        }
        List<Building> buildings = npc.buildingLocated().getAllBuildingEffectMe(buildingType);
        if(buildings.isEmpty())
            return null;
        for (Building building : buildings) {
            logger.info("chosen building " + building.id().toString() + " located at: " + building.coordinate());
        }
        logger.info("building num: " + buildings.size());
        int[] buildingWeights = new int[buildings.size()];
        final int cost = (int) (npc.salary() * BrandManager.instance().spendMoneyRatioBuilding(buildingType));
        logger.info("cost: " + cost);
        int i = 0;
        Iterator<Building> iterator = buildings.iterator();
        List<Building> newBuildings=new ArrayList<Building>();
        while(iterator.hasNext()) {
            Building building = iterator.next();
//          double c = ((1.d + BrandManager.instance().buildingBrandScore(buildingType)/ 100.d) + (1.d + City.instance().buildingQtyScore(building.type(), building.quality()) / 100.d) + (1.d + 0)) /3*cost;
//          int r = (int) ((1.d-(building.cost() / c))*100000);
            double c = cost ;
            int r = 0;// (int) ((1.d-(building.cost() / c))*100000 *(1.d + (1.d-Building.distance(building, npc.buildingLocated())/(1.42*MetaData.getCity().x))/100.d));
            if(1.d-(building.cost() / c)>=0){
                r = (int) ((c/ building.cost() )*100000 *(1.d + (1.d-Building.distance(building, npc.buildingLocated())/(1.42*MetaData.getCity().x))/100.d))*(int)((BrandManager.instance().buildingBrandScore(buildingType) + City.instance().buildingQtyScore(building.type(), building.quality())) /400.d * 7 + 1);
            }else{
                r = 0;
            }
            if(r<=0){
                continue;
            }
            newBuildings.add(building);
            buildingWeights[i++] = r<0?0:r;
        }
        int idx = ProbBase.randomIdx(buildingWeights);
        if(newBuildings==null||newBuildings.size()==0){
            return null;
        }
        Building chosen = newBuildings.get(idx);
        logger.info("chosen building: " + chosen.id().toString() + " mId: " + chosen.metaId() + " which coord is: " + chosen.coordinate());
        if(npc.money() < chosen.cost()){
            npc.hangOut(chosen);
            chosen.addFlowCount();
            return null;
        }
        else {
            Player owner = GameDb.getPlayer(chosen.ownerId());
            long income = chosen.cost();//今日收入
            long pay = chosen.cost();
            if (chosen.type() == MetaBuilding.APARTMENT) {//需要多扣除矿工费用

                //TODO:暂时矿工费用是向下取整,矿工费用（商品基本费用*矿工费用比例）
                double minersRatio = MetaData.getSysPara().minersCostRatio/10000;
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
                //矿工费记录
               /* LogDb.minersCost(owner.id(),minerCost,MetaData.getSysPara().minersCostRatio);
                LogDb.npcMinersCost(npc.id(),minerCost,MetaData.getSysPara().minersCostRatio);*/
            }
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
            LogDb.npcRentApartment(npc.id(), owner.id(), 1, chosen.cost(), chosen.ownerId(),
                    chosen.id(), chosen.type(), chosen.metaId()); //不包含矿工费用
            if(!GameServer.isOnline(owner.id())) {
                LogDb.sellerBuildingIncome(chosen.id(), chosen.type(), owner.id(), 1, chosen.cost(), 0);
            }
            chosen.updateTodayIncome(income);
           // GameDb.saveOrUpdate(Arrays.asList(npc, owner, chosen));
            npc.goFor(chosen);
            return new HashSet<>(Arrays.asList(owner, npc, chosen));
        }
    }
}
