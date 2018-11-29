package Game.Action;

import Game.*;

import java.util.*;

public class Shopping implements IAction {
    public Shopping(int aiId) {
        this.aiId = aiId;
    }
    private int aiId;
    @Override
    public void act(Npc npc) {
        List<Building> buildings = npc.buildingLocated().getAllBuildingEffectMe(MetaBuilding.RETAIL);
        if(buildings.isEmpty())
            return;
        AIBuy ai = MetaData.getAIBuy(aiId);
        MetaGood.Type type = ai.random(BrandManager.instance().getGoodWeightRatioWithType());
        AILux aiLux = MetaData.getAILux(npc.type());
        int lux = aiLux.random(BrandManager.instance().getGoodWeightRatioWithLux());
        Set<Integer> goodMetaIds = new TreeSet<>();
        buildings.forEach(b->{
            RetailShop shop = (RetailShop)b;
            goodMetaIds.addAll(shop.getMetaIdsInShelf(type, lux));
        });
        Map<Integer, Integer> brandV = new HashMap<>();
        int sumBrandV = 0;
        for (Integer goodMetaId : goodMetaIds) {
            int v = BrandManager.instance().getGood(goodMetaId);
            brandV.put(goodMetaId, v);
            sumBrandV += v;
        }
        int[] weight = new int[goodMetaIds.size()];
        int i = 0;
        for (Integer goodMetaId : goodMetaIds) {
            weight[i++] = (int) (BrandManager.instance().spendMoneyRatioGood(goodMetaId) * (1.d + (double)brandV.get(goodMetaId) / (double)sumBrandV));
        }
        int idx = ProbBase.randomIdx(weight);
        int chosenGoodMetaId = (int) goodMetaIds.toArray()[idx];
        Iterator<Building> iterator = buildings.iterator();
        while(iterator.hasNext()) {
            Building b = iterator.next();
            if(!((RetailShop)b).shelfHas(chosenGoodMetaId))
                iterator.remove();
        }
        List<WeightInfo> wi = new ArrayList<>();
        buildings.forEach(b->{
            double shopScore = (1 + BrandManager.instance().getBuilding(b.ownerId(), b.type()) / 100.d) + (1 + b.quality() / 100.d) + (1 + 100 - Building.distance(b, npc.buildingLocated())/4.d);
            int spend = (int) (npc.salary()*BrandManager.instance().spendMoneyRatioGood(chosenGoodMetaId));
            List<Shelf.SellInfo> sells = ((RetailShop)b).getSellInfo(chosenGoodMetaId);
            for (Shelf.SellInfo sell : sells) {
                double goodSpendV = ((1 + BrandManager.instance().getGood(sell.producerId, chosenGoodMetaId) / 100.d) + (1 + sell.qty / 100.d) + shopScore)/3.d * spend;
                int w = goodSpendV==0?0: (int) ((1 - sell.price / goodSpendV) * 100000);
                wi.add(new WeightInfo(b.id(), sell.producerId, sell.qty, w, sell.price, (MetaGood) sell.meta));
            }
        });
        WeightInfo chosen = wi.get(ProbBase.randomIdx(wi.stream().mapToInt(WeightInfo::getW).toArray()));
        Building sellShop = City.instance().getBuilding(chosen.bId);
        if(chosen.price > npc.money())
            npc.hangOut(sellShop);
        else {
            npc.decMoney(chosen.price);
            Player owner = GameDb.queryPlayer(sellShop.ownerId());
            owner.addMoney(chosen.price);
            ((IShelf)sellShop).delshelf(chosen.getItemKey(), 1);
            GameDb.saveOrUpdate(Arrays.asList(npc, owner, sellShop));
        }
    }
    private static final class WeightInfo {
        public WeightInfo(UUID bId, UUID producerId, int qty, int w, int price, MetaGood meta) {
            this.bId = bId;
            this.producerId = producerId;
            this.qty = qty;
            this.w = w;
            this.price = price;
            this.meta = meta;
        }
        int getW() {
            return w;
        }
        UUID bId;
        UUID producerId;
        int qty;
        int w;
        int price;
        MetaGood meta;
        public ItemKey getItemKey() {
            return new ItemKey(meta, producerId, qty);
        }
    }
}
