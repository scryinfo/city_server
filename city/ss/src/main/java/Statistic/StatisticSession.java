package Statistic;

import Shared.LogDb;
import Shared.Package;
import Shared.Util;
import Statistic.SummaryUtil.CountType;
import Statistic.Util.TotalUtil;
import com.google.protobuf.Message;
import gs.Gs;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import org.apache.log4j.Logger;
import org.bson.Document;
import ss.Ss;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static Statistic.SummaryUtil.HOUR_MILLISECOND;

// this class contain only getXXX method, that means the only purpose this class or this server
// is get data from db

public class StatisticSession {
    private ChannelHandlerContext ctx;
    private static final Logger logger = Logger.getLogger(StatisticSession.class);
    private ChannelId channelId;
    public static volatile boolean isReady = true;

    public static void setIsReady(boolean isReady)
    {
        StatisticSession.isReady = isReady;
    }

    public StatisticSession(ChannelHandlerContext ctx){
        this.ctx = ctx;
        this.channelId = ctx.channel().id();
    }

    public void logout()
    {
        this.ctx.disconnect();
    }

    private void write(Package p)
    {
        this.ctx.channel().writeAndFlush(p);
    }

    public void queryPlayerEconomy(short cmd, Message message)
    {
        UUID playerId = Util.toUuid(((Ss.Id)message).getId().toByteArray());
        if (!isReady)
        {
            this.write(Package.fail(cmd));
            logger.info("data not ready,playerId = " + playerId);
            return;
        }

        //for test
        //playerId = UUID.fromString("228953c5-7da9-4563-8856-166c41dbb19c");

        Ss.EconomyInfos economyInfos = SummaryUtil.getPlayerEconomy(playerId);
        this.write(Package.create(cmd, economyInfos));
    }

    public void queryBuildingIncome(short cmd, Message message)
    {
        UUID buildingId = Util.toUuid(((Ss.Id)message).getId().toByteArray());
        Ss.BuildingIncome.Builder builder = Ss.BuildingIncome.newBuilder();
        builder.setBuildingId(Util.toByteString(buildingId));
		builder.addAllNodes(SummaryUtil.getBuildDayIncomeById(buildingId));
        this.write(Package.create(cmd, builder.build()));
    }

	public void queryBuildingFlow(short cmd, Message message)
	{
		UUID buildingId = Util.toUuid(((Ss.Id) message).getId().toByteArray());
		long startTime = System.currentTimeMillis() - HOUR_MILLISECOND * 24;
		Ss.BuildingFlow.Builder builder = Ss.BuildingFlow.newBuilder().setBid(((Ss.Id) message).getId());
		LogDb.queryBuildingFlowAndLift(startTime, buildingId).forEach(document ->
		{
			builder.addNodes(Ss.BuildingFlow.Node.newBuilder()
					.setTime(document.getLong("t"))
					.setFlow(document.getInteger("f"))
					.build());
		});
		this.write(Package.create(cmd,builder.build()));
	}

	public void queryBuildingLift(short cmd, Message message)
	{
		UUID buildingId = Util.toUuid(((Ss.Id)message).getId().toByteArray());
		long startTime = System.currentTimeMillis() - HOUR_MILLISECOND * 24;
		Ss.BuildingLift.Builder builder = Ss.BuildingLift.newBuilder().setBid(((Ss.Id) message).getId());
		LogDb.queryBuildingFlowAndLift(startTime, buildingId).forEach(document ->
		{
			builder.addNodes(Ss.BuildingLift.Node.newBuilder()
					.setTime(document.getLong("t"))
					.setLift((float) (double)document.get("l"))
					.build());
		});
		this.write(Package.create(cmd,builder.build()));
	}

    public void queryAllPlayerSex(short cmd)
    {
        Ss.SexInfo.Builder builder = Ss.SexInfo.newBuilder();
        builder.setMale(SummaryUtil.getSexInfo(true));
        builder.setFemale(SummaryUtil.getSexInfo(false));
        this.write(Package.create(cmd, builder.build()));
    }
    
    public void queryNpcNum(short cmd, Message message)
    {
    	Ss.QueryNpcNum m = (Ss.QueryNpcNum)message;
    	long time=m.getTime();
    	int type=m.getType().getNumber();
    	Ss.NpcNums.Builder list = Ss.NpcNums.newBuilder();
    	Ss.NpcNums.NpcNumInfo.Builder info = Ss.NpcNums.NpcNumInfo.newBuilder();
    	List<Document> ls=null;
    	if(Ss.QueryNpcNum.Type.GOODS.getNumber()==type){
        	ls=SummaryUtil.getNpcHistoryData(SummaryUtil.getDayGoodsNpcNum(),CountType.BYSECONDS,time);
    	}else if(Ss.QueryNpcNum.Type.APARTMENT.getNumber()==type){
        	ls=SummaryUtil.getNpcHistoryData(SummaryUtil.getDayApartmentNpcNum(),CountType.BYSECONDS,time);
    	}
    	if(ls!=null&&ls.size()>0){
    	 	for (Document document : ls) {
        		info.setId(document.getInteger("id")==null?0:document.getInteger("id"));
        		info.setTotal(document.getLong("total"));
        		info.setTime(document.getLong("time"));
        		list.addNumInfo(info.build());
    		}
    	}
    	list.setType(type);
    	this.write(Package.create(cmd, list.build()));
    }
    
    public void queryNpcExchangeAmount(short cmd)
    {
    	Ss.NpcExchangeAmount.Builder builder = Ss.NpcExchangeAmount.newBuilder();
    	//npc购买商品的交易量
    	long yesterdayNpcBuyInShelf=SummaryUtil.getHistoryData(SummaryUtil.getDayNpcBuyInShelf(),CountType.BYDAY);
    	long todayNpcBuyInShelf=SummaryUtil.getTodayData(LogDb.getNpcBuyInShelf());
    	//npc租房的交易量
    	long yesterdayNpcRentApartment=SummaryUtil.getHistoryData(SummaryUtil.getDayNpcRentApartment(),CountType.BYDAY);
    	long todayNpcRentApartment=SummaryUtil.getTodayData(LogDb.getNpcRentApartment());
    	
    	builder.setNpcExchangeAmount(yesterdayNpcBuyInShelf+todayNpcBuyInShelf+yesterdayNpcRentApartment+todayNpcRentApartment);
    	this.write(Package.create(cmd, builder.build()));
    }
    
    public void queryExchangeAmount(short cmd)
    {
    	Ss.ExchangeAmount.Builder builder = Ss.ExchangeAmount.newBuilder();
    	//npc交易量
    	long yesterdayNpcBuyInShelf=SummaryUtil.getHistoryData(SummaryUtil.getDayNpcBuyInShelf(),CountType.BYDAY);
    	long todayNpcBuyInShelf=SummaryUtil.getTodayData(LogDb.getNpcBuyInShelf());
    	long yesterdayNpcRentApartment=SummaryUtil.getHistoryData(SummaryUtil.getDayNpcRentApartment(),CountType.BYDAY);
      	long todayNpcRentApartment=SummaryUtil.getTodayData(LogDb.getNpcRentApartment());
      	long npcExchangeAmount=yesterdayNpcBuyInShelf+todayNpcBuyInShelf+yesterdayNpcRentApartment+todayNpcRentApartment;
    	//player交易量
    	long yesterdayPlayerBuyGround=SummaryUtil.getHistoryData(SummaryUtil.getDayPlayerBuyGround(),CountType.BYDAY);
    	long todayPlayerBuyGround=SummaryUtil.getTodayData(LogDb.getBuyGround());
    	long yesterdayPlayerBuyInShelf=SummaryUtil.getHistoryData(SummaryUtil.getDayPlayerBuyInShelf(),CountType.BYDAY);
    	long todayPlayerBuyInShelf=SummaryUtil.getTodayData(LogDb.getBuyInShelf());
    	long yesterdayPlayerRentGround=SummaryUtil.getHistoryData(SummaryUtil.getDayPlayerRentGround(),CountType.BYDAY);
    	long todayPlayerRentGround=SummaryUtil.getTodayData(LogDb.getRentGround());
		long yesterdayPlayerResearch = SummaryUtil.getHistoryData(SummaryUtil.getDayPlayerResearch(), CountType.BYDAY);
		long todayPlayerResearch = SummaryUtil.getTodayData(LogDb.getLaboratoryRecord());
		long yesterdayPlayerPromotion = SummaryUtil.getHistoryData(SummaryUtil.getDayPlayerPromotion(), CountType.BYDAY);
		long todayPlayerPromotion = SummaryUtil.getTodayData(LogDb.getPromotionRecord());
		long playerExchangeAmount = yesterdayPlayerBuyGround + todayPlayerBuyGround + yesterdayPlayerBuyInShelf
		+ todayPlayerBuyInShelf + yesterdayPlayerRentGround + todayPlayerRentGround + yesterdayPlayerResearch + todayPlayerResearch + yesterdayPlayerPromotion + todayPlayerPromotion;
    	builder.setExchangeAmount(npcExchangeAmount+playerExchangeAmount);
    	this.write(Package.create(cmd, builder.build()));
    }
    
    public void queryGoodsNpcNumCurve(short cmd, Message message)
    {
      	Ss.GoodsNpcNumCurve g = (Ss.GoodsNpcNumCurve)message;
    	int id=g.getId();
		Map<Long, Long> map=SummaryUtil.queryGoodsNpcNumCurve(SummaryUtil.getDayGoodsNpcNum(),id,CountType.BYHOUR);
		Ss.NpcNumCurveMap.Builder bd=Ss.NpcNumCurveMap.newBuilder();
		Ss.GoodsNpcNumCurve.Builder list = Ss.GoodsNpcNumCurve.newBuilder();
	    map.forEach((k,v)->{
	    	bd.setKey(k);
			bd.setValue(v);
			list.addNpcNumCurveMap(bd.build());
	    });
		this.write(Package.create(cmd,list.build()));
    }

	public void queryApartmentNpcNumCurve(short cmd)
	{
		Map<Long, Long> map=SummaryUtil.queryApartmentNpcNumCurve(SummaryUtil.getDayApartmentNpcNum(),CountType.BYHOUR);
		Ss.NpcNumCurveMap.Builder bd=Ss.NpcNumCurveMap.newBuilder();
		Ss.ApartmentNpcNumCurve.Builder list = Ss.ApartmentNpcNumCurve.newBuilder();
		map.forEach((k,v)->{
			bd.setKey(k);
			bd.setValue(v);
			list.addNpcNumCurveMap(bd.build());
	    });
		this.write(Package.create(cmd,list.build()));
    }
    
    public void queryCityBroadcast(short cmd)
    {
    	List<Document> listDocument=SummaryUtil.queryCityBroadcast(LogDb.getCityBroadcast());
    	Ss.CityBroadcasts.Builder list = Ss.CityBroadcasts.newBuilder();
    	Ss.CityBroadcast.Builder bd=Ss.CityBroadcast.newBuilder();
    	for (Document document : listDocument) {
    		if(document.get("s")!=null){
    			bd.setSellerId(Util.toByteString(UUID.fromString(document.get("s").toString())));
    		}
            if(document.get("b")!=null){
            	bd.setBuyerId(Util.toByteString(UUID.fromString(document.get("b").toString())));
            }
    		bd.setCost(document.getLong("c"));
    		bd.setNum(document.getInteger("n"));
    		bd.setType(document.getInteger("tp"));
    		bd.setTs(document.getLong("t"));
    		list.addCityBroadcast(bd.build());
		}
    	this.write(Package.create(cmd,list.build()));
    }

    public void queryNpcTypeNum(short cmd)
    {
    	Ss.NpcHourTypeNum.Builder list = Ss.NpcHourTypeNum.newBuilder();
    	Map<Long, Map> map=SummaryUtil.getNpcTypeNumHistoryData(LogDb.getNpcTypeNum());
    	map.forEach((k,v)->{
            Ss.NpcTypeNumInfo.Builder info = Ss.NpcTypeNumInfo.newBuilder();
     		info.setT(k);
     		Map<Integer,Long> m=v;
     		for(Map.Entry<Integer,Long> entry:m.entrySet()){
                Ss.NpcTypeNumMap.Builder npcTypeNumMap=Ss.NpcTypeNumMap.newBuilder();
                npcTypeNumMap.setTp(entry.getKey());
                npcTypeNumMap.setN(entry.getValue());
                info.addNpcTypeNumMap(npcTypeNumMap.build());
			}
	  		list.addNpcTypeNumInfo(info.build());
    	});

    	this.write(Package.create(cmd, list.build()));
    }

    //---ly

    public void queryPlayerExchangeAmount(short cmd) {
        Ss.PlayExchangeAmount.Builder builder = Ss.PlayExchangeAmount.newBuilder();
        long playerExchangeAmount = SummaryUtil.getTodayData(SummaryUtil.getPlayerExchangeAmount(), CountType.BYSECONDS);
        builder.setPlayExchangeAmount(playerExchangeAmount);
        this.write(Package.create(cmd, builder.build()));
    }

    // 查询一周曲线图
	public void queryPlayerExchangeCurve(short cmd, Message message) {
		Ss.PlayerGoodsCurve curve = (Ss.PlayerGoodsCurve) message;
		long id = curve.getId();
		int exchangeType = curve.getExchangeType();
		Map<Long, Long> moneyMap = SummaryUtil.queryPlayerExchangeCurve(SummaryUtil.getPlayerExchangeAmount(), id, exchangeType, curve.getType() ? CountType.BYHOUR : CountType.BYDAY, true);
		Map<Long, Long> numMap = SummaryUtil.queryPlayerExchangeCurve(SummaryUtil.getPlayerExchangeAmount(), id, exchangeType, curve.getType() ? CountType.BYHOUR : CountType.BYDAY, false);
		Ss.PlayerGoodsCurve.Builder builder = Ss.PlayerGoodsCurve.newBuilder();
		builder.setId(id);
		builder.setExchangeType(exchangeType);
		builder.setType(curve.getType());
		moneyMap.forEach((k,v)->{
			Ss.PlayerGoodsCurve.PlayerGoodsCurveMap.Builder b = builder.addPlayerGoodsCurveMapBuilder();
			b.setTime(k);
			b.setMoney(v);
			b.setSize((numMap != null && numMap.size() > 0 && numMap.get(k) != null) ? numMap.get(k) : 0);
		});
		this.write(Package.create(cmd,builder.build()));
	}
	
    public void queryPlayerIncomePayCurve(short cmd, Message message)
    {
    	UUID id = Util.toUuid(((Ss.Id) message).getId().toByteArray());
		Map<Long,Ss.PlayerIncomePayCurve.PlayerIncomePay> totalMap = new TreeMap<>();//用于统计收入和支出的合并数据
    	Map<Long, Long> playerIncomeMap=SummaryUtil.queryPlayerIncomePayCurve(SummaryUtil.getDayPlayerIncome(),id);
    	Map<Long, Long> playerPayMap=SummaryUtil.queryPlayerIncomePayCurve(SummaryUtil.getDayPlayerPay(),id);
    	//统计整理数据
		Map<Long, Long> monthTotalIncome = TotalUtil.getInstance().monthTotal(playerIncomeMap);
		Map<Long, Long> monthTotalpay = TotalUtil.getInstance().monthTotal(playerPayMap);
		Ss.PlayerIncomePayCurve.Builder builder=Ss.PlayerIncomePayCurve.newBuilder();
    	builder.setId(Util.toByteString(id));
		//1.处理收入信息
    	monthTotalIncome.forEach((k,v)->{
    		Ss.PlayerIncomePayCurve.PlayerIncomePay.Builder b=builder.addPlayerIncomeBuilder();
    		b.setTime(k);
    		b.setIncome(v);
    		b.setPay((monthTotalpay!=null&&monthTotalpay.size()>0&&monthTotalpay.get(k)!=null)?monthTotalpay.get(k):0);
			totalMap.put(k,b.build());
    	});
		//2.处理支出信息
		for (Map.Entry<Long, Long> pay : monthTotalpay.entrySet()) {
			//如果在收入中已经处理了，则跳过
			Long time = pay.getKey();
			if(totalMap.containsKey(time)){
				continue;
			}
			Ss.PlayerIncomePayCurve.PlayerIncomePay.Builder b = Ss.PlayerIncomePayCurve.PlayerIncomePay.newBuilder();
			//添加其他的信息
			b.setTime(pay.getKey());
			b.setPay(pay.getValue());
			b.setIncome((monthTotalIncome!=null&&monthTotalIncome.size()>0&&monthTotalIncome.get(pay.getKey())!=null)?monthTotalIncome.get(pay.getKey()):0);
			totalMap.put(pay.getKey(),b.build());
		}
		//3.处理今日最新收入和支出信息
		Long todayIncome = TotalUtil.getInstance().todayIncomeOrPay(playerIncomeMap);
		Long todayPay = TotalUtil.getInstance().todayIncomeOrPay(playerPayMap);
		builder.setTodayIncome(todayIncome);
		builder.setTodayPay(todayPay);
		builder.addAllPlayerIncome(totalMap.values());
    	this.write(Package.create(cmd,builder.build()));
    }

	public void queryIncomeNotify(short cmd, Message message)
	{
		UUID playerId = Util.toUuid(((Gs.Id) message).getId().toByteArray());
		this.write(Package.create(cmd,
				Ss.IncomeNotifys.newBuilder()
						.setId(Util.toByteString(playerId))
						.addAllNotifys(LogDb.getIncomeNotify(playerId, 30))
						.build()));
	}
}
