package Game.ddd;

import org.apache.commons.codec.digest.DigestUtils;
import org.spongycastle.util.encoders.Hex;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ActiveSing {
    public ActiveSing(String purchaseId, String ethAddr, long ts, String amount,  byte[]  pubkey){
        PurchaseId = purchaseId;
        EthAddr = ethAddr;
        Ts = ts;
        Amount = amount;
    }
	
    String PurchaseId;
    long Ts;
    String EthAddr;         // Eee or ddd address
    String Amount;     // 附加信息，可以不写

    public String formatAmount(String count)
    {
        if (count.length() % 2 == 1) count = "0" + count;
        return count;
    }

    public byte[] ToHash() throws IOException {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        byte[] purchaseId = Hex.decode(PurchaseId.getBytes());
        byte[] addr =DigestUtils.sha256((EthAddr.getBytes()));
        byte[] ts = ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(Ts).array();
        byte[] amount = Hex.decode(formatAmount(Amount).getBytes());

        all.write(purchaseId);
        all.write(amount);
        all.write(ts);
        all.write(addr);

        all.flush();
        byte[] dd = DigestUtils.sha256(all.toByteArray());
        return dd;
    }
}