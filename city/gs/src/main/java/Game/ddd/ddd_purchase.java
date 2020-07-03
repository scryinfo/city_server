package Game.ddd;

import Shared.Util;
import org.ethereum.crypto.ECKey;
import org.spongycastle.util.encoders.Hex;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.UUID;

//import java.security.interfaces.ECKey;

enum StatusPurchase
{
    PROCESSING, PROCESSED, FAILED, NONE;
}

@Entity
public class ddd_purchase {
    ddd_purchase(){}
    //Key data----------------------------
    @Id
    //@GeneratedValue(strategy = GenerationType.AUTO)
    public UUID purchaseId;   //Order id city offers

    public UUID player_id;      //Player uuid
    //Key data----------------------------

    double ddd;	 		//ddd, recharge value is positive, withdrawal is negative
    public String ddd_from;    //
    public String ddd_to;	    //ddd Recharge user address
    String ddd_tx_id;	//todo add
    String ddd_tx_info;	//
    public int ddd_date;
    StatusPurchase status;         //trading status

    public long getExpire_time() {
        return expire_time;
    }

    long expire_time;

    public long getCreate_time() {
        return create_time;
    }

    long create_time;
    long completion_time;
    int type;

    //Key data signature verification----------------------------
    String req_pubkey;     	//Requester public key

    public byte[] getReq_Signature() {
        return req_Signature;
    }

    byte[] req_Signature;	//Requester's signature

    String resp_pubkey;    	//Response server public key

    public byte[] getResp_Signature() {
        return resp_Signature;
    }

    public void setResp_Signature(byte[] resp_Signature) {
        this.resp_Signature = resp_Signature;
    }

    byte[] resp_Signature;	//Responder's signature
    //Key data signature verification----------------------------

    public ddd_purchase(UUID inPurchaseId, UUID playerid, double dddCount, String addsFrom, String addsTo){
        purchaseId = inPurchaseId;
        player_id = playerid;
        ddd = dddCount;
        ddd_from = addsFrom;
        ddd_to = addsTo;
        create_time =  System.currentTimeMillis();
        status = StatusPurchase.PROCESSED;
    }

    public ccapi.Dddbind.ct_TradingRecord toProto() {
        return ccapi.Dddbind.ct_TradingRecord.newBuilder()
                .setPurchaseId(Util.toByteString(purchaseId))
                .setDdd(Double.toString(ddd))
                .setDddFrom(ddd_from)
                .setDddTo(ddd_to)
                .setStatus(status.ordinal())
                .setExpireTime(expire_time)
                .setCreateTime(create_time)
                .setCompletionTime(completion_time)
                .build();
    }
    public void bindReqSignature( String reqPbk, byte[] reqSig){
        req_pubkey = reqPbk;
        req_Signature = reqSig;
    }

    public void bindRespSignature( String respPbk, byte[] respSig){
        resp_pubkey= respPbk;
        resp_Signature = respSig;
    }

    public boolean verifyReq(){
        return  verify_internal(req_pubkey,req_Signature);
    }

    public boolean verifyResp(){
        return  verify_internal(resp_pubkey,resp_Signature);
    }

    private boolean verify_internal(String pubkey, byte[] Signature){
        byte[] pubKey = Hex.decode(pubkey);
        SignCharge pSignCharge = new SignCharge(
                purchaseId.toString()
                , Double.toString(ddd)
                , create_time
                //, pubKey
        );
        try {
            byte[] hSignCharge = pSignCharge.ToHash();
            byte[] sigbts = Hex.decode(Signature);
            ECKey.ECDSASignature newsig = new ECKey.ECDSASignature(
                    new BigInteger(1,Arrays.copyOfRange(sigbts, 0, 32)),
                    new BigInteger(1,Arrays.copyOfRange(sigbts, 32, 64))
            );
            ECKey newpubkey = ECKey.fromPublicOnly(pubKey);
            boolean pass =  newpubkey.verify(hSignCharge ,newsig); //Verified
            return pass;
        }catch (Exception e){
            return false;
        }
    }
}
