package Statistic;

import Shared.*;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.Scheduler;
import org.quartz.impl.StdSchedulerFactory;
import sscode.SsCode;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

public class StatisticServer {
    private static final EventExecutorGroup businessLogicExecutor = new DefaultEventExecutorGroup(4);
    public static final ChannelGroup allClientChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private int id;
    private GameServerInfo serverInfo;
    public StatisticServer() {
        ServerCfgDb.init(GlobalConfig.configUri());
        ServerCfgDb.startUp();
        this.id = GlobalConfig.serverId();
        serverInfo = ServerCfgDb.getGameServerInfo(id);
        LogDb.init(serverInfo.getLogDbUri(),serverInfo.getLogDbName());
        SummaryUtil.init();
        StatisticEventDispatcher.getInstance();
    }

    public void run() throws Exception {
        Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
        scheduler.start();

        scheduler.scheduleJob(newJob(DayJob.class).build(), newTrigger()
                .withSchedule(/*CronScheduleBuilder.dailyAtHourAndMinute(0,0)*/
                        CronScheduleBuilder.cronSchedule("0 */10 * * * ?"))
                .build());
        scheduler.scheduleJob(newJob(WeekJob.class).build(), newTrigger()
                .withSchedule(CronScheduleBuilder.weeklyOnDayAndHourAndMinute(1,0,0))
                .build());
        scheduler.scheduleJob(newJob(PerHourJob.class).build(), newTrigger()
                .withSchedule(CronScheduleBuilder.cronSchedule("0 55 */1 * * ?"))
                .build());//每种商品购买的npc,每隔1个小时执行,统计前一个小时的
        scheduler.scheduleJob(newJob(YesterdayJob.class).build(), newTrigger()
        		.withSchedule(CronScheduleBuilder.cronSchedule("0 0 1 * * ?"))
        		.build());//统计昨天包括以前的数据，每天凌晨1点统计
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        List<ChannelFuture> fs = new ArrayList<>();
        try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            public void initChannel(SocketChannel ch) throws Exception {
                                ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(ByteOrder.LITTLE_ENDIAN,1024, 0, 4,2,4, true));
                                ch.pipeline().addLast(new PackageDecoder());
                                ch.pipeline().addLast(new PackageEncoder(SsCode.OpCode.class));
                                //ch.pipeline().addLast(new IdleStateHandler(10, 10, 0));
                                ch.pipeline().addLast(businessLogicExecutor, new StatisticEventHandler());
                                ch.pipeline().addLast(new ExceptionHandler());
                            }
                        }).option(ChannelOption.SO_REUSEADDR, true);
                fs.add(b.bind(20001));
            System.out.println("listening on port for client " + 20001 + " for client");

            for(ChannelFuture f : fs)
            {
                f.sync().channel().closeFuture().sync();
            }
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        java.util.logging.Logger.getLogger("org.mongodb").setLevel(java.util.logging.Level.OFF);
        GlobalConfig.init(args[0]);
        new StatisticServer().run();
    }
}
