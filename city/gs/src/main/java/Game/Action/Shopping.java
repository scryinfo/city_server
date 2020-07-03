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
        Set<Object> buyerNpc = new HashSet<>();//Used to update the database in one batch
        logger.info("npc " + npc.id().toString() + " type " + npc.type() + " begin to shopping who located at: " + npc.buildingLocated().coordinate());
        List<Building> buildings = npc.buildingLocated().getAllBuildingEffectMe(MetaBuilding.RETAIL);
        if(buildings.isEmpty())
            return null;
        logger.info("Shopping building num: " + buildings.size());
        AIBuy ai = MetaData.getAIBuy(aiId);
        MetaGood.Type type = ai.random(BrandManager.instance().getGoodWeightRatioWithType());
        AILux aiLux = MetaData.getAILux(npc.type());
        int lux = aiLux.random(BrandManager.instance().getGoodWeightRatioWithLux());
        logger.info("choose good type: " + type.ordinal()+" lux: " + lux);
        //Obtain the corresponding NPC commodity category + luxury expected consumption and related NPC commodity category expected consumption
        City.GoodFilter filter=new City.GoodFilter();
        filter.type=type.ordinal();
        filter.lux=lux;
        Map<City.GoodFilter,Set<City.GoodSellInfo>> retailShopGoodMap=City.instance().getRetailShopGoodMap();
        Set<City.GoodSellInfo> goodSellInfos=retailShopGoodMap.get(filter);
            if(goodSellInfos==null||goodSellInfos.size()==0){
            return null;
        }
        //Actual total shopping expectation = actual residential shopping expectation + all actual shopping expectation for a certain commodity (invented)
        int allCostSpend=MetaData.getAllCostSpendRatio();
        int sumGoodSpendRatio=0;

        Map<Building,Double> moveKnownMap=new HashMap<Building,Double>();
        Map<ItemKey,City.GoodSellInfo> buyKnownValueMap=new HashMap<ItemKey,City.GoodSellInfo>();
        AISelect aiSelect = MetaData.getAISelect(aiId);
        for(City.GoodSellInfo goodSellInfo:goodSellInfos){
            ItemKey itemKey=goodSellInfo.itemKey;
            int goodMetaId=itemKey.meta.id;
            //Actual shopping expectation of a certain commodity (invented) = shopping expectation of commodity * (1 + average value of a certain product's popularity in the city / average value of a city's popularity) * (1 + average value of a certain product quality in the city / average value of the whole city)
            double realGoodSpend=MetaData.getCostSpendRatio(goodMetaId)* (1 +  AiBaseAvgManager.getInstance().getBrandMapVal(goodMetaId) /AiBaseAvgManager.getInstance().getAllBrandAvg()) * (1 +  AiBaseAvgManager.getInstance().getQualityMapVal(goodMetaId) / AiBaseAvgManager.getInstance().getAllQualityAvg());
            //NPC Commodity Expected Expenditure = Urban Wage Standard * (Actual Shopping Expectation for Certain Products / Actual Total Shopping Expectation)
            final int subCost = (int) (npc.salary() * (realGoodSpend/allCostSpend));
            sumGoodSpendRatio+=MetaData.getCostSpendRatio(goodMetaId);

            logger.info("goodSellInfo goodSellInfo.b: " + goodSellInfo.b+" goodSellInfo.moveKnownValue: "+goodSellInfo.moveKnownValue);
            moveKnownMap.put(goodSellInfo.b,goodSellInfo.moveKnownValue);

            //int wn=MetaData.getAISelectGood(goodMetaId);0
            //buyKnownValue = (1 + gid_xxxx/100) * NPC Commodity Expected Consumption / Selling Price * (1 + product quality / average value of a certain commodity in the city) * (1 + product awareness / average value of a certain commodity in the city) * 100
            Shelf.Content content=goodSellInfo.content;
            double buyKnownValue=(1 + aiSelect.random()/100d) * subCost /content.getPrice() * (1 +  AiBaseAvgManager.getInstance().getBrandMapVal(goodMetaId) /AiBaseAvgManager.getInstance().getAllBrandAvg()) * (1 +  AiBaseAvgManager.getInstance().getQualityMapVal(goodMetaId) / AiBaseAvgManager.getInstance().getAllQualityAvg()) * 100;
            goodSellInfo.cost=subCost;
            goodSellInfo.buyKnownValue=buyKnownValue;
            buyKnownValueMap.put(itemKey,goodSellInfo);
        }
        // NPC Commodity Category + Luxury Expected Consumption = Urban Wage Standard * (Total of all actual shopping expectations of an item (invented) and total actual shopping expectations)
        int cost = (int) (npc.salary() * (sumGoodSpendRatio*1d/allCostSpend));

        //Retail store mobile options
        //Randomly select 3 retail stores to join the alternate list
        Map<Building,Double> moveKnownBak=getRandomN(moveKnownMap,3);
        moveKnownBak.forEach((k,v)->{
            //Alternate retail store's mobile selection weight = moveKnownValue * (2-(ABS (NPC's current location. x-retail store coordinates. x) + ABS (NPC's current location. y-retail store coordinates. y)) / 160)
            double r= v * (2 - Building.distance(k, npc.buildingLocated())) / 160;
            moveKnownBak.put(k,r);
        });
        //Randomly select one and move to the retail store
        Map<Building,Double> moveKnownSelect=getRandomN(moveKnownBak,1);
        List<Building> moveKnownList=new ArrayList<>(moveKnownSelect.keySet());
        Building moveKnownChosen=moveKnownList.get(0);
        npc.goFor(moveKnownChosen);

        //Order purchase options
        //Randomly select 10 products to join the order list
        Map<ItemKey,City.GoodSellInfo> buyKnownBak=getRandomNN(buyKnownValueMap,10,20);
        //If there is no alternative, then stay in place
        if(buyKnownBak==null||buyKnownBak.size()==0){
            return null;
        }
        buyKnownBak.forEach((k,v)->{
            //Alternative product purchase selection weight = buyKnownValue * (2-(ABS (NPC current location. x-retail store coordinates. x) + ABS (NPC current location. y-retail store coordinates. y)) / 160)
            double r= v.buyKnownValue * (2 - Building.distance(v.b, npc.buildingLocated())) / 160;
            //Purchase quantity of goods Random purchase quantity (random between 0-maximum purchase quantity)
            Random rand = new Random();
            int buyNum = rand.nextInt(v.content.getN()+1);
            v.r=r;
            v.buyNum=buyNum;
            buyKnownBak.put(k,v);
        });

        //Order purchase
        for (Map.Entry<ItemKey, City.GoodSellInfo> entry:buyKnownBak.entrySet()) {
            ItemKey itemKey=entry.getKey();
            City.GoodSellInfo goodSellInfo=entry.getValue();

            //This shopping fund = min (NPC commodity category + luxury consumption expectation, gold held)
            long buyMoney=npc.money();
            if(cost<npc.money()){
                buyMoney=cost*5;
            }

            if(goodSellInfo.buyNum==0){
                continue;
            }
            //If this shopping fund> product selling price * random amount, the purchase is successful
            int spend1=goodSellInfo.content.getPrice()*goodSellInfo.buyNum;
            //If this shopping fund / product price> 1 then the purchase was successful
            double spend2=buyMoney*1d/goodSellInfo.content.getPrice();
            if(buyMoney>spend1){
                //buy
                buyGood(npc,itemKey,goodSellInfo);
                //Funds for this purchase = Funds for this purchase-Product price * Random number
                buyMoney-=spend1;
            }else if(spend2>1){
                //buy
                buyGood(npc,itemKey,goodSellInfo);
                //This shopping fund = This shopping fund-Product price * Round down (this shopping fund / product price)
                buyMoney-=goodSellInfo.content.getPrice()* Math.floor(spend2);
            }
        }

        //The behavior after purchase is randomly selected according to the weight of w3 w4 w5
        int id = npc.chooseId();

        AIBuilding aiBuilding = MetaData.getAIBuilding(id);
        if(aiBuilding == null)
            return null;
        IAction action = aiBuilding.randomAgain(aiBuilding,id);
        action.act(npc);

        return buyerNpc;
    }
    private Set<Object> buyGood(Npc npc,ItemKey itemKey, City.GoodSellInfo goodSellInfo){
        Set<Object> buyerNpc = new HashSet<>();
        int chosenGoodMetaId = itemKey.meta.id;
        Building sellShop = goodSellInfo.b;
        RetailShop retailShop=(RetailShop)sellShop;

        logger.info("chosen goodSellInfo retailShop id: "+retailShop.id() + " at: " + retailShop.coordinate()+"  goodMetaId"+ itemKey.meta.id + "  price: " + goodSellInfo.content.getPrice() + " cost: " + goodSellInfo.cost);
        WeightInfo chosen=new WeightInfo(goodSellInfo.b.id(), itemKey.producerId, (int)itemKey.getTotalQty(), (int)goodSellInfo.r, goodSellInfo.content.getPrice(),(MetaGood) itemKey.meta,(int)retailShop.getTotalBrand(), (int)retailShop.getTotalQty());
        sellShop.addFlowCount();

        //TODO:Calculate absenteeism fee
        double minersRatio = MetaData.getSysPara().minersCostRatio;
        long minerCost = (long) Math.floor(chosen.price* minersRatio);
        int saleCount = ((IShelf) sellShop).getSaleCount(chosenGoodMetaId);//Number of shelves
        if(chosen.price+minerCost > npc.money()) {
            //Insufficient gold held at the time of purchase, industry salary increase index += pricing-gold held
            // int money=(int) ((chosen.price+minerCost)-npc.money());
            // City.instance().addIndustryMoney(npc.building().type(),money);
            npc.hangOut(sellShop);
            return null;
        }else if(saleCount<=0){//No goods on shelves, no purchases allowed
            npc.hangOut(sellShop);
            return null;
        }
        else {
            //Both the beneficiary and the buyer must calculate the absenteeism fee
            npc.decMoney((int) (chosen.price+minerCost));
            Player owner = GameDb.getPlayer(sellShop.ownerId());
            owner.addMoney(chosen.price-minerCost);
            LogDb.playerIncome(owner.id(),chosen.price-minerCost,sellShop.type());
            ((IShelf)sellShop).delshelf(chosen.getItemKey(), 1, false);
            sellShop.updateTodayIncome(chosen.price-minerCost);
            //The number of shelves in the retail store changes and is pushed (only when there is something on the shelf)========yty
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

            LogDb.npcBuyInRetailCol(chosen.meta.id, chosen.price, chosen.getItemKey().producerId,    //Consumption records do not count absenteeism fees
                    chosen.qty,sellShop.ownerId(), chosen.buildingBrand,chosen.buildingQty);
            //Get brand name
            BrandManager.BrandName brandName = BrandManager.instance().getBrand(owner.id(),chosen.meta.id).brandName;
            String goodName=brandName==null?owner.getCompanyName():brandName.getBrandName();
            // Record product ratings
            double brandScore = GlobalUtil.getBrandScore(chosen.getItemKey().getTotalBrand(), chosen.meta.id);
            double goodQtyScore = GlobalUtil.getGoodQtyScore(chosen.getItemKey().getTotalQty(), chosen.meta.id, MetaData.getGoodQuality(chosen.meta.id));
            double score = ((brandScore + goodQtyScore) / 2);
            LogDb.npcBuyInShelf(npc.id(),owner.id(),1,chosen.price,chosen.getItemKey().producerId,//Does not include absenteeism fees
                    chosen.bId,MetaItem.type(chosen.meta.id),chosen.meta.id,goodName,score,chosen.getItemKey().getTotalBrand(),chosen.getItemKey().getTotalQty(),((RetailShop)sellShop).getTotalBrand(),((RetailShop)sellShop).getTotalQty(),minerCost);
            LogDb.buildingIncome(chosen.bId, npc.id(),chosen.price-minerCost, MetaItem.type(chosen.meta.id), chosen.meta.id);
            if(!GameServer.isOnline(owner.id())) {
                LogDb.sellerBuildingIncome(chosen.bId, sellShop.type(), owner.id(), 1, chosen.price, chosen.meta.id);//Record details of construction revenue
            }
            //Miner's expense records
            LogDb.minersCost(owner.id(),minerCost,MetaData.getSysPara().minersCostRatio);
            LogDb.npcMinersCost(npc.id(),minerCost,MetaData.getSysPara().minersCostRatio);
            //db operation moved in from the outside
            //Set u = new HashSet(Arrays.asList(npc, owner, sellShop));
            //GameDb.saveOrUpdate(u);
            buyerNpc.addAll(Arrays.asList(npc,owner, sellShop));
            //Shop again
            double spend=MetaData.getGoodSpendMoneyRatio(chosen.meta.id);
            //Wages differentiate unemployment
            int salary=npc.building().singleSalary();
            //Unemployment is unemployment benefits
            if(npc.getStatus()==1){
                salary=(int) (City.instance().getAvgIndustrySalary()*MetaData.getCity().insuranceRatio);
            }
            double mutilSpend=salary*spend;
            mutilSpend-=chosen.price;
            double repeatBuyRetio=mutilSpend/salary*spend;
            Random random = new Random();
            int num = random.nextInt(101);
            //Update remaining quantity
            saleCount=((IShelf) sellShop).getSaleCount(chosenGoodMetaId);
            if(num/100.d<repeatBuyRetio&&saleCount>0){
                //After selecting the products that meet the conditions, go shopping logic again
                Set<Object> objects = buyGood(npc, itemKey, goodSellInfo);
                if(objects!=null&&objects.size()>0){
                    buyerNpc.addAll(objects);
                }
            }
            return buyerNpc;
        }
    }

    private static final class WeightInfo {
        public WeightInfo(UUID bId, UUID producerId, int qty, int w, int price, MetaGood meta, int buildingBrand, int ibuldingQty) {
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
    private Map<ItemKey,City.GoodSellInfo> getRandomNN(Map<ItemKey,City.GoodSellInfo> buyKnownValueMap,int n,int limit){
        Map<ItemKey,Double> map=new HashMap<ItemKey,Double>();
        buyKnownValueMap.forEach((k,v)->{
            map.put(k,v.buyKnownValue);
        });
        Map<ItemKey,City.GoodSellInfo> newMap=new HashMap<>();
        List<ItemKey> keyList=new ArrayList<>(map.keySet());
        List<Double> list= new ArrayList<>(map.values());
        double[] doubles=Util.toDoubleArray(list);
        for (int i=0;i<limit;i++){
            int j=Util.randomIdx(doubles);
            //Random goods meet the selling price <= NPC commodity small class expected consumption, there is no similar goods, up to 20 random selections..
            ItemKey itemKey=keyList.get(j);

            City.GoodSellInfo goodSellInfo=buyKnownValueMap.get(itemKey);
            if(goodSellInfo.content.getPrice()<=goodSellInfo.cost){
                n--;
                if(n<0){
                    break;
                }
                newMap.put(itemKey,goodSellInfo);
            }
        }
        return newMap;
    }
}


