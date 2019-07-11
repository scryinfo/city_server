package Game.OffLineInfo;

import java.util.UUID;

public class IncomeRecord {

    public UUID bid;
    public UUID ownerId;
    public int n;
    public int price;

    public IncomeRecord(UUID bid, UUID ownerId, int n, int price) {
        this.bid = bid;
        this.ownerId = ownerId;
        this.n = n;
        this.price = price;
    }

    public IncomeRecord() {
    }
}
