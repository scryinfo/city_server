package Account;

import Shared.*;
import ascode.AsCode;
import gacode.GaCode;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.math.BigInteger;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


public class AccountServer {
	private static AccountServerInfo accInfo;
	private static final EventExecutorGroup businessLogicExecutor = new DefaultEventExecutorGroup(4);
	public static final ChannelGroup allGsChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
	public static final ChannelGroup allClientChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
	public static final ConcurrentHashMap<String, ChannelId> clientAccountToChannelId = new ConcurrentHashMap<>();
	public static final ConcurrentHashMap<Integer, ChannelId> gsIdToChannelId = new ConcurrentHashMap<>();
	public static MessageDigest md5;
	public AccountServer() {
		ServerCfgDb.init(GlobalConfig.configUri());
		ServerCfgDb.startUp();
		AccountDb.init(GlobalConfig.configUri());
		AccountDb.startUp();
		accInfo =  ServerCfgDb.getAccountserverInfo();
	}
	public static String getMd5Str(String in)
	{
		MessageDigest md5 = null;
		try
		{
			md5 = MessageDigest.getInstance("MD5");
		}
		catch (NoSuchAlgorithmException e)
		{
			e.printStackTrace();
		}
		md5.update(in.getBytes());
		byte[] bytes = md5.digest();
		String md5String = new BigInteger(1,bytes).toString(16);
		int i = 32 - md5String.length();
		StringBuilder builder = new StringBuilder();
		for (; i  > 0; i--)
		{
			builder.append("0");
		}
		return builder.append(md5String).toString();
	}

	public void run() throws Exception {
		YunSmsManager.getInstance();

		EventLoopGroup bossGroup = new NioEventLoopGroup();
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		try {
			List<ChannelFuture> fs = new ArrayList<>();
            {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            public void initChannel(SocketChannel ch) throws Exception {
								ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(ByteOrder.LITTLE_ENDIAN,1024, 0, 4,2,4, true));
                                ch.pipeline().addLast(new PackageDecoder());
                                ch.pipeline().addLast(new PackageEncoder(GaCode.OpCode.class));
                                ch.pipeline().addLast(businessLogicExecutor, new GameServerEventHandler());
								ch.pipeline().addLast(new ExceptionHandler());
                            }
                        }).option(ChannelOption.SO_REUSEADDR, true);
                fs.add(b.bind(accInfo.getLanPortGs()));
            }
            System.out.println("listening on port " + accInfo.getLanPortGs() + " for gs");
			{
				ServerBootstrap b = new ServerBootstrap();
				b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
						.childHandler(new ChannelInitializer<SocketChannel>() {
							@Override
							public void initChannel(SocketChannel ch) throws Exception {
								ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(ByteOrder.LITTLE_ENDIAN,1024, 0, 4,2,4, true));
								ch.pipeline().addLast(new PackageDecoder());
								ch.pipeline().addLast(new PackageEncoder(AsCode.OpCode.class));
								//ch.pipeline().addLast(new IdleStateHandler(10, 10, 0));
								ch.pipeline().addLast(businessLogicExecutor, new AccountEventHandler());
								ch.pipeline().addLast(new ExceptionHandler());
							}
						}).option(ChannelOption.SO_REUSEADDR, true);
				fs.add(b.bind(accInfo.getInternetPort()));
			}
            System.out.println("listening on port for client " + accInfo.getInternetPort() + " for client");

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
		new AccountServer().run();
	}
}
