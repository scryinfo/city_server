package Game;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import ccapi.GlobalDef.*;
import ccapi.Dddbind.*;
import ccapi.CcOuterClass.*;

/**
 * A simple client that requests a greeting from the {@link HelloWorldServer}.
 */
public class chainClient {
    static private chainClient _instance = null;
    static public chainClient instance(){
        if(_instance == null){
            _instance = new chainClient("localhost",50020);
        }
        return  _instance;
    }
    private static final Logger logger = Logger.getLogger(chainClient.class.getName());

    private final ManagedChannel channel;
    private final ccapi.CcGrpc.CcBlockingStub blockingStub;

    /** Construct client connecting to HelloWorld server at {@code host:port}. */
    public chainClient(String host, int port) {
        this(ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build());
    }

    /** Construct client for accessing HelloWorld server using the existing channel. */
    chainClient(ManagedChannel channel) {
        this.channel = channel;
        blockingStub = ccapi.CcGrpc.newBlockingStub(channel);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    /** Say hello to server. */
    /*public void greet(String name) {
        logger.info("Will try to greet " + name + " ...");
        HelloRequest request = HelloRequest.newBuilder().setName(name).build();
        HelloReply response;
        try {
            response = blockingStub.sayHello(request);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
        logger.info("Greeting: " + response.getMessage());
    }*/
    public ccapi.GlobalDef.ResHeader CreateUser(ccapi.CcOuterClass.CreateUserReq req) {
        logger.info("Will try to greet " + req.getCityUserName() + " ...");
        ccapi.GlobalDef.ResHeader response;
        try {
            response = blockingStub.createUser(req);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return null;
        }
        logger.info("Greeting: " + response.getErrMsg());
        return response;
    }

    public void RechargeRequestReq(ccapi.CcOuterClass.RechargeRequestReq req) {
        logger.info("Will try to greet " + req.getPurchaseId() + " ...");
        ccapi.CcOuterClass.RechargeRequestRes response;
        try {
            response = blockingStub.rechargeRequest(req);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
        logger.info("Greeting: " + response.getPurchaseId());
    }

    /**
     * Greet server. If provided, the first element of {@code args} is the name to use in the
     * greeting.
     */
    /*public static void main(String[] args) throws Exception {
        //chainClient client = new chainClient("localhost", 50051);
        chainClient client = new chainClient("192.168.0.191", 50051);
        try {
            *//* Access a service running on the local machine on port 50051 *//*
            String user = "world";
            if (args.length > 0) {
                user = args[0]; *//* Use the arg as the name to greet if provided *//*
            }
            client.greet(user);
        } finally {
            client.shutdown();
        }
    }*/
}
