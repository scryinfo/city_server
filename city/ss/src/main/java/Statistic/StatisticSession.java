package Statistic;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.bson.Document;

import com.google.protobuf.Message;

import Shared.LogDb;
import Shared.Package;
import Shared.Util;
import Statistic.SummaryUtil.CountType;
import gs.Gs;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import ss.Ss;

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
        UUID buildingId = Util.toUuid((message).toByteArray());
        Ss.BuildingIncome.Builder builder = Ss.BuildingIncome.newBuilder();
        builder.setBuildingId(Util.toByteString(buildingId));
        SummaryUtil.getBuildIncomeById(buildingId).forEach((k,v) ->{
            Ss.NodeIncome.Builder node = Ss.NodeIncome.newBuilder()
                    .setIncome(v)
                    .setTime(k);
            builder.addNodes(node.build());
        });
        this.write(Package.create(cmd, builder.build()));
    }

    public void queryAllPlayerSex(short cmd)
    {
        Ss.SexInfo.Builder builder = Ss.SexInfo.newBuilder();
        builder.setMale(SummaryUtil.getSexInfo(true));
        builder.setFemale(SummaryUtil.getSexInfo(false));
        this.write(Package.create(cmd, builder.build()));
    }
    
    public void queryGoodsNpcNum(short cmd, Message message)
    {
    	Ss.GoodNpcNumInfo m = (Ss.GoodNpcNumInfo)message;
    	long time=m.getTime();
    	Ss.GoodsNpcNum.Builder list = Ss.GoodsNpcNum.newBuilder();
    	Ss.GoodNpcNumInfo.Builder info = Ss.GoodNpcNumInfo.newBuilder();
    	List<Document> ls=SummaryUtil.getGoodsNpcHistoryData(SummaryUtil.getDayGoodsNpcNum(),CountType.BYHOUR,time);
    	for (Document document : ls) {
    		info.setId(document.getInteger("id"));
    		info.setTotal(document.getLong("total"));
    		info.setTime(document.getLong("time"));
    		list.addGoodNpcNumInfo(info.build());
		}
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
    	long playerExchangeAmount=yesterdayPlayerBuyGround+todayPlayerBuyGround+yesterdayPlayerBuyInShelf+todayPlayerBuyInShelf+yesterdayPlayerRentGround+todayPlayerRentGround;
    	
    	builder.setExchangeAmount(npcExchangeAmount+playerExchangeAmount);
    	this.write(Package.create(cmd, builder.build()));
    }
    
    public void queryGoodsNpcNumCurve(short cmd, Message message)
    {
      	Ss.GoodsNpcNumCurve g = (Ss.GoodsNpcNumCurve)message;
    	int id=g.getId();
		Map<Long, Long> map=SummaryUtil.queryGoodsNpcNumCurve(SummaryUtil.getDayGoodsNpcNum(),id,CountType.BYHOUR);
		Ss.GoodsNpcNumCurveMap.Builder bd=Ss.GoodsNpcNumCurveMap.newBuilder();
		Ss.GoodsNpcNumCurve.Builder list = Ss.GoodsNpcNumCurve.newBuilder();
	    for (Map.Entry<Long, Long> entry : map.entrySet()) { 
			bd.setKey(entry.getKey());
			bd.setValue(entry.getValue());
			list.addGoodsNpcNumCurveMap(bd.build());
	    }
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
}
