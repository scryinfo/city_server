package Game;

import gs.Gs;

import javax.persistence.Entity;
import javax.persistence.Id;
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
