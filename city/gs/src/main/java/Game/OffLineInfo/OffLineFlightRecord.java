package Game.OffLineInfo;

import gs.Gs;

import java.util.UUID;

/*Encapsulate data during offline*/
public class OffLineFlightRecord {
    public UUID playerId;
    public boolean win;
    public int profitOrLoss;//Profit and loss
    public Gs.FlightData data;//flight information

    public OffLineFlightRecord(UUID playerId, boolean win, int profitOrLoss, Gs.FlightData data) {
        this.playerId=playerId;
        this.win = win;
        this.profitOrLoss = profitOrLoss;
        this.data = data;
    }
}
