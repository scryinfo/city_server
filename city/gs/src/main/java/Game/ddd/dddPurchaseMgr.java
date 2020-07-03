package Game.ddd;

import Game.GameDb;
import Game.Mail;
import Game.MailBox;
import Game.Player;
import Shared.Package;
import Shared.Util;
import ccapi.CcOuterClass.DisChargeRes;
import ccapi.CcOuterClass.RechargeRequestRes;
import ccapi.Dddbind.ct_DisChargeRes;
import ccapi.Dddbind.ct_RechargeRequestRes;
import ccapi.GlobalDef;
import cityapi.CityOuterClass.RechargeResultReq;
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

    public ddd_purchase getPurchase(UUID purId){
        return allddd_purchase.get(purId);
    }
    //Add order
    public boolean addPurchase(ddd_purchase purchase){
        //If there is enough eee
        purchase.status = StatusPurchase.PROCESSING;
        Player player = GameDb.getPlayer(purchase.player_id);
        if(purchase.ddd < 0){
            //Determine if the transaction is legal
            long eee = (long)GameDb.calGameCurrencyFromDDD(purchase.ddd);
            if(player.money() + eee*10000 < 0 ){ //Insufficient game currency
                return false;
            }
            //Withdraw operation, need to lock eee
            player.lockMoney(purchase.purchaseId,-eee*10000);
        }

        //Do not allow multiple transactions with the same id
        if(!allddd_purchase.containsKey(purchase.purchaseId)){
            allddd_purchase.put(purchase.purchaseId,purchase);
            //Persistence
            if(purchase.ddd < 0){
                GameDb.saveOrUpdate(player);
            }
            GameDb.saveOrUpdate(this);
        }
        return  true;
    }

    //Remove the order and process the overdue order regularly later
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

    //Recharge request verification, normal city server forwarding is required, because the request will be verified on the ccapi server, so no city verification is required
    //The advantage of city server verification is to prevent malicious billing
    public boolean varifyPurchaseReq( UUID purchaseId ){
        ddd_purchase pur = allddd_purchase.get(purchaseId);
        if(pur != null ){
            return pur.verifyReq();
        }
        return false;
    }

    public boolean varifyPurchaseResp( UUID purchaseId ){
        //There is no verification of resp yet, so it returns true directly
        return true;
        //After having resp verification, cancel the following logout
        /*ddd_purchase pur = allddd_purchase.get(purchaseId);
        if(pur != null ){
            return pur.verifyResp();
        }
        return false;*/
    }

    //This is the callback after the successful recharge request
    public void on_RechargeReqResp(RechargeResultReq ccapiReq){
        UUID purId = Util.toUuid(ccapiReq.getCityPurchaseId().getBytes());
        ddd_purchase pur = allddd_purchase.get(purId);
        if(varifyPurchaseResp(purId)){ //Verify ccapi request
            Player player = GameDb.getPlayer(pur.player_id);
            if(pur == null){
                //todo This is an abnormal situation. The city server has no orders, but the ccap server still has it. This situation requires manual processing.
            }

            if(pur.ddd < 0){
                player.spentLockMoney(purId);
            }else{
                long eee = 0;
                eee = (long)GameDb.calGameCurrencyFromDDD(pur.ddd);
                player.addMoney(eee * 10000);
            }
            pur.status = StatusPurchase.PROCESSED;
            pur.completion_time = System.currentTimeMillis();
            if(ccapiReq.getSignature().size() > 0)
                pur.resp_Signature = ccapiReq.getSignature().toByteArray();
            GameDb.saveOrUpdate(player);
            GameDb.saveOrUpdate(pur);

            if(pur.ddd > 0){//Deposit successfully
                ct_RechargeRequestRes.Builder msg = ct_RechargeRequestRes.newBuilder();
                GlobalDef.ResHeader.Builder header = GlobalDef.ResHeader.newBuilder();
                header.setErrCode(ERR_SUCCESS).setReqId(ccapiReq.getReqHeader().getReqId())
                .setVersion(ccapiReq.getReqHeader().getVersion());
                RechargeRequestRes.Builder pRechargeRequestRes = RechargeRequestRes.newBuilder();

                pRechargeRequestRes
                        .setResHeader(header)
                        .setPurchaseId(pur.purchaseId.toString())
                        .setEthAddr(pur.ddd_from)
                        .setTs(pur.create_time)
                        .setExpireTime(pur.expire_time)
                        .setSignature(ByteString.copyFrom(pur.resp_Signature));

                msg.setPlayerId(Util.toByteString(pur.player_id)).setRechargeRequestRes(pRechargeRequestRes.build());
                Package pack = Package.create(GsCode.OpCode.ct_RechargeRequestRes_VALUE, msg.build());
                player.send(pack);
                MailBox.instance().sendMail(Mail.MailType.DDD_RECHARGEREQUESTRES.getMailType(), pur.player_id, null, null);
            }else{//Withdraw
                ct_DisChargeRes.Builder msg = ct_DisChargeRes.newBuilder();
                DisChargeRes.Builder disC = DisChargeRes.newBuilder();
                disC.setAmount(String.valueOf(pur.ddd))
                    .setPurchaseId( pur.purchaseId.toString())
                    .setResHeader(GlobalDef.ResHeader.newBuilder().setErrCode(ERR_SUCCESS).setReqId(ccapiReq.getReqHeader().getReqId()).setVersion(ccapiReq.getReqHeader().getVersion()));
                msg.setPlayerId(Util.toByteString(pur.player_id)).setDisChargeRes(disC.build());
                Package pack = Package.create(GsCode.OpCode.ct_DisChargeRes_VALUE, msg.build());
                player.send(pack);
                MailBox.instance().sendMail(Mail.MailType.DDD_DISCHARGERES.getMailType(), pur.player_id, null, null);
            }
        }
    }

    //This is the callback after the recharge is successful
    public void on_RechargeSuccess(UUID pid, UUID purchaseId, int ErrCode){
        if(varifyPurchaseResp(purchaseId)){ //Verify ccapi request
            Player player = GameDb.getPlayer(pid);
            ddd_purchase pur = allddd_purchase.get(purchaseId);
            if(pur == null){
                //todo This is an abnormal situation. The city server has no orders, but the ccap server still has it. This situation requires manual processing.
            }
            long eee = (long)GameDb.calGameCurrencyFromDDD(pur.ddd);
            player.addMoney(eee);
            GameDb.saveOrUpdate(player);
        }
    }

    public void on_dddMsg(RechargeResultReq ccapiReq){
        on_RechargeReqResp(ccapiReq);
    }

}
