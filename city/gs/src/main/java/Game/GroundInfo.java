package Game;

import Shared.Util;
import gs.Gs;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
public class GroundInfo implements Serializable {
    public static final int OWN = 0x00000001;
    public static final int RENTING = 0x00000002;
    public static final int RENTED = 0x00000004;
    public static final int SELLING = 0x00000008;
    public static final int CAN_BUILD = OWN | RENTING;
    @Id
    @Column(name = "x", nullable = false)
    public int x;
    @Id
    @Column(name = "y", nullable = false)
    public int y;

    int rentPreDay;
    int rentDaysMin;
    int rentDaysMax;
    int rentDays;
    UUID renterId;
    UUID rentTransactionId;
    @Column(nullable = false)
    UUID ownerId;
    int sellPrice;
    long rentBeginTs;
    long payTs;
    public GroundInfo(int x, int y) {
        this.x = x;
        this.y = y;
    }

    protected GroundInfo() {}
    public boolean sameAs(RentPara rentPara) {
        return rentPara.rentDaysMin == rentDaysMin && rentPara.rentDaysMax == rentDaysMax && rentPara.rentPreDay == rentPreDay;
    }
    public void setBy(RentPara rentPara) {
        this.rentDays = rentPara.rentDays;
        this.rentPreDay = rentPara.rentPreDay;
        this.rentDaysMin = rentPara.rentDaysMin;
        this.rentDaysMax = rentPara.rentDaysMax;
    }
    public void rentOut(RentPara rentPara, UUID tid, UUID renterId, long now) {
        this.setBy(rentPara);
        this.rentTransactionId = tid;
        this.renterId = renterId;
        this.rentBeginTs = now;
        this.payTs = this.rentBeginTs;
        this.rentDays = rentPara.rentDays;
    }
    public boolean inRenting() {
        return this.rentDays > 0;
    }
    public boolean isRentOut() {
        return this.rentTransactionId != null;
    }
    public boolean inSelling() {
        return sellPrice > 0;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GroundInfo coordinate = (GroundInfo) o;
        return x == coordinate.x &&
                y == coordinate.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    public Gs.GroundInfo toProto() {
        Gs.GroundInfo.Builder builder = Gs.GroundInfo.newBuilder();
        builder.setOwnerId(Util.toByteString(ownerId))
                .setX(x)
                .setY(y);
        if(inRenting()) {
            Gs.GroundInfo.Rent.Builder giBuilder = Gs.GroundInfo.Rent.newBuilder();
            if(isRentOut()) {
                giBuilder.setRenterId(Util.toByteString(renterId));
                giBuilder.setRentDays(rentDays);
                giBuilder.setRentBeginTs(rentBeginTs);
            }
            giBuilder.setRentDaysMin(rentDaysMin);
            giBuilder.setRentDaysMax(rentDaysMax);
            giBuilder.setRentPreDay(rentPreDay);
            builder.setRent(giBuilder);
        }
        else if(inSelling()) {
            builder.setSell(Gs.GroundInfo.Sell.newBuilder()
                .setPrice(sellPrice)
            );
        }
        return builder.build();
    }

    public void endRent() {
        rentBeginTs = 0;
        renterId = null;
        rentTransactionId = null;
        payTs = 0;
        rentDaysMax = 0;
        rentDaysMin = 0;
        rentDays = 0;
    }

    public boolean canSell(UUID id) {
        return ownerId.equals(id) && !inRent();
    }
    public boolean inRent() {
        return renterId != null;
    }
    public boolean inSell() {
        return !inRent() && sellPrice > 0;
    }

    public void cancelRent() {
        assert renterId == null;
        rentBeginTs = 0;
        rentTransactionId = null;
        payTs = 0;
        rentDaysMax = 0;
        rentDaysMin = 0;
        rentDays = 0;
    }
}