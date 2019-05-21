package Game.Action;


import Game.*;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaData;
import Game.Meta.ProbBase;
import Shared.LogDb;
import Shared.Util;
import gs.Gs;

import java.util.*;

public class JustVisit implements IAction {
    public JustVisit(int buildingType) {
        this.buildingType = buildingType;
    }

    private int buildingType;
    @Override
    public Set<Object> act(Npc npc) {
        logger.info("npc " + npc.id().toString() + " type " + npc.type() + " just visit building type " + buildingType + " located at: " + npc.buildingLocated().coordinate());
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
        while(iterator.hasNext()) {
            Building building = iterator.next();
//          double c = ((1.d + BrandManager.instance().buildingBrandScore(buildingType)/ 100.d) + (1.d + City.instance().buildingQtyScore(building.type(), building.quality()) / 100.d) + (1.d + 0)) /3*cost;
//          int r = (int) ((1.d-(building.cost() / c))*100000);
            double c = ((BrandManager.instance().buildingBrandScore(buildingType) + City.instance().buildingQtyScore(building.type(), building.quality())) /400.d * 7 + 1) * cost ;
            int r = (int) ((1.d-(building.cost() / c))*100000 *(1.d + (1.d-Building.distance(building, npc.buildingLocated())/(1.42*MetaData.getCity().x))/100.d));
            buildingWeights[i++] = r<0?0:r;
        }
        int idx = ProbBase.randomIdx(buildingWeights);
        Building chosen = buildings.get(idx);
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
                long minerCost = (long) Math.floor(chosen.cost() * MetaData.getSysPara().minersCostRatio);
                income -= minerCost;
                pay += minerCost;
                GameServer.sendIncomeNotity(owner.id(),Gs.IncomeNotify.newBuilder()
                        .setBuyer(Gs.IncomeNotify.Buyer.NPC)
                        .setBuyerId(Util.toByteString(npc.id()))
                        .setCost(pay)
                        .setType(Gs.IncomeNotify.Type.RENT_ROOM)
                        .setBid(chosen.metaId())
                        .build());
                //矿工费记录
                LogDb.minersCost(owner.id(),minerCost,MetaData.getSysPara().minersCostRatio);
                LogDb.npcMinersCost(npc.id(),minerCost,MetaData.getSysPara().minersCostRatio);
            }
            owner.addMoney(income);
            npc.decMoney((int) pay);
            LogDb.playerIncome(owner.id(), income);
            LogDb.incomeVisit(owner.id(),chosen.type(),income,chosen.id(),npc.id());
            LogDb.buildingIncome(chosen.id(),npc.id(),income,0,0);
            LogDb.npcRentApartment(npc.id(),owner.id(),1,chosen.cost(),chosen.ownerId(),
                    chosen.id(),chosen.type(),chosen.metaId()); //不包含矿工费用
            chosen.updateTodayIncome(income);
            GameDb.saveOrUpdate(Arrays.asList(npc, owner, chosen));
            npc.goFor(chosen);
            return new HashSet<>(Arrays.asList(owner, npc, chosen));
        }
    }
}
