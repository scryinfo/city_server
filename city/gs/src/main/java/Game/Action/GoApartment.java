package Game.Action;


import Game.*;
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
        logger.info("GoApartment buildings num: " + buildings.size());

        //实际住宅购物预期 = 住宅购物预期 * (1 + 全城住宅知名度均值 / 全城知名度均值) * (1 + 全城住宅品质均值 / 全城品质均值)
        double realApartmentSpend=MetaData.getCostSpendRatio(buildingType)* (1 +  AiBaseAvgManager.getInstance().getBrandMapVal(MetaBuilding.APARTMENT) /AiBaseAvgManager.getInstance().getAllBrandAvg()) * (1 +  AiBaseAvgManager.getInstance().getQualityMapVal(MetaBuilding.APARTMENT) / AiBaseAvgManager.getInstance().getAllQualityAvg());
        //实际总购物预期 = 实际住宅购物预期 + 所有 实际某种商品购物预期(已发明)
        double allCostSpend=MetaData.getAllCostSpendRatio();
        //NPC住宅预期消费 = 城市工资标准 * (实际住宅购物预期 / 实际总购物预期)
        final int cost = (int) (npc.salary() * (realApartmentSpend/allCostSpend));

        //住宅移动选择
        Map<Building,Double> moveKnownMap=City.instance().getMoveKnownaApartmentMap();
        //随机选择3个住宅加入备选列表
        Map<Building,Double> moveKnownBak=getRandomN(moveKnownMap,3);
        moveKnownBak.forEach((k,v)->{
            double r= v * (2 - Building.distance(k, npc.buildingLocated())) / 160;
            moveKnownBak.put(k,r);
        });
        //随机选中其中一个并移动到该住宅
        Map<Building,Double> moveKnownSelect=getRandomN(moveKnownBak,1);
        moveKnownSelect.forEach((k,v)->{
            npc.goFor(k);
        });

        //住宅购买选择
        Map<Building,Double> buyKnownValueMap=new HashMap<Building,Double>();
        moveKnownMap.forEach((k,v)->{
            //buyKnownValue = NPC住宅预期消费 / 售价 * 10000
            double buyKnownValue = cost / k.cost() * 10000;
            buyKnownValueMap.put(k,buyKnownValue);
        });
        //随机选择3个住宅加入备选列表
        Map<Building,Double> buyKnownBak=getRandomN(moveKnownMap,3);
        buyKnownBak.forEach((k,v)->{
            double r= v * (2 - Building.distance(k, npc.buildingLocated())) / 160;
            buyKnownBak.put(k,r);
        });
        //随机选中其中一个
        Map<Building,Double> buyKnownSelect=getRandomN(buyKnownBak,1);
        if(buyKnownSelect==null||buyKnownSelect.size()==0){
            return null;
        }
        List<Building> chosenList=new ArrayList<>(buyKnownSelect.keySet());
        Building chosen = chosenList.get(0);
        logger.info("chosen apartment building: " + chosen.id().toString() + " mId: " + chosen.metaId() + " which coord is: " + chosen.coordinate());

        if(npc.money() < chosen.cost()){
            npc.hangOut(chosen);
            chosen.addFlowCount();
            return null;
        }else {
            Player owner = GameDb.getPlayer(chosen.ownerId());
            long income = chosen.cost();//今日收入
            long pay = chosen.cost();

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
            // 建筑繁荣度 建筑评分
            Apartment apartment = (Apartment) chosen;
            int prosperityScore = (int) ProsperityManager.instance().getBuildingProsperityScore(chosen);
            double brandScore = GlobalUtil.getBrandScore(apartment.getTotalBrand(), chosen.type());
            double retailScore = GlobalUtil.getBuildingQtyScore(apartment.getTotalQty(), chosen.type());
            int curRetailScore = (int) ((brandScore + retailScore) / 2);
            LogDb.npcRentApartment(npc.id(), owner.id(), 1, chosen.cost(), chosen.ownerId(),
                    chosen.id(), chosen.type(), chosen.metaId(),curRetailScore,prosperityScore,owner.getName(),owner.getCompanyName(),apartment.getTotalBrand(),apartment.getTotalQty()); //不包含矿工费用
            if(!GameServer.isOnline(owner.id())) {
                LogDb.sellerBuildingIncome(chosen.id(), chosen.type(), owner.id(), 1, chosen.cost(), 0);
            }
            chosen.updateTodayIncome(income);
            npc.goFor(chosen);
            return new HashSet<>(Arrays.asList(npc,owner, chosen));
        }
    }
    //随机选择n个住宅加入备选列表
    public Map<Building,Double> getRandomN(Map<Building,Double> map,int n){
        Map<Building,Double> newMap=new HashMap<>();
        List<Building> keyList=new ArrayList<>(map.keySet());
        Double[] val=(Double[])map.values().toArray();
        double[] doubles=Util.toDoubleArray(val);
        for (int i=0;i<=n;i++){
            int j=Util.randomIdx(doubles);
            newMap.put(keyList.get(j),val[j]);
        }
        return newMap;
    }
}
