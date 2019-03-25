package Game.Action;


import Game.*;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaItem;
import Game.Meta.ProbBase;
import Shared.LogDb;
import Shared.Package;
import Shared.Util;
import gs.Gs;
import gscode.GsCode;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class JustVisit implements IAction {
    public JustVisit(int buildingType) {
        this.buildingType = buildingType;
    }

    private int buildingType;
    @Override
    public void act(Npc npc) {
        logger.info("npc " + npc.id().toString() + " type " + npc.type() + " just visit building type " + buildingType + " located at: " + npc.buildingLocated().coordinate());
        List<Building> buildings = npc.buildingLocated().getAllBuildingEffectMe(buildingType);
        if(buildings.isEmpty())
            return;
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
            double c = ((1.d + BrandManager.instance().buildingBrandScore(buildingType)/ 100.d) + (1.d + City.instance().buildingQtyScore(building.type(), building.quality()) / 100.d) + (1.d + 0)) /3*cost;
            int r = (int) ((1.d-(building.cost() / c))*100000);
            buildingWeights[i++] = r<0?0:r;
        }
        int idx = ProbBase.randomIdx(buildingWeights);
        Building chosen = buildings.get(idx);
        logger.info("chosen building: " + chosen.id().toString() + " mId: " + chosen.metaId() + " which coord is: " + chosen.coordinate());
        if(npc.money() < chosen.cost()){
            npc.hangOut(chosen);
        }
        else {
            Player owner = GameDb.queryPlayer(chosen.ownerId());
            owner.addMoney(chosen.cost());
            npc.decMoney(chosen.cost());
            LogDb.incomeVisit(owner.id(),chosen.type(),chosen.cost(),chosen.id(),npc.id());
            LogDb.buildingIncome(chosen.id(),npc.id(),chosen.cost(),0,0);
            LogDb.npcRentApartment(npc.id(),owner.id(),1,chosen.cost(),chosen.ownerId(),
                    chosen.id(),chosen.type(),chosen.metaId());
            chosen.updateTodayIncome(chosen.cost());

            GameDb.saveOrUpdate(Arrays.asList(npc, owner, chosen));

            if (chosen.type() == MetaBuilding.APARTMENT) {
                GameServer.sendIncomeNotity(owner.id(),Gs.IncomeNotify.newBuilder()
                        .setBuyer(Gs.IncomeNotify.Buyer.NPC)
                        .setBuyerId(Util.toByteString(npc.id()))
                        .setCost(chosen.cost())
                        .setType(Gs.IncomeNotify.Type.RENT_ROOM)
                        .setBid(chosen.metaId())
                        .build());
            }
            npc.goFor(chosen);
        }
    }
}
