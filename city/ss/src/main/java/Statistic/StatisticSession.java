package Statistic;

import Param.ItemKey;
import Param.MetaBuilding;
import Shared.LogDb;
import Shared.Package;
import Shared.Util;
import Statistic.SummaryUtil.CountType;
import Statistic.Util.TimeUtil;
import Statistic.Util.TotalUtil;
import com.google.protobuf.Message;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import org.apache.log4j.Logger;
import org.bson.Document;
import ss.Ss;

import java.util.*;

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
		Map<Long, Long> income = SummaryUtil.getBuildDayIncomeById(buildingId);//建筑今日的营收
		Map<Long, Long> pay = SummaryUtil.getBuildDayPayById(buildingId);//建筑今日的支出
		//合并收入支出
		Map<Long,Ss.NodeIncome> nodes = new HashMap<>();
		income.forEach((k,v)->{
			Ss.NodeIncome.Builder node = Ss.NodeIncome.newBuilder();
			node.setTime(k)
					.setIncome(v)
					.setPay(pay.getOrDefault(k,0L));
			nodes.put(k,node.build());
		});
		pay.forEach((k,v)->{
			if(!nodes.containsKey(k)){
				Ss.NodeIncome.Builder node = Ss.NodeIncome.newBuilder();
				node.setTime(k)
						.setPay(v)
						.setIncome(income.getOrDefault(k,0L));
				nodes.put(k, node.build());
			}
		});
		builder.addAllNodes(nodes.values());
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
		long todayPlayerBuyInShelf=SummaryUtil.getTodayData(LogDb.getBuyInShelf()); // 新版研究所、广告公司也包含在内
		long yesterdayPlayerRentGround=SummaryUtil.getHistoryData(SummaryUtil.getDayPlayerRentGround(),CountType.BYDAY);
		long todayPlayerRentGround=SummaryUtil.getTodayData(LogDb.getRentGround());
		long playerExchangeAmount = yesterdayPlayerBuyGround + todayPlayerBuyGround + yesterdayPlayerBuyInShelf
		+ todayPlayerBuyInShelf + yesterdayPlayerRentGround + todayPlayerRentGround;
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
//        long playerExchangeAmount = SummaryUtil.getTodayData(SummaryUtil.getPlayerExchangeAmount(), CountType.BYSECONDS);
		//player交易量
		long yesterdayPlayerBuyGround=SummaryUtil.getHistoryData(SummaryUtil.getDayPlayerBuyGround(),CountType.BYDAY);
		long todayPlayerBuyGround=SummaryUtil.getTodayData(LogDb.getBuyGround());
		long yesterdayPlayerBuyInShelf=SummaryUtil.getHistoryData(SummaryUtil.getDayPlayerBuyInShelf(),CountType.BYDAY);
		long todayPlayerBuyInShelf=SummaryUtil.getTodayData(LogDb.getBuyInShelf()); // 新版研究所、数据公司也包含在内
		long yesterdayPlayerRentGround=SummaryUtil.getHistoryData(SummaryUtil.getDayPlayerRentGround(),CountType.BYDAY);
		long todayPlayerRentGround=SummaryUtil.getTodayData(LogDb.getRentGround());
		long playerExchangeAmount = yesterdayPlayerBuyGround + todayPlayerBuyGround + yesterdayPlayerBuyInShelf
		+ todayPlayerBuyInShelf + yesterdayPlayerRentGround + todayPlayerRentGround;
        builder.setPlayExchangeAmount(playerExchangeAmount);
        this.write(Package.create(cmd, builder.build()));
    }

	// 查询一周曲线图
	public void queryPlayerGoodsCurve(short cmd, Message message) {
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
		/*前29天的收入支出数据（按天统计）*/
		Map<Long, Long> playerIncomeMap=SummaryUtil.queryPlayerIncomePayCurve(SummaryUtil.getDayPlayerIncome(),id);
		Map<Long, Long> playerPayMap=SummaryUtil.queryPlayerIncomePayCurve(SummaryUtil.getDayPlayerPay(),id);
		Map<Long, Long> monthTotalIncome = TotalUtil.getInstance().monthTotal(playerIncomeMap);
		Map<Long, Long> monthTotalPay = TotalUtil.getInstance().monthTotal(playerPayMap);
		//1.处理收入信息
		monthTotalIncome.forEach((k,v)->{
			Ss.PlayerIncomePayCurve.PlayerIncomePay.Builder b=Ss.PlayerIncomePayCurve.PlayerIncomePay.newBuilder();
			b.setTime(k);
			b.setIncome(v);
			b.setPay((monthTotalPay!=null&&monthTotalPay.get(k)!=null)?monthTotalPay.get(k):0);
			totalMap.put(k,b.build());
		});
		//2.处理支出信息
		for (Map.Entry<Long, Long> pay : monthTotalPay.entrySet()) {
			//如果在收入中已经处理了，则跳过
			Long time = pay.getKey();
			if(totalMap.containsKey(time)){
				continue;
			}
			Ss.PlayerIncomePayCurve.PlayerIncomePay.Builder b = Ss.PlayerIncomePayCurve.PlayerIncomePay.newBuilder();
			//添加其他的信息
			b.setTime(pay.getKey());
			b.setPay(pay.getValue());
			//由于所有的收入在上面已经处理过了，所以，现在不可能存在收入的情况了，统一设置为0 ，一旦经过这里，都是有支出无收入的情况
			b.setIncome(0);
			totalMap.put(pay.getKey(),b.build());
		}
		//3.处理今日最新收入和支出信息（之恶杰从LogDb的玩家u收入支出中统计）
		Long todayIncome =LogDb.getTodayPlayerIncomeOrPay(TimeUtil.todayStartTime(), System.currentTimeMillis(), LogDb.getPlayerIncome(), id).stream().reduce(Long::sum).orElse(0L);
		Long todayPay = LogDb.getTodayPlayerIncomeOrPay(TimeUtil.todayStartTime(), System.currentTimeMillis(), LogDb.getPlayerPay(), id).stream().reduce(Long::sum).orElse(0L);
		//返回数据
		Ss.PlayerIncomePayCurve.Builder builder=Ss.PlayerIncomePayCurve.newBuilder();
		builder.setId(Util.toByteString(id));
		builder.setTodayIncome(todayIncome);
		builder.setTodayPay(todayPay);
		builder.addAllPlayerIncome(totalMap.values());
		this.write(Package.create(cmd,builder.build()));
	}

	public void queryGoodsSoldDetailCurve(short cmd, Message message)
	{
		int itemId = ((Ss.GoodsSoldDetailCurve) message).getItemId();
		UUID produceId = Util.toUuid(((Ss.GoodsSoldDetailCurve) message).getProduceId().toByteArray());

		Map<Long,Ss.GoodsSoldDetailCurve.GoodsSoldDetail> totalMap = new TreeMap<>();//用于统计商品的销售量和销售额的合并数据
		Map<Long, Document> goodsSoldMap=SummaryUtil.queryGoodsSoldDetailCurve(SummaryUtil.getDayGoodsSoldDetail(),itemId,produceId);
		goodsSoldMap.forEach((k,v)->{
			Ss.GoodsSoldDetailCurve.GoodsSoldDetail.Builder b=Ss.GoodsSoldDetailCurve.GoodsSoldDetail.newBuilder();
			b.setTime(k);
			b.setSoldNum(v.getInteger("n"));
			b.setSoldAmount(v.getLong("total"));
			totalMap.put(k,b.build());
		});
		//处理今日最新销售量和销售额信息
		List<Document> documentList = LogDb.getDayGoodsSoldDetail(TimeUtil.todayStartTime(),System.currentTimeMillis(), LogDb.getNpcBuyInShelf());
		//返回数据
		Ss.GoodsSoldDetailCurve.Builder builder=Ss.GoodsSoldDetailCurve.newBuilder();
		builder.setItemId(itemId);
		builder.setProduceId(Util.toByteString(produceId));

		documentList.forEach(document ->{
			UUID p=document.get("p",UUID.class);
			int id=document.getInteger("id");
			long todayTime=System.currentTimeMillis();
			if((itemId==id)&&(produceId==p)){
				Ss.GoodsSoldDetailCurve.GoodsSoldDetail.Builder b=Ss.GoodsSoldDetailCurve.GoodsSoldDetail.newBuilder();
				b.setTime(todayTime).setSoldNum(document.getInteger("n")).setSoldAmount(document.getLong("total"));
				totalMap.put(todayTime,b.build());
			}else{
				Ss.GoodsSoldDetailCurve.GoodsSoldDetail.Builder b=Ss.GoodsSoldDetailCurve.GoodsSoldDetail.newBuilder();
				b.setTime(todayTime).setSoldNum(0).setSoldAmount(0);
				totalMap.put(todayTime,b.build());
			}
		});
		builder.addAllSoldDetail(totalMap.values());
		this.write(Package.create(cmd,builder.build()));
	}

	public void queryIncomeNotify(short cmd, Message message)
	{
		UUID playerId = Util.toUuid(((Ss.Id) message).getId().toByteArray());
		this.write(Package.create(cmd,
				Ss.IncomeNotifys.newBuilder()
						.setId(Util.toByteString(playerId))
						.addAllNotifys(LogDb.getIncomeNotify(playerId, 30))
						.build()));
	}

	public void queryIndustryDevelopment(short cmd, Message message){
		Ss.IndustryDevelopment msg = (Ss.IndustryDevelopment)message;
		int buildType=msg.getType().getNumber();

		Ss.IndustryDevelopment.Builder b=Ss.IndustryDevelopment.newBuilder();
		b.setType(msg.getType());
		Map<Long,Long> singleMap=new TreeMap<Long,Long>();
		Map<Long,Long> totalMap=new TreeMap<Long,Long>();
		//前六天数据
		List<Document> sixDaylist=SummaryUtil.queryWeekIndustryDevelopment(SummaryUtil.getDayIndustryIncome());
		//前六天每天的行业总收入和每天指定类型建筑的总收入
		getIndustryIncomeList(sixDaylist,buildType,totalMap,singleMap);
		//今天不同行业的收入
		long now=System.currentTimeMillis();
		List<Document> documentList = LogDb.daySummaryHistoryIncome(TimeUtil.todayStartTime(), now, LogDb.getBuyInShelf());
		getTodayIndustryIncomeList(documentList,now,0);
		documentList = LogDb.daySummaryHistoryIncome(TimeUtil.todayStartTime(), now, LogDb.getNpcBuyInShelf());
		getTodayIndustryIncomeList(documentList,now,Ss.BuildType.RETAILSHOP.getNumber());
		documentList = LogDb.daySummaryHistoryIncome(TimeUtil.todayStartTime(), now, LogDb.getNpcRentApartment());
		List<Document> todaylist=getTodayIndustryIncomeList(documentList,now,Ss.BuildType.APARTMENT.getNumber());
		//...研究所和广告公司
		//今天的总收入
		getIndustryIncomeList(todaylist,buildType,totalMap,singleMap);
		//一周的数据
		totalMap.forEach((k,v)->{
			Ss.IndustryDevelopment.IndustryInfo.Builder industryInfo=Ss.IndustryDevelopment.IndustryInfo.newBuilder();
			industryInfo.setTime(k).setAmount(singleMap.get(k)).setTotalAmount(v).setPercent(singleMap.get(k)/v/1.d);
			b.addIndustryInfo(industryInfo);
		});
		this.write(Package.create(cmd,b.build()));
	}
	private void getIndustryIncomeList(List<Document> list,int buildType,Map<Long,Long> totalMap,Map<Long,Long> singleMap){
		list.forEach(document ->{
			long time=document.getLong("time");
			long total=document.getLong("total");
			int type=document.getInteger("type");
			totalMap.put(time,total+(totalMap.get(time)!=null?totalMap.get(time):0));
			if(buildType==type){//每天指定的建筑类型的总收入
				singleMap.put(time,total);
			}
		});
	}
	private List<Document> getTodayIndustryIncomeList(List<Document> documentList,long now,int buildType){
		List<Document> todayIncomeList=new ArrayList<Document>();
		documentList.forEach(document -> {
			Document d=null;
			int type= document.getInteger("id");
			long total=document.getLong("total");
			if(buildType==0){
				if(type==21){//原料厂
					d=new Document().append("time",now).append("total",total).append("type", Ss.BuildType.MATERIAL.getNumber());
				}else{//加工厂
					d=new Document().append("time",now).append("total",total).append("type",  Ss.BuildType.PRODUCE.getNumber());
				}
			}
			d=new Document().append("time",now).append("total",total).append("type", buildType);
			todayIncomeList.add(d);
		});
		return todayIncomeList;
	}
	public void queryIndustryCompetition(short cmd, Message message){
		Ss.IndustryCompetition msg = (Ss.IndustryCompetition)message;
		int buildType=msg.getType().getNumber();

		Ss.IndustryCompetition.Builder b=Ss.IndustryCompetition.newBuilder();
		b.setType(msg.getType());
		//前六天数据
		List<Document> sixDaylist=SummaryUtil.queryWeekIndustryCompetition(SummaryUtil.getDayBuildingBusiness(),buildType);
		sixDaylist.forEach(d->{
			Ss.IndustryCompetition.IndustryInfo.Builder info=Ss.IndustryCompetition.IndustryInfo.newBuilder();
			info.setCompanyNum(d.getLong("n")).setStaffNum(d.getLong("total")).setTime(d.getLong("time"));
			b.addIndustryInfo(info);
		});
		//今天的数据
        List<Document> todaylist=LogDb.playerBuildingBusiness(TimeUtil.todayStartTime(),System.currentTimeMillis(),LogDb.getPlayerBuildingBusiness(),buildType);
        todaylist.forEach(d->{
            Ss.IndustryCompetition.IndustryInfo.Builder info=Ss.IndustryCompetition.IndustryInfo.newBuilder();
            info.setCompanyNum(d.getLong("n")).setStaffNum(d.getLong("total")).setTime(System.currentTimeMillis());
            b.addIndustryInfo(info);
        });
		this.write(Package.create(cmd,b.build()));
    }
	/*查询今日的商品销售情况(建筑经营详情,yty)*/
	public void queryBuildingSaleDetail(short cmd, Message message){
		Ss.QueryBuildingSaleDetail saleDetail = (Ss.QueryBuildingSaleDetail) message;
		UUID bid = Util.toUuid(saleDetail.getBuildingId().toByteArray());
		int buildingType = saleDetail.getType();
		//返回数据
		Ss.BuildingTodaySaleDetail.Builder builder = Ss.BuildingTodaySaleDetail.newBuilder();
		builder.setBuildingId(saleDetail.getBuildingId());
		//1.查询并且获取到7天的销售(出现过的商品)信息
		Map<Long, Map<ItemKey, Document>> historyDetail = SummaryUtil.queryBuildingGoodsSoldDetail(SummaryUtil.getDayBuildingGoodSoldDetail(), bid);
		/*2.查询今日的经营详情，直接从buyInshelf中统计（需要根据建筑类型来选择统计哪些）*/
		List<Document> todaySale = new ArrayList<>();
		Long todayStartTime = TimeUtil.todayStartTime();
		Long now = System.currentTimeMillis();
		if(buildingType==MetaBuilding.RETAIL){/*只有零售店需要到npc购买统计表中统计数据*/
			todaySale = LogDb.buildingDaySaleDetailByBuilding(todayStartTime,now,bid,LogDb.getNpcBuyInShelf());//统计今天的销售额
		}else{
			todaySale = LogDb.buildingDaySaleDetailByBuilding(todayStartTime,now,bid,LogDb.getBuyInShelf());//统计今天的销售额
		}
		//今日收入销售信息
		List<ItemKey> todayAllItem = new ArrayList<>();//保存今日有哪些商品销售
		for (Document d : todaySale) {
			ItemKey itemKey = new ItemKey(d.getInteger("itemId"), (UUID) d.get("p"));
			todayAllItem.add(itemKey);
			Ss.BuildingTodaySaleDetail.TodaySaleDetail saleDetail1 = TotalUtil.totalBuildingSaleDetail(d, buildingType, true);
			builder.addTodaySaleDetail(saleDetail1);
		}
		//从7天内的历史记录获取是否有其他的销售记录（有则代表记录为0）
		Map<ItemKey, Long> yesterdaySale = new HashMap<>();//昨日的销售额度
		historyDetail.forEach((k,v)->{
			v.forEach((key,doc)->{
				if(doc.getLong("time")<TimeUtil.todayStartTime()&&doc.getLong("time")>=TimeUtil.getTimeDayStartTime(TimeUtil.todayStartTime()-1)){
					yesterdaySale.put(key, doc.getLong("total"));
				}
				if(!todayAllItem.contains(key)){
					todayAllItem.add(key);
					Ss.BuildingTodaySaleDetail.TodaySaleDetail saleDetail1 = TotalUtil.totalBuildingSaleDetail(doc, buildingType, false);//统计历史中的收入记录，但今日收益为0
					builder.addTodaySaleDetail(saleDetail1);
				}
			});
		});
		/*求提升比例,如果昨天的销售记录中存在，就取出计算比例，只有今天的就设置为1，今天和昨天都没有设置为0 */
		for (Ss.BuildingTodaySaleDetail.TodaySaleDetail.Builder todaySaleInfo : builder.getTodaySaleDetailBuilderList()) {
			ItemKey itemKey = new ItemKey(todaySaleInfo.getItemId(),Util.toUuid(todaySaleInfo.getProducerId().toByteArray()));
			if(yesterdaySale.containsKey(itemKey))
				todaySaleInfo.setIncreasePercent(todaySaleInfo.getSaleAccount() / yesterdaySale.get(itemKey));
			else {
				if (todaySaleInfo.getSaleAccount() > 0) {
					todaySaleInfo.setIncreasePercent(1);
				} else {
					todaySaleInfo.setIncreasePercent(0);
				}
			}
		}
		this.write(Package.create(cmd,builder.build()));
	}

	//获取商品历史统计（7天）(建筑经营详情,yty)
	public void queryHistoryBuildingSaleDetail(short cmd, Message message){
		Ss.QueryHistoryBuildingSaleDetail itemInfo = (Ss.QueryHistoryBuildingSaleDetail) message;
		UUID bid = Util.toUuid(itemInfo.getBuildingId().toByteArray());
		UUID producerId = Util.toUuid(itemInfo.getProducerId().toByteArray());
		int itemId = itemInfo.getItemId();
		int buildingType = itemInfo.getType();
		Map<Long, Document> documentMap = SummaryUtil.queryBuildingGoodsSoldDetail(SummaryUtil.getDayBuildingGoodSoldDetail(), itemId, bid, producerId);
		Ss.BuildingHistorySaleDetail.Builder builder = Ss.BuildingHistorySaleDetail.newBuilder();
		builder.setBuildingId(Util.toByteString(bid));
		documentMap.forEach((k,doc)->{
			Ss.BuildingHistorySaleDetail.HistorySaleDetail.Builder history = Ss.BuildingHistorySaleDetail.HistorySaleDetail.newBuilder();
			history.setTime(k)
					.setItemId(doc.getInteger("itemId"));
			Ss.BuildingHistorySaleDetail.HistorySaleDetail.SaleDetail.Builder detail = Ss.BuildingHistorySaleDetail.HistorySaleDetail.SaleDetail.newBuilder();
			long num = doc.getLong("num");
			detail.setIncome(doc.getLong("total"))
					.setSaleNum((int)num);
			history.setSaleDetail(detail);
			builder.addHistoryDetail(history);
		});
		this.write(Package.create(cmd,builder.build()));
	}

	public void queryIndustryTop(short cmd, Message message) {
		Ss.queryTop top = (Ss.queryTop) message;
		UUID pid = Util.toUuid(top.getPid().toByteArray()); // 玩家id
		int type = top.getIndustryType();  // 行业类型
		this.write(Package.create(cmd, SummaryUtil.queryIndustryTop(pid,type)));
	}
}
