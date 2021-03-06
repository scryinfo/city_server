package Game;

import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Id;

import Shared.LogDb;
import Shared.Package;
import gs.Gs;
import gscode.GsCode;

@Entity
public class MoneyPool {
    public static UUID AccountId = UUID.fromString("e66a58ee-8b06-4b09-a9fe-6437e93a2b6d");
    public static final int ID = 0;
    private static MoneyPool instance;
    public static MoneyPool instance() {
        return instance;
    }
    public static void init() {
        GameDb.initMoneyPool();
        instance = GameDb.getMoneyPool();
    }
    protected MoneyPool() {}
    @Id
    public final int id = ID;
    private long n;

    public void add(long n) {
        this.n += n;
        //City bonus pool breakthrough, bonus pool reaches 1000, broadcast to the front end, including bonus pool amount, time
        if(n>=10000000){
        	GameServer.sendToAll(Package.create(GsCode.OpCode.cityBroadcast_VALUE,Gs.CityBroadcast.newBuilder()
        			.setType(4)
        			.setCost(n)
                    .setTs(System.currentTimeMillis())
                    .build()));
        	LogDb.cityBroadcast(null,null,n,0,4);
        }
    }
    public long money() {
        return n;
    }
    public boolean dec(long n) {
        if(this.n < n)
            return false;
        this.n -= n;
        return true;
    }

    public Gs.MoneyPool toProto() {
        return Gs.MoneyPool.newBuilder().setMoney(this.n).build();
    }

    public long getN() {
        return n;
    }
}
