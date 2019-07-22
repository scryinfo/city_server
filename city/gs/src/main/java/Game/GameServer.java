package Game;

import Game.Contract.ContractManager;
import Game.Eva.EvaManager;
import Game.FriendManager.FriendManager;
import Game.FriendManager.SocietyManager;
import Game.Gambling.FlightManager;
import Game.Gambling.ThirdPartyDataSource;
import Game.League.LeagueManager;
import Game.Meta.MetaData;
import Game.Util.BuildingUtil;
import Game.ddd.chainRpcMgr;
import Game.ddd.dddPurchaseMgr;
import Shared.*;
import Shared.Package;
import com.google.common.collect.MapMaker;
import ga.Ga;
import gacode.GaCode;
import gs.Gs;
import gscode.GsCode;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelMatcher;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.apache.log4j.Logger;

import java.net.InetSocketAddress;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GameServer {
    public static void testRechargeRequestRes(){
        String str0 = "123456";
        String str1 = str0.substring(0,5);
        int xx = 0;
        /*CcOuterClass.RechargeRequestRes.Builder pRechargeRequestRes = CcOuterClass.RechargeRequestRes.newBuilder();
        ddd_purchase pur = new ddd_purchase(UUID.randomUUID(),UUID.randomUUID(),1,"addsfrom","addsto");
        *//*if(ccapiReq.getSignature().size() > 0)
            pur.setResp_Signature(Hex.decode(ccapiReq.getSignature().toStringUtf8()));*//*
        Dddbind.ct_RechargeRequestRes.Builder msg = Dddbind.ct_RechargeRequestRes.newBuilder();
        pRechargeRequestRes
                .setPurchaseId(pur.purchaseId.toString())
                .setEthAddr(pur.ddd_from)
                .setTs(pur.getCreate_time())
                .setExpireTime(pur.getExpire_time());
                //.setSignature(ByteString.copyFrom(pur.getResp_Signature()));
        msg.setPlayerId(Util.toByteString(pur.player_id)).setRechargeRequestRes(pRechargeRequestRes.build());*/
    }
    private static final Logger logger = Logger.getLogger(GameServer.class);
    private static final ScheduledExecutorService thirdPartyDataSourcePullExecutor = Executors.newScheduledThreadPool(1);
    private static final EventExecutorGroup businessLogicExecutor = new DefaultEventExecutorGroup(4);
    public static final ChannelGroup allClientChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    public static final ConcurrentMap<UUID, GameSession> allGameSessions = new MapMaker().concurrencyLevel(1).weakValues().makeMap();
    private static int id;
    public static GameServerInfo gsInfo;
    public static AccountServerInfo accInfo;
    public int getId() {
        return id;
    }
    private static class ChannelIdsMatcher implements ChannelMatcher {
        private Collection<ChannelId> channelIds;
        ChannelIdsMatcher(Collection<ChannelId> ids) {
            this.channelIds = ids;
        }
        @Override
        public boolean matches(Channel channel) {
            return channelIds.contains(channel.id());
        }
    }
    public static void sendTo(Collection<ChannelId> ids, Package pack) {
        allClientChannels.writeAndFlush(pack, new ChannelIdsMatcher(ids));
    }
    public static void sendTo(List<UUID> roleIds, Package pack) {
        roleIds.forEach(id->{
            GameSession session = allGameSessions.get(id);
            if(session != null) {
                session.write(pack);
            }
        });
    }

    public static boolean isOnline(UUID id)
    {
        return allGameSessions.get(id) != null;
    }
    public static void sendToAll(Package pack) {
        allClientChannels.writeAndFlush(pack);
    }
    public static void sendIncomeNotity(UUID roleId, Gs.IncomeNotify notify)
    {
        LogDb.insertIncomeNotify(roleId,notify);
        GameSession session = allGameSessions.get(roleId);
        if(session != null) {
            session.write(Package.create(GsCode.OpCode.incomeNotify_VALUE,notify));
        }
    }
    public GameServer() throws Exception {
        GameEventDispatcher.getInstance(); //just used to check error when process start run
        ServerCfgDb.init(GlobalConfig.configUri());
        ServerCfgDb.startUp();
        accInfo = ServerCfgDb.getAccountserverInfo();
        this.id = GlobalConfig.serverId();
        gsInfo = ServerCfgDb.getGameServerInfo(this.getId());
        if(gsInfo == null)
            throw new Exception("can not find game server " + this.id + " in cfg db");
        LogDb.init(gsInfo.getLogDbUri(), gsInfo.getLogDbName());
        LogDb.startUp();
        MetaData.init(gsInfo.getMetaDbUri());
        MetaData.startUp();
        // db info is in hibernate.xml now
        //GameDb.startUp(gsInfo.getGameDbUrl());
        //GameDb.startUp();
    }

    private void asConnectAction(ChannelFuture f) {
        Ga.Login.Builder c = Ga.Login.newBuilder();
        c.setId(GameServer.id);
        f.channel().writeAndFlush(Package.create(GaCode.OpCode.login_VALUE, c.build()));
        System.out.println("login to account server");
    }
    public void run() throws Exception {
        FriendManager.getInstance().init();
        City.init(MetaData.getCity()); // some other object depend on java, so startUp it first
        NpcManager.instance(); // load all npc, npc will refer building(enter it)
        GroundAuction.init();
        GroundManager.init();
        PromotionMgr.init();
        Exchange.init();
        TechTradeCenter.init();
        MoneyPool.init();
        ContractManager.getInstance().init();
        LeagueManager.getInstance().init();
        EvaManager.getInstance().init();
        TickManager.init();
        BrandManager.init();
        FlightManager.init();
        SocietyManager.init();
        BuildingUtil.instance().updateMaxOrMinTotalQty();//初始化全城最大最小建筑品质
        dddPurchaseMgr.init();
        chainRpcMgr.instance();

        // DO NOT put init below this!!! java might can't see the init
        City.instance().run();
        thirdPartyDataSourcePullExecutor.execute(() -> ThirdPartyDataSource.instance().updateWeatherInfo());
        thirdPartyDataSourcePullExecutor.scheduleAtFixedRate(()->{
            try {
                ThirdPartyDataSource.instance().update(TimeUnit.SECONDS.toMillis(10));
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }, 30, 10, TimeUnit.SECONDS);
        EventLoopGroup clientGroup = new NioEventLoopGroup();
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            List<ChannelFuture> fs = new ArrayList<ChannelFuture>();
            {
                Bootstrap b = new Bootstrap();
                b.group(clientGroup)
                        .channel(NioSocketChannel.class)
                        .remoteAddress(new InetSocketAddress(accInfo.getLanIp(), accInfo.getLanPortGs()))
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            public void initChannel(SocketChannel ch) throws Exception {
                                ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(ByteOrder.LITTLE_ENDIAN,1024, 0, 4,2,4, true));
                                ch.pipeline().addLast(new PackageDecoder());
                                ch.pipeline().addLast(new PackageEncoder(GaCode.OpCode.class));
                                ch.pipeline().addLast(businessLogicExecutor, new AccountServerEventHandler());
                                ch.pipeline().addLast(new AutoReconnectHandler(b, GameServer.this::asConnectAction));
                                ch.pipeline().addLast(new ExceptionHandler());
                            }
                        });
                ChannelFuture f = b.connect().sync();
                if (f.isSuccess())
                    asConnectAction(f);
            }
            {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            public void initChannel(SocketChannel ch) throws Exception {
                                ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(ByteOrder.LITTLE_ENDIAN,1024, 0, 4,2,4, true));
                                ch.pipeline().addLast(new PackageDecoder());
                                ch.pipeline().addLast(new PackageEncoder(GsCode.OpCode.class));
                                //ch.pipeline().addLast(new IdleStateHandler(10, 10, 0));
                                ch.pipeline().addLast(businessLogicExecutor, new GameEventHandler()); // seems helpless. it only can relieve some db read operation cost due to all business are run in java thread
                                ch.pipeline().addLast(new ExceptionHandler());
                            }
                        }).option(ChannelOption.SO_BACKLOG, 128).option(ChannelOption.SO_REUSEADDR, true)
                        .childOption(ChannelOption.SO_KEEPALIVE, true);

                fs.add(b.bind(gsInfo.getPort()));
                System.out.println("listening on port " + gsInfo.getPort());
            }
            for (ChannelFuture f : fs) {
                f.channel().closeFuture().sync();
            }
        } finally {
            clientGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
    public static void main(String[] args) throws Exception {
        testRechargeRequestRes();
        // By default, logging is enabled via the popular SLF4J API. The use of SLF4J is optional; the driver will use SLF4J if the driver detects the presence of SLF4J in the classpath. Otherwise, the driver will fall back to JUL (java.util.logging)
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.SIMPLE);
        java.util.logging.Logger.getLogger("org.mongodb").setLevel(java.util.logging.Level.OFF);
        GlobalConfig.init(args[0]);
        GameDb.startUp(args[1]);
        new GameServer().run();
    }
//    public static void main1(String[] args) throws Exception {
//        java.util.logging.Logger.getLogger("org.mongodb").setLevel(java.util.logging.Level.OFF);
//        GlobalConfig.init(args[0]);
//        GameDb.startUp(args[1]);
//
//        GameServer gs = new GameServer();
//        City.init(MetaData.getCity()); // some other object depend on java, so startUp it first
//
//
//    }
}