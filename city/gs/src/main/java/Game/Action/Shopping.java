package Game.Action;

import Game.*;
import Game.Meta.*;
import Game.Util.GlobalUtil;
import Shared.LogDb;
import Shared.Package;
import Shared.Util;
import gs.Gs;
import gscode.GsCode;

import java.util.*;

public class Shopping implements IAction {
    public Shopping(int aiId) {
        this.aiId = aiId;
    }
    private int aiId;
    @Override
    public Set<Object> act(Npc npc) {
        Set<Object> buyerNpc = new HashSet<>();//用于一次性批量更新数据库
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
            int saleCount = ((IShelf)b).getSaleCount(chosenGoodMetaId);
            if(!((RetailShop)b).shelfHas(chosenGoodMetaId)||saleCount==0)//移除货架上该商品为0个的商品
                iterator.remove();
        }
      //再次判断建筑是否为空，为空则return
        if(buildings==null||buildings.size()==0){
            return null;
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
        if(wi==null||wi.size()==0){//再次判断是否为空
            return null;
        }
        WeightInfo chosen = wi.get(ProbBase.randomIdx(wi.stream().mapToInt(WeightInfo::getW).toArray()));

        Building sellShop = City.instance().getBuilding(chosen.bId);
        sellShop.addFlowCount();
        logger.info("chosen shop: " + sellShop.metaId() + " at: " + sellShop.coordinate());
        //TODO:计算旷工费
        double minersRatio = MetaData.getSysPara().minersCostRatio/10000;
        long minerCost = (long) Math.floor(chosen.price* minersRatio);
        int saleCount = ((IShelf) sellShop).getSaleCount(chosenGoodMetaId);//货架上的数量
        if(chosen.price+minerCost > npc.money()) {
        	//购买时所持金不足,行业涨薪指数 += 定价 - 所持金
        	int money=(int) ((chosen.price+minerCost)-npc.money());
        	City.instance().addIndustryMoney(npc.building().type(),money);

            npc.hangOut(sellShop);
            return null;
        }else if(saleCount<=0){//货架上无货物，不允许购买
            npc.hangOut(sellShop);
            return null;
        }
        else {
            //收益方和购买方都要计算旷工费
            npc.decMoney((int) (chosen.price+minerCost));
            Player owner = GameDb.getPlayer(sellShop.ownerId());
            owner.addMoney(chosen.price-minerCost);
            LogDb.playerIncome(owner.id(),chosen.price-minerCost,sellShop.type());
            ((IShelf)sellShop).delshelf(chosen.getItemKey(), 1, false);
            sellShop.updateTodayIncome(chosen.price-minerCost);
            //零售店货架数量改变，推送(只有货架上还有东西的时候推送)========yty
            sendShelfNotice(sellShop,chosen);
            GameDb.saveOrUpdate(Arrays.asList(npc, owner, sellShop));

//            City.instance().send(sellShop.coordinate().toGridIndex().toSyncRange(), Package.create(GsCode.OpCode.moneyChange_VALUE, Gs.MakeMoney.newBuilder().setBuildingId(Util.toByteString(sellShop.id())).setPos(sellShop.coordinate().toProto()).setItemId(chosen.meta.id).setMoney((int) (chosen.price-minerCost)).build()));

            Gs.IncomeNotify notify = Gs.IncomeNotify.newBuilder()
                    .setBuyer(Gs.IncomeNotify.Buyer.NPC)
                    .setBuyerId(Util.toByteString(npc.id()))
                    .setCost(chosen.price+minerCost)
                    .setCount(1)
                    .setType(Gs.IncomeNotify.Type.INSHELF)
                    .setBid(sellShop.metaId())
                    .setItemId(chosen.meta.id)
                    .build();
            GameServer.sendIncomeNotity(owner.id(),notify);

            GameServer.sendToAll(Package.create(GsCode.OpCode.makeMoneyInform_VALUE,Gs.MakeMoney.newBuilder()
                    .setBuildingId(Util.toByteString(chosen.bId))
                    .setMoney(chosen.price-minerCost)
                    .setPos(sellShop.toProto().getPos())
                    .setItemId(chosen.meta.id)
                    .build()
            ));

            LogDb.npcBuyInRetailCol(chosen.meta.id, chosen.price, chosen.getItemKey().producerId,    //消费记录不计算旷工费
                    chosen.qty,sellShop.ownerId(), chosen.buildingBrand,chosen.buildingQty);
            //获取品牌名
            BrandManager.BrandName brandName = BrandManager.instance().getBrand(owner.id(),chosen.meta.id).brandName;
            String goodName=brandName==null?owner.getCompanyName():brandName.getBrandName();
            // 记录商品评分
            double brandScore = GlobalUtil.getBrandScore(chosen.getItemKey().getTotalBrand(), chosen.meta.id);
            double goodQtyScore = GlobalUtil.getGoodQtyScore(chosen.getItemKey().getTotalQty(), chosen.meta.id, MetaData.getGoodQuality(chosen.meta.id));
            double score = ((brandScore + goodQtyScore) / 2);
            LogDb.npcBuyInShelf(npc.id(),owner.id(),1,chosen.price,chosen.getItemKey().producerId,   //消费记录不计算旷工费
                    chosen.bId,MetaItem.type(chosen.meta.id),chosen.meta.id,goodName,score);
            LogDb.buildingIncome(chosen.bId, npc.id(),chosen.price-minerCost, MetaItem.type(chosen.meta.id), chosen.meta.id);
            if(!GameServer.isOnline(owner.id())) {
                LogDb.sellerBuildingIncome(chosen.bId, sellShop.type(), owner.id(), 1, chosen.price, chosen.meta.id);//记录建筑收益详细信息
            }
            //矿工费用记录
            LogDb.minersCost(owner.id(),minerCost,MetaData.getSysPara().minersCostRatio);
            LogDb.npcMinersCost(npc.id(),minerCost,MetaData.getSysPara().minersCostRatio);
            //db操作 从外部挪进来
            //Set u = new HashSet(Arrays.asList(npc, owner, sellShop));
            //GameDb.saveOrUpdate(u);
            buyerNpc.addAll(Arrays.asList(npc,owner, sellShop));
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
            //更新剩余数量
            saleCount=((IShelf) sellShop).getSaleCount(chosenGoodMetaId);
            if(num/100.d<repeatBuyRetio&&saleCount>0){
            	//选出满足条件的商品后，走再次购物逻辑
                Set<Object> objects = repeatBuyGood(npc, chosen, mutilSpend);
                if(objects!=null&&objects.size()>0){
                    buyerNpc.addAll(objects);
                }
            }
            return buyerNpc;
        }
    }
    private Set<Object> repeatBuyGood(Npc npc,WeightInfo chosen,double mutilSpend){
        Set<Object> buyerNpc = new HashSet<>();
        Building sellShop = City.instance().getBuilding(chosen.bId);
        sellShop.addFlowCount();
        logger.info("chosen shop: " + sellShop.metaId() + " at: " + sellShop.coordinate());
        //TODO:计算旷工费
        double minersRatio = MetaData.getSysPara().minersCostRatio/10000;
        long minerCost = (long) Math.floor(chosen.price* minersRatio);
        //获取货架商品数量
        int saleCount = ((IShelf) sellShop).getSaleCount(chosen.meta.id);//货架上的数量
        if(chosen.price+minerCost> npc.money()) {
          	//购买时所持金不足,行业涨薪指数 += 定价（已包含旷工费） - 所持金
          	int money=(int) ((chosen.price+minerCost)-npc.money());
          	City.instance().addIndustryMoney(npc.building().type(),money);

          	npc.hangOut(sellShop);
          	return null;
          }else  if(saleCount<=0){
            npc.hangOut(sellShop);
            return null;
          }
          else {
              npc.decMoney((int) (chosen.price+minerCost));
              Player owner = GameDb.getPlayer(sellShop.ownerId());
              owner.addMoney(chosen.price-minerCost);
              LogDb.playerIncome(owner.id(), chosen.price-minerCost,sellShop.type());
              ((IShelf)sellShop).delshelf(chosen.getItemKey(), 1, false);
              sellShop.updateTodayIncome(chosen.price-minerCost);
              //零售店货架数量改变，推送(只有货架上还有东西的时候推送)========yty
              sendShelfNotice(sellShop,chosen);
              GameDb.saveOrUpdate(Arrays.asList(npc, owner, sellShop));
              Gs.IncomeNotify notify = Gs.IncomeNotify.newBuilder()
                      .setBuyer(Gs.IncomeNotify.Buyer.NPC)
                      .setBuyerId(Util.toByteString(npc.id()))
                      .setCost(chosen.price+minerCost)
                      .setCount(1)
                      .setType(Gs.IncomeNotify.Type.INSHELF)
                      .setBid(sellShop.metaId())
                      .setItemId(chosen.meta.id)
                      .build();
              GameServer.sendIncomeNotity(owner.id(),notify);

              GameServer.sendToAll(Package.create(GsCode.OpCode.makeMoneyInform_VALUE,Gs.MakeMoney.newBuilder()
                    .setBuildingId(Util.toByteString(chosen.bId))
                    .setMoney(chosen.price-minerCost)
                    .setPos(sellShop.toProto().getPos())
                    .setItemId(chosen.meta.id)
                    .build()
              ));

              LogDb.npcBuyInRetailCol(chosen.meta.id, chosen.price, chosen.getItemKey().producerId, //不包含旷工费
                      chosen.qty,sellShop.ownerId(), chosen.buildingBrand,chosen.buildingQty);
            double brandScore = GlobalUtil.getBrandScore(chosen.getItemKey().getTotalBrand(), chosen.meta.id);
            double goodQtyScore = GlobalUtil.getGoodQtyScore(chosen.getItemKey().getTotalQty(), chosen.meta.id, MetaData.getGoodQuality(chosen.meta.id));
            double score = ((brandScore + goodQtyScore) / 2);
              BrandManager.BrandName brandName = BrandManager.instance().getBrand(owner.id(),chosen.meta.id).brandName;
              String goodName=brandName==null?owner.getCompanyName():brandName.getBrandName();
              LogDb.npcBuyInShelf(npc.id(),owner.id(),1,chosen.price,chosen.getItemKey().producerId,//不包含旷工费
                      chosen.bId,MetaItem.type(chosen.meta.id),chosen.meta.id,goodName,score);
              LogDb.buildingIncome(chosen.bId, npc.id(), chosen.price-minerCost, MetaItem.type(chosen.meta.id), chosen.meta.id);
              if(!GameServer.isOnline(owner.id())) {
                LogDb.sellerBuildingIncome(chosen.bId, sellShop.type(), owner.id(), 1, chosen.price, chosen.meta.id);//记录建筑收益详细信息
              }
              //矿工费用记录
              LogDb.minersCost(owner.id(),minerCost,MetaData.getSysPara().minersCostRatio);
              LogDb.npcMinersCost(npc.id(),minerCost,MetaData.getSysPara().minersCostRatio);
              //db操作 从外部挪进来
             /* Set u = new HashSet(Arrays.asList(npc, owner, sellShop));
              GameDb.saveOrUpdate(u);*/
//            City.instance().send(sellShop.coordinate().toGridIndex().toSyncRange(), Package.create(GsCode.OpCode.moneyChange_VALUE, Gs.MakeMoney.newBuilder().setBuildingId(Util.toByteString(sellShop.id())).setPos(sellShop.coordinate().toProto()).setItemId(chosen.meta.id).setMoney((int) (chosen.price-minerCost)).build()));
              buyerNpc.addAll(Arrays.asList(npc,owner, sellShop));
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
              saleCount = ((IShelf) sellShop).getSaleCount(chosen.meta.id);//刷新货架上的数量
              if(num/100.d<repeatBuyRetio&&saleCount>0){
                  //递归购物
                  Set<Object> objects = repeatBuyGood(npc, chosen, mutilSpend);
                  if(objects!=null&&objects.size()>0){
                      buyerNpc.addAll(objects);
                  }
              }

              return buyerNpc;
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

    public void sendShelfNotice(Building sellShop,WeightInfo chosen){
        Shelf.Content content = ((IShelf) sellShop).getContent(chosen.getItemKey());
        if(content!=null){
            List<UUID> owerId = new ArrayList<>();
            owerId.add(sellShop.ownerId());
            Gs.salesNotice.Builder builder = Gs.salesNotice.newBuilder();
            builder.setBuildingId(Util.toByteString(sellShop.id()))
                    .setItemId(chosen.getItemKey().meta.id)
                    .setSelledCount(content.getCount())
                    .setSelledPrice(content.getPrice()).setAutoRepOn(content.isAutoReplenish())
                    .setProducerId(Util.toByteString(chosen.producerId));
            GameServer.sendTo(owerId, Package.create(GsCode.OpCode.salesNotice_VALUE, builder.build()));
        }
    }
}


