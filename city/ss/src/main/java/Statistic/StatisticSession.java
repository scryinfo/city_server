package Statistic;

import Shared.Package;
import Shared.Util;
import com.google.protobuf.Message;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import org.apache.log4j.Logger;
import ss.Ss;

import java.util.Map;
import java.util.UUID;

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
        UUID playerId = Util.toUuid((message).toByteArray());
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
}
