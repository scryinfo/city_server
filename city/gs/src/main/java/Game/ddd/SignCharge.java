package Game.ddd;

import org.apache.commons.codec.digest.DigestUtils;
import org.spongycastle.util.encoders.Hex;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;


public class SignCharge {
    public SignCharge(String purchaseId, long ts, String metaData /*,  byte[]  pubkey*/){
        PurchaseId = purchaseId;
        Ts = ts;
        MetaData = metaData;
        //PubKey = pubkey;
    }
    String PurchaseId;
    long Ts;
    String Addr;         // Eee or ddd address
    String MetaData;     // 附加信息，可以不写
    //byte[] PubKey;        // 公钥

    public byte[] ToHash() throws IOException {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        byte[] purchaseId = Hex.decode(PurchaseId.getBytes());
        byte[] ts = ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(Ts).array();
        //byte[] metaData = Hex.decode(MetaData.getBytes());
        byte[] metaData = MetaData.getBytes();

        all.write(purchaseId);
        all.write(ts);
        all.write(metaData);
        //all.write(PubKey);

        all.flush();
        byte[] dd = DigestUtils.sha256(all.toByteArray());
        return dd;
    }
}