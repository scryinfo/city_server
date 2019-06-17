package Game.Action;

import Game.*;
import Game.Meta.*;
import Shared.LogDb;
import Shared.Util;
import gs.Gs;

import java.util.*;

public class Shopping implements IAction {
    public Shopping(int aiId) {
        this.aiId = aiId;
    }
    private int aiId;
    @Override
    public Set<Object> act(Npc npc) {
        logger.info("npc " + npc.id().toString() + " type " + npc.type() + " begin to shopping who located at: " + npc.buildingLocated().coordinate());
        List<Building> buildings = npc.buildingLocated().getAllBuildingEffectMe(MetaBuilding.RETAIL);
        if(buildings.isEmpty())
            return null;
        logger.info("building num: " + buildings.size());
        AIBuy ai = MetaData.getAIBuy(aiId);
        MetaGood.Type type = ai.random(BrandManager.instance().getGoodWeightRatioWithType());
        logger.info("choose good type: " + type.ordinal());
        AILux aiLux = MetaData.getAILux(npc.type());
        int lux = aiLux.random(BrandManager.instance().getGoodWeightRatioWithLux());
        logger.info("choose lux: " + lux);
        Set<Integer> goodMetaIds = new TreeSet<>();
        buildings.forEach(b->{
            RetailShop shop = (RetailShop)b;
            goodMetaIds.addAll(shop.getMetaIdsInShelf(type, lux));
        });
        logger.info("good meta ids this npc can buy: " + goodMetaIds);
        if(goodMetaIds.isEmpty())
            return null;
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
            double ratio = 0;
            if (sumBrandV != 0) {
                ratio = (double) brandV.get(goodMetaId) / (double) sumBrandV;
            }
            weight[i++] = (int) (BrandManager.instance().spendMoneyRatioGood(goodMetaId) * (1.d + ratio) * 100000);
        }
        logger.info("good weight : " + Arrays.toString(weight));
        int idx = ProbBase.randomIdx(weight);
        int chosenGoodMetaId = (int) goodMetaIds.toArray()[idx];
        logger.info("chosen: " + chosenGoodMetaId);
        Iterator<Building> iterator = buildings.iterator();
        while(iterator.hasNext()) {
            Building b = iterator.next();
            if(!((RetailShop)b).shelfHas(chosenGoodMetaId))
                iterator.remove();
        }
        List<WeightInfo> wi = new ArrayList<>();
        buildings.forEach(b->{
            int buildingBrand = BrandManager.instance().getBuilding(b.ownerId(), b.type());
          //double shopScore = (1 + buildingBrand / 100.d) + (1 + b.quality() / 100.d) + (1 + 100 - Building.distance(b, npc.buildingLocated())/4.d);
            int spend = (int) (npc.salary()*BrandManager.instance().spendMoneyRatioGood(chosenGoodMetaId));
            List<Shelf.SellInfo> sells = ((RetailShop)b).getSellInfo(chosenGoodMetaId);
            for (Shelf.SellInfo sell : sells) {
              //double goodSpendV = ((1 + BrandManager.instance().getGood(sell.producerId, chosenGoodMetaId) / 100.d) + (1 + sell.qty / 100.d) + shopScore)/3.d * spend;
            	double goodSpendV = spend;
              //int w = goodSpendV==0?0: (int) ((1 - sell.price / goodSpendV) * 100000);
                int w =0;// goodSpendV==0?0: (int) ((1 - sell.price / goodSpendV) * 100000 * (1 + (1-Building.distance(b, npc.buildingLocated())/(1.42*MetaData.getCity().x))/100.d));
                if(1 - sell.price / goodSpendV >= 0){
                	w=goodSpendV==0?0: (int) ((goodSpendV /sell.price) * 100000 * (1 + (1-Building.distance(b, npc.buildingLocated())/(1.42*MetaData.getCity().x))/100.d))*(int)((buildingBrand + b.quality() + BrandManager.instance().getGood(sell.producerId, chosenGoodMetaId)  + sell.qty) / 400.d * 7 + 1);
                }else{
                	w=0;
                }
                if(w<=0){
                    continue;
                }
                wi.add(new WeightInfo(b.id(), sell.producerId, sell.qty, w, sell.price, (MetaGood) sell.meta, buildingBrand, b.quality()));
            }
        });
        WeightInfo chosen = wi.get(ProbBase.randomIdx(wi.stream().mapToInt(WeightInfo::getW).toArray()));
        Building sellShop = City.instance().getBuilding(chosen.bId);
        sellShop.addFlowCount();
        logger.info("chosen shop: " + sellShop.metaId() + " at: " + sellShop.coordinate());
        //TODO:计算旷工费
        double minersRatio = MetaData.getSysPara().minersCostRatio/10000;
        long minerCost = (long) Math.floor(chosen.price* minersRatio);
        if(chosen.price+minerCost > npc.money()) {
        	//购买时所持金不足,行业涨薪指数 += 定价 - 所持金
        	int money=(int) ((chosen.price+minerCost)-npc.money());
        	City.instance().addIndustryMoney(npc.building().type(),money);

            npc.hangOut(sellShop);
            return null;
        }
        else {
            //收益方和购买方都要计算旷工费
            npc.decMoney((int) (chosen.price+minerCost));
            Player owner = GameDb.getPlayer(sellShop.ownerId());
            owner.addMoney(chosen.price-minerCost);
            LogDb.playerIncome(owner.id(),chosen.price-minerCost);
            ((IShelf)sellShop).delshelf(chosen.getItemKey(), 1, false);
            sellShop.updateTodayIncome(chosen.price-minerCost);
            GameDb.saveOrUpdate(Arrays.asList(npc, owner, sellShop));


            Gs.IncomeNotify notify = Gs.IncomeNotify.newBuilder()
                    .setBuyer(Gs.IncomeNotify.Buyer.NPC)
                    .setBuyerId(Util.toByteString(npc.id()))
                    .setCost(chosen.price)
                    .setCount(1)
                    .setType(Gs.IncomeNotify.Type.INSHELF)
                    .setBid(sellShop.metaId())
                    .setItemId(chosen.meta.id)
                    .build();
            GameServer.sendIncomeNotity(owner.id(),notify);
            LogDb.npcBuyInRetailCol(chosen.meta.id, chosen.price, chosen.getItemKey().producerId,    //消费记录不计算旷工费
                    chosen.qty,sellShop.ownerId(), chosen.buildingBrand,chosen.buildingQty);
            LogDb.npcBuyInShelf(npc.id(),owner.id(),1,chosen.price,chosen.getItemKey().producerId,   //消费记录不计算旷工费
                    chosen.bId,MetaItem.type(chosen.meta.id),chosen.meta.id);
            LogDb.buildingIncome(chosen.bId, npc.id(),chosen.price-minerCost, MetaItem.type(chosen.meta.id), chosen.meta.id);
            //矿工费用记录
            LogDb.minersCost(owner.id(),minerCost,MetaData.getSysPara().minersCostRatio);
            LogDb.npcMinersCost(npc.id(),minerCost,MetaData.getSysPara().minersCostRatio);
            //db操作 从外部挪进来
            Set u = new HashSet(Arrays.asList(npc, owner, sellShop));
            GameDb.saveOrUpdate(u);
            
            //再次购物
            double spend=MetaData.getGoodSpendMoneyRatio(chosen.meta.id);
            //工资区分失业与否
            int salary=npc.building().singleSalary();
            //失业则是失业金
            if(npc.getStatus()==1){
            	salary=(int) (City.instance().getAvgIndustrySalary()*MetaData.getCity().insuranceRatio);
            }
            double mutilSpend=salary*spend;
            mutilSpend-=chosen.price;
            double repeatBuyRetio=mutilSpend/salary*spend;
            Random random = new Random();
            int num = random.nextInt(101);
            if(num/100.d<repeatBuyRetio){
            	//选出满足条件的商品后，走再次购物逻辑
                repeatBuyGood(npc,chosen,mutilSpend);
            }
            return null;
        }
    }
    private Set<Object> repeatBuyGood(Npc npc,WeightInfo chosen,double mutilSpend){
          Building sellShop = City.instance().getBuilding(chosen.bId);
          sellShop.addFlowCount();
          logger.info("chosen shop: " + sellShop.metaId() + " at: " + sellShop.coordinate());
        //TODO:计算旷工费
        double minersRatio = MetaData.getSysPara().minersCostRatio/10000;
        long minerCost = (long) Math.floor(chosen.price* minersRatio);

        if(chosen.price+minerCost> npc.money()) {
          	//购买时所持金不足,行业涨薪指数 += 定价（已包含旷工费） - 所持金
          	int money=(int) ((chosen.price+minerCost)-npc.money());
          	City.instance().addIndustryMoney(npc.building().type(),money);

            npc.hangOut(sellShop);
            return null;
          }
          else {
              npc.decMoney((int) (chosen.price+minerCost));
              Player owner = GameDb.getPlayer(sellShop.ownerId());
              owner.addMoney(chosen.price-minerCost);
              LogDb.playerIncome(owner.id(), chosen.price-minerCost);
              ((IShelf)sellShop).delshelf(chosen.getItemKey(), 1, false);
              sellShop.updateTodayIncome(chosen.price-minerCost);
              GameDb.saveOrUpdate(Arrays.asList(npc, owner, sellShop));
              Gs.IncomeNotify notify = Gs.IncomeNotify.newBuilder()
                      .setBuyer(Gs.IncomeNotify.Buyer.NPC)
                      .setBuyerId(Util.toByteString(npc.id()))
                      .setCost(chosen.price)
                      .setCount(1)
                      .setType(Gs.IncomeNotify.Type.INSHELF)
                      .setBid(sellShop.metaId())
                      .setItemId(chosen.meta.id)
                      .build();
              GameServer.sendIncomeNotity(owner.id(),notify);
              LogDb.npcBuyInRetailCol(chosen.meta.id, chosen.price, chosen.getItemKey().producerId, //不包含旷工费
                      chosen.qty,sellShop.ownerId(), chosen.buildingBrand,chosen.buildingQty);
              LogDb.npcBuyInShelf(npc.id(),owner.id(),1,chosen.price,chosen.getItemKey().producerId,//不包含旷工费
                      chosen.bId,MetaItem.type(chosen.meta.id),chosen.meta.id);
              LogDb.buildingIncome(chosen.bId, npc.id(), chosen.price-minerCost, MetaItem.type(chosen.meta.id), chosen.meta.id);
              //矿工费用记录
              LogDb.minersCost(owner.id(),minerCost,MetaData.getSysPara().minersCostRatio);
              LogDb.npcMinersCost(npc.id(),minerCost,MetaData.getSysPara().minersCostRatio);
              //db操作 从外部挪进来
              Set u = new HashSet(Arrays.asList(npc, owner, sellShop));
              GameDb.saveOrUpdate(u);
              
              //再次购物
              double spend=MetaData.getGoodSpendMoneyRatio(chosen.meta.id);
              //工资区分失业与否
              int salary=npc.building().singleSalary();
              //失业则是失业金
              if(npc.getStatus()==1){
              	salary=(int) (City.instance().getAvgIndustrySalary()*MetaData.getCity().insuranceRatio);
              }
            //double mutilSpend=salary*spend;
              mutilSpend-=chosen.price;//不断减，直到超出范围则不再次购物
              double repeatBuyRetio=mutilSpend/salary*spend;
              Random random = new Random();
              int num = random.nextInt(101);
              if(num/100.d<repeatBuyRetio){
                  //递归购物
                  repeatBuyGood(npc,chosen,mutilSpend);
              }
              
              return null;
          }
    }
    
    private static final class WeightInfo {
        public WeightInfo(UUID bId, UUID producerId, int qty, int w, int price, MetaGood meta, int buildingBrand, int buildingQty) {
            this.bId = bId;
            this.producerId = producerId;
            this.qty = qty;
            this.w = w;
            this.price = price;
            this.meta = meta;
            this.buildingBrand = buildingBrand;
            this.buildingQty = buildingQty;
        }
        int getW() {
            return w;
        }
        UUID bId;
        UUID producerId;
        int qty;
        int w;
        int price;
        int buildingBrand;
        int buildingQty;
        MetaGood meta;
        public ItemKey getItemKey() {
            return new ItemKey(meta, producerId, qty,producerId);
        }
    }
}


