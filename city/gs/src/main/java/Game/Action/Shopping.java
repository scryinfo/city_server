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
        logger.info("Shopping building num: " + buildings.size());
        AIBuy ai = MetaData.getAIBuy(aiId);
        MetaGood.Type type = ai.random(BrandManager.instance().getGoodWeightRatioWithType());
        AILux aiLux = MetaData.getAILux(npc.type());
        int lux = aiLux.random(BrandManager.instance().getGoodWeightRatioWithLux());
        logger.info("choose good type: " + type.ordinal()+" lux: " + lux);
        //取得对应的 NPC商品大类+奢侈度预期消费 和 相关的 NPC商品小类预期消费
        City.GoodFilter filter=new City.GoodFilter();
        filter.type=type.ordinal();
        filter.lux=lux;
        Map<City.GoodFilter,Set<City.GoodSellInfo>> retailShopGoodMap=City.instance().getRetailShopGoodMap();
        Set<City.GoodSellInfo> goodSellInfos=retailShopGoodMap.get(filter);
        if(goodSellInfos==null||goodSellInfos.size()==0){
            return null;
        }
        //实际总购物预期 = 实际住宅购物预期 + 所有 实际某种商品购物预期(已发明)
        int allCostSpend=MetaData.getAllCostSpendRatio();
        int sumGoodSpendRatio=0;

        Map<Building,Double> moveKnownMap=new HashMap<Building,Double>();
        Map<ItemKey,City.GoodSellInfo> buyKnownValueMap=new HashMap<ItemKey,City.GoodSellInfo>();
        AISelect aiSelect = MetaData.getAISelect(aiId);
        for(City.GoodSellInfo goodSellInfo:goodSellInfos){//商品id重复有影响没？
            ItemKey itemKey=goodSellInfo.itemKey;
            int goodMetaId=itemKey.meta.id;
            //实际某种商品购物预期(已发明) = 商品购物预期 * (1 + 全城某种商品知名度均值 / 全城知名度均值) * (1 + 全城某种商品品质均值 / 全城品质均值)
            double realApartmentSpend=MetaData.getCostSpendRatio(goodMetaId)* (1 +  AiBaseAvgManager.getInstance().getBrandMapVal(MetaBuilding.APARTMENT) /AiBaseAvgManager.getInstance().getAllBrandAvg()) * (1 +  AiBaseAvgManager.getInstance().getQualityMapVal(MetaBuilding.APARTMENT) / AiBaseAvgManager.getInstance().getAllQualityAvg());
            //NPC商品小类预期消费 = 城市工资标准 * (实际某种商品购物预期 / 实际总购物预期)
            final int subCost = (int) (npc.salary() * (realApartmentSpend/allCostSpend));
            sumGoodSpendRatio+=MetaData.getCostSpendRatio(goodMetaId);

            logger.info("goodSellInfo goodSellInfo.b: " + goodSellInfo.b+" goodSellInfo.moveKnownValue: "+goodSellInfo.moveKnownValue);
            moveKnownMap.put(goodSellInfo.b,goodSellInfo.moveKnownValue);

            //int wn=MetaData.getAISelectGood(goodMetaId);0
            //buyKnownValue = (1 + gid_xxxx/100) * NPC商品小类预期消费 / 售价 * (1 + 商品品质 / 全城某种商品品质均值) * (1 + 商品知名度 / 全城某种商品知名度均值) * 100
            Shelf.Content content=goodSellInfo.content;
            double buyKnownValue=(1 + aiSelect.random()/100d) * subCost /content.getPrice() * (1 +  AiBaseAvgManager.getInstance().getBrandMapVal(goodMetaId) /AiBaseAvgManager.getInstance().getAllBrandAvg()) * (1 +  AiBaseAvgManager.getInstance().getQualityMapVal(goodMetaId) / AiBaseAvgManager.getInstance().getAllQualityAvg()) * 100;
            goodSellInfo.cost=subCost;
            goodSellInfo.buyKnownValue=buyKnownValue;
            buyKnownValueMap.put(itemKey,goodSellInfo);
        }
        // NPC商品大类+奢侈度预期消费 = 城市工资标准 * ( 符合奢侈度和大类的 全部 实际某种商品购物预期(已发明) 的和 / 实际总购物预期)
        int cost = (int) (npc.salary() * (sumGoodSpendRatio/allCostSpend));

        //零售店移动选择
        //随机选择3个零售店加入备选列表
        Map<Building,Double> moveKnownBak=getRandomN(moveKnownMap,3);
        moveKnownBak.forEach((k,v)->{
            //备选零售店的移动选择权重 = moveKnownValue * (2 - (ABS(NPC当前所在位置.x - 零售店坐标.x) + ABS(NPC当前所在位置.y - 零售店坐标.y)) / 160)
            double r= v * (2 - Building.distance(k, npc.buildingLocated())) / 160;
            moveKnownBak.put(k,r);
        });
        //随机选中其中一个并移动到该零售店
        Map<Building,Double> moveKnownSelect=getRandomN(moveKnownBak,1);
        List<Building> moveKnownList=new ArrayList<>(moveKnownSelect.keySet());
        Building moveKnownChosen=moveKnownList.get(0);
        npc.goFor(moveKnownChosen);

        //商品顺序购买选择
        //随机选择10个商品加入顺序列表
        Map<ItemKey,City.GoodSellInfo> buyKnownBak=getRandomNN(buyKnownValueMap,10,20);
        //如果没有备选,则原地不动
        if(buyKnownBak==null||buyKnownBak.size()==0){
            return null;
        }
        buyKnownBak.forEach((k,v)->{
            //备选商品的购买选择权重 = buyKnownValue * (2 - (ABS(NPC当前所在位置.x - 零售店坐标.x) + ABS(NPC当前所在位置.y - 零售店坐标.y)) / 160)
            double r= v.buyKnownValue * (2 - Building.distance(v.b, npc.buildingLocated())) / 160;
            //商品购买数量  随机出购买数量(0-最大购买数量之间随机)
            Random rand = new Random();
            int buyNum = rand.nextInt(v.content.getN());
            v.r=r;
            v.buyNum=buyNum;
            buyKnownBak.put(k,v);
        });

        //顺序购买
        for (Map.Entry<ItemKey, City.GoodSellInfo> entry:buyKnownBak.entrySet()) {
            ItemKey itemKey=entry.getKey();
            City.GoodSellInfo goodSellInfo=entry.getValue();

            //本次购物资金 = min(NPC商品大类+奢侈度预期消费 , 所持金)
            long buyMoney=npc.money();
            if(cost<npc.money()){
                buyMoney=cost;
            }

            if(goodSellInfo.buyNum==0){
                continue;
            }
            //如果 本次购物资金 > 商品售价 * 随机出的数量 则购买成功
            int spend1=goodSellInfo.content.getPrice()*goodSellInfo.buyNum;
            //如果 本次购物资金 / 商品售价 > 1 则购买成功
            double spend2=buyMoney/goodSellInfo.content.getPrice();
            if(buyMoney>spend1){
                //购买
                buyGood(npc,itemKey,goodSellInfo);
                //本次购物资金 = 本次购物资金 - 商品售价 * 随机出的数量
                buyMoney-=spend1;
            }else if(spend2>1){
                //购买
                buyGood(npc,itemKey,goodSellInfo);
                //本次购物资金 = 本次购物资金 - 商品售价 * 向下取整(本次购物资金 / 商品售价)
                buyMoney-=goodSellInfo.content.getPrice()* Math.floor(spend2);
            }
        }

        //购买后的行为,根据 w3 w4 w5 的权重进行随机选择
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

        //TODO:计算旷工费
        double minersRatio = MetaData.getSysPara().minersCostRatio;
        long minerCost = (long) Math.floor(chosen.price* minersRatio);
        int saleCount = ((IShelf) sellShop).getSaleCount(chosenGoodMetaId);//货架上的数量
        if(chosen.price+minerCost > npc.money()) {
            //购买时所持金不足,行业涨薪指数 += 定价 - 所持金
            // int money=(int) ((chosen.price+minerCost)-npc.money());
            // City.instance().addIndustryMoney(npc.building().type(),money);
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
            LogDb.npcBuyInShelf(npc.id(),owner.id(),1,chosen.price,chosen.getItemKey().producerId,//不包含旷工费
                    chosen.bId,MetaItem.type(chosen.meta.id),chosen.meta.id,goodName,score,chosen.getItemKey().getTotalBrand(),chosen.getItemKey().getTotalQty(),((RetailShop)sellShop).getTotalBrand(),((RetailShop)sellShop).getTotalQty(),minerCost);
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
    //随机选择n个住宅加入备选列表
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
            //随机到的商品满足售价 <= NPC商品小类预期消费,不存在同类商品,最多进行20次随机选择..
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


