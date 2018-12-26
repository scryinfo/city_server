package Game.Action;


import Game.*;
import Shared.LogDb;
import org.apache.log4j.Logger;

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
        if(this.buildingType == MetaBuilding.APARTMENT && npc.hasApartment())
            return;
        logger.info("npc " + npc.id().toString() + " just visit building type " + buildingType + " located at: " + npc.buildingLocated().coordinate());
        List<Building> buildings = npc.buildingLocated().getAllBuildingEffectMe(buildingType);
        if(buildings.isEmpty())
            return;
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
            LogDb.incomeVisit(owner.id(),owner.money(),chosen.cost(),chosen.id(),npc.id());
            GameDb.saveOrUpdate(Arrays.asList(npc, chosen));
            npc.goFor(chosen);
        }
    }
}
