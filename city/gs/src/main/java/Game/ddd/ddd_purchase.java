package Game.ddd;

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
    PROCESSING, PROCESSED, NONE;
}

@Entity
public class ddd_purchase {
    //关键数据----------------------------
    @Id
    //@GeneratedValue(strategy = GenerationType.AUTO)
    UUID purchaseId;   //订单id city 提供

    UUID player_id;      //玩家 uuid
    //关键数据----------------------------

    double ddd;	 		//ddd ，充值为正，提币为负
    String ddd_from;    //
    String ddd_to;	    //ddd 充值到的用户地址
    String ddd_tx_id;	//todo add
    String ddd_tx_info;	//
    int ddd_date;
    StatusPurchase status;         //交易状态
    long expire_time;
    long create_time;
    int type;

    //关键数据签名验证----------------------------
    String req_pubkey;     	//请求方公钥
    byte[] req_Signature;	//请求方签名

    String resp_pubkey;    	//响应服务器公钥
    byte[] resp_Signature;	//响应方方签名
    //关键数据签名验证----------------------------

    public ddd_purchase(UUID inPurchaseId, UUID playerid, double dddCount, String addsFrom, String addsTo){
        purchaseId = inPurchaseId;
        player_id = playerid;
        ddd = dddCount;
        ddd_from = addsFrom;
        ddd_to = addsTo;
        create_time =  System.currentTimeMillis();
        status = StatusPurchase.PROCESSED;
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
                , create_time
                , Double.toString(ddd)
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
            boolean pass =  newpubkey.verify(hSignCharge ,newsig); //验证通过
            return pass;
        }catch (Exception e){
            return false;
        }
    }
}
