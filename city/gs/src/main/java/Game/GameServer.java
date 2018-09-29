package Game;

import Shared.*;
import Shared.Package;
import com.google.common.collect.MapMaker;
import ga.Ga;
import gacode.GaCode;
import io.netty.bootstrap.Bootstrap;
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
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.apache.log4j.Logger;
import org.bson.types.ObjectId;

import java.net.InetSocketAddress;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

public class GameServer {
    private static final Logger logger = Logger.getLogger(GameServer.class);
    private static final EventExecutorGroup businessLogicExecutor = new DefaultEventExecutorGroup(4);
    public static final ChannelGroup allClientChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    public static final ConcurrentMap<UUID, GameSession> allGameSessions = new MapMaker().concurrencyLevel(1).weakValues().makeMap();
    private static int id;
    public static GameServerInfo gsInfo;
    public static AccountServerInfo accInfo;
    public int getId() {
        return id;
    }
    public GameServer() throws Exception {
        ServerCfgDb.init(GlobalConfig.configUri());
        ServerCfgDb.startUp();
        accInfo = ServerCfgDb.getAccountserverInfo();
        this.id = GlobalConfig.serverId();
        gsInfo = ServerCfgDb.getGameServerInfo(this.getId());
        if(gsInfo == null)
            throw new Exception("can not find game server " + this.id + " in cfg db");
        LogDb.init(gsInfo.getLogDbUri());
        LogDb.startUp();
        MetaData.init(gsInfo.getMetaDbUri());
        MetaData.startUp();

        // db info is in hibernate.xml now
        //GameDb.init(gsInfo.getGameDbUri());
        //GameDb.startUp();
    }

    private void asConnectAction(ChannelFuture f) {
        Ga.Login.Builder c = Ga.Login.newBuilder();
        c.setId(GameServer.id);
        f.channel().writeAndFlush(Package.create(GaCode.OpCode.login_VALUE, c.build()));
        System.out.println("login to account server");
    }
    public void run() throws Exception {
        City.init(MetaData.getCity()); // some other object depend on city, so init it first
        NpcManager.instance(); // load all npc, npc will refer building(enter it)
        GroundAuction.instance();

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
                                ch.pipeline().addLast(new PackageEncoder());
                                ch.pipeline().addLast(businessLogicExecutor, new AccountServerEventHandler());
                                ch.pipeline().addLast(new AutoReconnectHandler(b, GameServer.this::asConnectAction));
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
                                ch.pipeline().addLast(new PackageEncoder());
                                //ch.pipeline().addLast(new IdleStateHandler(10, 10, 0));
                                ch.pipeline().addLast(new GameEventHandler());
                                ch.pipeline().addLast(businessLogicExecutor, new GameEventHandler()); // seems helpless. it only can relieve some db read operation cost due to all business are run in city thread
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
        // By default, logging is enabled via the popular SLF4J API. The use of SLF4J is optional; the driver will use SLF4J if the driver detects the presence of SLF4J in the classpath. Otherwise, the driver will fall back to JUL (java.util.logging)
        java.util.logging.Logger.getLogger("org.mongodb").setLevel(java.util.logging.Level.OFF);

        GlobalConfig.init(args[0]);
        new GameServer().run();
    }
}