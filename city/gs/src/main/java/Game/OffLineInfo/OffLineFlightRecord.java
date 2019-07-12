package Game.OffLineInfo;

import gs.Gs;

import java.util.UUID;

/*封装离线期间的数据*/
public class OffLineFlightRecord {
    public UUID playerId;
    public boolean win;
    public int profitOrLoss;//盈亏
    public Gs.FlightData data;//航班信息

    public OffLineFlightRecord(UUID playerId, boolean win, int profitOrLoss, Gs.FlightData data) {
        this.playerId=playerId;
        this.win = win;
        this.profitOrLoss = profitOrLoss;
        this.data = data;
    }
}
