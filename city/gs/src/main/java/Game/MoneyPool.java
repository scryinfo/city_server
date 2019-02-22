package Game;

import gs.Gs;
import gscode.GsCode;

import javax.persistence.Entity;
import javax.persistence.Id;

import Shared.Package;

import java.util.UUID;

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
        //城市奖金池突破,奖金池达到1000,发送广播给前端,包括奖金池金额，时间 
        if(n>=1000){
        	GameSession gs = GameServer.allGameSessions.get(id);
        	gs.write(Package.create(GsCode.OpCode.getNumBreakThrough_VALUE,Gs.CityBroadcast.newBuilder()
        			.setType(4)
        			.setCost(n)
                    .setTs(System.currentTimeMillis())
                    .build()));
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
}
