package Game.ddd;

import Game.GameDb;
import Game.Player;
import Shared.Package;
import Shared.Util;
import ccapi.CcGrpc.CcBlockingStub;
import ccapi.CcOuterClass;
import ccapi.GlobalDef;
import common.Common;
import gscode.GsCode;
import io.grpc.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple client that requests a greeting from the {@link HelloWorldServer}.
 */
public class chainRpcMgr {
    static private chainRpcMgr _instance = null;
    static public chainRpcMgr instance() throws IOException {
        if(_instance == null){
            _instance = new chainRpcMgr("localhost",50020);
        }
        return  _instance;
    }
    private static final Logger logger = Logger.getLogger(chainRpcMgr.class.getName());

    private final ManagedChannel channelCl;
    private final CcBlockingStub blockingStubCl;

    private Server server;

    /** Construct client connecting to HelloWorld server at {@code host:port}. */
    public chainRpcMgr(String host, int clientPort) throws IOException {
        this(ManagedChannelBuilder.forAddress(host, clientPort)
                .usePlaintext()
                .build());
        logger.info("Client started, listening on " + clientPort);

        /* The port on which the server should run */
        int serPort = 50051;
        server = ServerBuilder.forPort(serPort)
                .addService(new RechargeResultImpl())
                .build()
                .start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                chainRpcMgr.this.stop();
                System.err.println("*** server shut down");
            }
        });
    }

    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    /** Construct client for accessing HelloWorld server using the existing channelCl. */
    chainRpcMgr(ManagedChannel channelCl) {
        this.channelCl = channelCl;
        blockingStubCl = ccapi.CcGrpc.newBlockingStub(channelCl);
    }

    public void shutdown() throws InterruptedException {
        channelCl.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public void CreateUser(ccapi.CcOuterClass.CreateUserReq req) {
        logger.info("Will try to greet " + req.getCityUserName() + " ...");
        ccapi.GlobalDef.ResHeader response;
        try {
            response = blockingStubCl.createUser(req);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
        logger.info("Greeting: " + response.getErrMsg());
    }

    public ccapi.CcOuterClass.RechargeRequestRes RechargeRequestReq(ccapi.CcOuterClass.RechargeRequestReq req) {
        logger.info("Will try to greet " + req.getPurchaseId() + " ...");
        ccapi.CcOuterClass.RechargeRequestRes response;
        try {
            response = blockingStubCl.rechargeRequest(req);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return ccapi.CcOuterClass.RechargeRequestRes.newBuilder().setResHeader(
                    ccapi.GlobalDef.ResHeader.newBuilder()
                            .setErrMsg(e.getMessage())
                            .setErrCode(ccapi.GlobalDef.ErrCode.ERR_FAILED)
            ).build();
        }
        logger.info("Greeting: " + response.getPurchaseId());
        return response;
    }

    public ccapi.CcOuterClass.DisChargeRes DisChargeReq(ccapi.CcOuterClass.DisChargeReq req) {
        logger.info("Will try to greet " + req.getPurchaseId() + " ...");
        ccapi.CcOuterClass.DisChargeRes response;
        try {
            response = blockingStubCl.disCharge(req);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return ccapi.CcOuterClass.DisChargeRes.newBuilder().setResHeader(ccapi.GlobalDef.ResHeader.newBuilder()
                    .setErrCode(GlobalDef.ErrCode.ERR_FAILED).setErrMsg(e.getMessage())).build();
        }

        if(response.getResHeader().getErrCode() != GlobalDef.ErrCode.ERR_SUCCESS){
            return response;
        }

        logger.info("Greeting: " + response.getPurchaseId());
        return response;
    }

    //grpc服务器------------------------------------------------------------------------------------------------------
    static class RechargeResultImpl extends cityapi.CityGrpc.CityImplBase{
        @Override
        public void rechargeResult(cityapi.CityOuterClass.RechargeResultReq req, io.grpc.stub.StreamObserver<cityapi.CityOuterClass.RechargeResultRes> responseObserver) {
            logger.info("Client RechargeResultRes recived ");
            ccapi.GlobalDef.ResHeader.Builder ResHeader = ccapi.GlobalDef.ResHeader.newBuilder();
            ResHeader.setReqId(req.getReqHeader().getReqId()).setVersion(req.getReqHeader().getVersion());
            cityapi.CityOuterClass.RechargeResultRes reply = cityapi.CityOuterClass.RechargeResultRes.newBuilder().setResHeader(ResHeader).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
            dddPurchaseMgr.instance().on_dddMsg(req);
        }
    }
    //grpc服务器------------------------------------------------------------------------------------------------------
}
