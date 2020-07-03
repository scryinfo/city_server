package Game.ddd;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;


public class SignCharge {
    public SignCharge(String purchaseId, String metaData, long ts /*,  byte[]  pubkey*/){
        PurchaseId = purchaseId;
        Ts = ts;
        Amount = metaData;
        //PubKey = pubkey;
    }
    String PurchaseId;
    long Ts;
    String Addr;         // Eee or ddd address
    String Amount;     // Additional information, you can not write
    //byte[] PubKey;        // Public key

    public byte[] ToHash() throws IOException {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        byte[] purchaseId = PurchaseId.getBytes(StandardCharsets.UTF_8);
        byte[] ts = ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(Ts).array();
        byte[] metaData = Amount.getBytes(StandardCharsets.UTF_8);
        //byte[] addr = Addr.getBytes(StandardCharsets.UTF_8);

        all.write(purchaseId);  //purchaseId
        all.write(metaData);    //amount
        all.write(ts);          //ts
        //all.write(addr);

        all.flush();
        byte[] dd = DigestUtils.sha256(all.toByteArray());
        return dd;
    }
}