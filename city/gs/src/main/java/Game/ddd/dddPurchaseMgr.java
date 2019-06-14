package Game.ddd;

import Game.GameDb;
import Game.Player;
import Shared.Package;
import Shared.Util;
import ccapi.CcOuterClass.DisChargeRes;
import ccapi.CcOuterClass.RechargeRequestRes;
import ccapi.Dddbind.ct_DisChargeRes;
import ccapi.Dddbind.ct_RechargeRequestRes;
import ccapi.GlobalDef;
import cityapi.City.RechargeResultReq;
import com.google.protobuf.ByteString;
import gscode.GsCode;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.*;

import static ccapi.GlobalDef.ErrCode.ERR_SUCCESS;

@Entity
public class dddPurchaseMgr {

    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKey(name = "purchaseId")
    @JoinColumn(name = "ddd_purchase_id")
    private Map<UUID, ddd_purchase> allddd_purchase = new HashMap<>();

    private static dddPurchaseMgr instance;
    public static dddPurchaseMgr instance() {
        if(instance == null){
            init();
        }
        return instance;
    }

    public static final int ID = 0;
    @Id
    private final int id = ID;

    public dddPurchaseMgr() {
    }

    public static void init() {
        GameDb.initDddPurchaseMgr();
        instance = GameDb.getDddPurchaseMgr();
}

    //添加订单
    public boolean addPurchase(ddd_purchase purchase){
        //不允许多次设置相同id的交易
        if(allddd_purchase.containsKey(purchase.purchaseId))
            return  false;

        //有足够的eee的情况下
        purchase.status = StatusPurchase.PROCESSING;
        Player player = GameDb.getPlayer(purchase.player_id);
        if(purchase.ddd < 0){
            //判断交易是否合法
            long eee = (long)GameDb.calGameCurrencyFromDDD(purchase.ddd);
            if(player.money() + eee < 0 ){ //游戏币不够
                return false;
            }
            //提币操作，需锁定eee
            player.lockMoney(purchase.purchaseId,eee);
        }

        allddd_purchase.put(purchase.purchaseId,purchase);
        //持久化
        if(purchase.ddd < 0){
            GameDb.saveOrUpdate(player);
        }
        GameDb.saveOrUpdate(this);
        return  true;
    }

    //移除订单，后边定期处理超期订单，目前先不考虑
    public void delPurchase(){
        List<ddd_purchase> toRemove = new ArrayList();
        for (Iterator<Map.Entry<UUID, ddd_purchase>> it = allddd_purchase.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, ddd_purchase> item = it.next();
            ddd_purchase odr = item.getValue();
            if(System.currentTimeMillis() - odr.create_time > odr.expire_time
                    && odr.status == StatusPurchase.PROCESSED){
                toRemove.add(odr);
                it.remove();
            }
        }
        GameDb.delete(toRemove);
        GameDb.saveOrUpdate(this);
    }

    //充值请求验证，正常city服务器转发就行，因为请求在ccapi服务器会进行验证，所以不需要city这边验证
    //city服务器验证的好处是可以防止恶意的刷单
    public boolean varifyPurchaseReq( UUID purchaseId ){
        ddd_purchase pur = allddd_purchase.get(purchaseId);
        if(pur != null ){
            return pur.verifyReq();
        }
        return false;
    }

    public boolean varifyPurchaseResp( UUID purchaseId ){
        //暂时还没有resp的验证,所以直接返回true
        return true;
        //后边有了resp的验证后，撤销下面的注销
        /*ddd_purchase pur = allddd_purchase.get(purchaseId);
        if(pur != null ){
            return pur.verifyResp();
        }
        return false;*/
    }

    //这个是充值请求成功后的回调
    public void on_RechargeReqResp(RechargeResultReq ccapiReq){
        UUID purId = Util.toUuid(ccapiReq.getCityPurchaseId().getBytes());
        ddd_purchase pur = allddd_purchase.get(purId);
        if(varifyPurchaseResp(purId)){ //验证ccapi请求
            Player player = GameDb.getPlayer(pur.player_id);
            if(pur == null){
                //todo 这种是异常的情况，city服务器没有订单，但是ccap服务器还有，这种情况需要人工处理
            }

            long eee = 0;
            if(pur.ddd < 0){
                eee = player.unlockMoney(purId);
            }else{
                eee = (long)GameDb.calGameCurrencyFromDDD(pur.ddd);
            }
            player.addMoney(eee);
            pur.status = StatusPurchase.PROCESSED;

            GameDb.saveOrUpdate(player);

            if(pur.ddd > 0){//充值成功
                ct_RechargeRequestRes.Builder msg = ct_RechargeRequestRes.newBuilder();
                GlobalDef.ResHeader.Builder header = GlobalDef.ResHeader.newBuilder();
                header.setErrCode(ERR_SUCCESS).setReqId(ccapiReq.getReqHeader().getReqId())
                .setVersion(ccapiReq.getReqHeader().getVersion());
                RechargeRequestRes.Builder pRechargeRequestRes = RechargeRequestRes.newBuilder();

                pRechargeRequestRes
                        .setPurchaseId(pur.purchaseId.toString())
                        .setEthAddr(pur.ddd_from)
                        .setTs(pur.create_time)
                        .setExpireTime(pur.expire_time)
                        .setSignature(ByteString.copyFrom(pur.req_Signature));

                msg.setPlayerId(Util.toByteString(pur.player_id)).setRechargeRequestRes(pRechargeRequestRes);
                Package pack = Package.create(GsCode.OpCode.ct_RechargeRequestRes_VALUE, msg.build());
                player.send(pack);
            }else{//提币
                ct_DisChargeRes.Builder msg = ct_DisChargeRes.newBuilder();
                DisChargeRes.Builder disC = DisChargeRes.newBuilder();
                disC.setAmount(String.valueOf(pur.ddd))
                    .setPurchaseId( pur.purchaseId.toString())
                    .setResHeader(GlobalDef.ResHeader.newBuilder().setErrCode(ERR_SUCCESS).setReqId(ccapiReq.getReqHeader().getReqId()).setVersion(ccapiReq.getReqHeader().getVersion()));
                msg.setPlayerId(Util.toByteString(pur.player_id)).setDisChargeRes(disC.build());
            }
        }
    }

    //这个是充值上链成功后的回调
    public void on_RechargeSuccess(UUID pid, UUID purchaseId, int ErrCode){
        if(varifyPurchaseResp(purchaseId)){ //验证ccapi请求
            Player player = GameDb.getPlayer(pid);
            ddd_purchase pur = allddd_purchase.get(purchaseId);
            if(pur == null){
                //todo 这种是异常的情况，city服务器没有订单，但是ccap服务器还有，这种情况需要人工处理
            }
            long eee = (long)GameDb.calGameCurrencyFromDDD(pur.ddd);
            player.addMoney(eee);
        }
    }

}
