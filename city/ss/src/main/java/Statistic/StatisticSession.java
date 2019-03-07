package Statistic;

import java.util.UUID;

import org.apache.log4j.Logger;

import com.google.protobuf.Message;

import Shared.LogDb;
import Shared.Package;
import Shared.Util;
import Statistic.SummaryUtil.CountType;
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
    
    public void queryGoodsNpcNum(short cmd)
    {
    	Ss.GoodsNpcNum.Builder builder = Ss.GoodsNpcNum.newBuilder();
    	builder.setYestodayNpcNum(SummaryUtil.getYesterdayNpcData(SummaryUtil.getDayGoodsNpcNum(),CountType.BYDAY));
    	builder.setHourNpcNum(SummaryUtil.getYesterdayNpcData(SummaryUtil.getDayGoodsNpcNum(),CountType.BYHOUR));
    	this.write(Package.create(cmd, builder.build()));
    }
    
    public void queryNpcExchangeAmount(short cmd)
    {
    	Ss.NpcExchangeAmount.Builder builder = Ss.NpcExchangeAmount.newBuilder();
    	//npc购买商品的交易量
    	long yesterdayNpcBuyInShelf=SummaryUtil.getYesterdayNpcData(SummaryUtil.getDayNpcBuyInShelf(),CountType.BYDAY);
    	long todayNpcBuyInShelf=SummaryUtil.getTodayNpcData(SummaryUtil.getDayNpcBuyInShelf());
    	//npc租房的交易量
    	long yesterdayNpcRentApartment=SummaryUtil.getYesterdayNpcData(SummaryUtil.getDayNpcRentApartment(),CountType.BYDAY);
    	long todayNpcRentApartment=SummaryUtil.getTodayNpcData(SummaryUtil.getDayNpcRentApartment());
    	
    	builder.setNpcExchangeAmount(yesterdayNpcBuyInShelf+todayNpcBuyInShelf+yesterdayNpcRentApartment+todayNpcRentApartment);
    	this.write(Package.create(cmd, builder.build()));
    }
    
    public void queryExchangeAmount(short cmd)
    {
    	Ss.ExchangeAmount.Builder builder = Ss.ExchangeAmount.newBuilder();
    	//npc交易量
    	long yesterdayNpcBuyInShelf=SummaryUtil.getYesterdayNpcData(SummaryUtil.getDayNpcBuyInShelf(),CountType.BYDAY);
    	long todayNpcBuyInShelf=SummaryUtil.getTodayNpcData(SummaryUtil.getDayNpcBuyInShelf());
    	long yesterdayNpcRentApartment=SummaryUtil.getYesterdayNpcData(SummaryUtil.getDayNpcRentApartment(),CountType.BYDAY);
    	long todayNpcRentApartment=SummaryUtil.getTodayNpcData(SummaryUtil.getDayNpcRentApartment());
    	//player交易量
    	
    	
    	builder.setExchangeAmount(0l);
    	this.write(Package.create(cmd, builder.build()));
    }
}
