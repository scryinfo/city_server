package Game;

import Shared.Util;
import gs.Gs;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.io.Serializable;
import java.util.HashSet;
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
    @Column(nullable = false)
    private GroundStatus status = GroundStatus.STATELESS;
    public GroundInfo(int x, int y) {
        this.x = x;
        this.y = y;
    }

    protected GroundInfo() {}
    public boolean sameAs(RentPara rentPara) {
        return rentPara.rentDaysMin == rentDaysMin && rentPara.rentDaysMax == rentDaysMax && rentPara.rentPreDay == rentPreDay;
    }

    public void rented(RentPara rentPara, UUID tid, UUID renterId, long now) {
        this.rentTransactionId = tid;
        this.renterId = renterId;
        this.rentBeginTs = now;
        this.payTs = this.rentBeginTs;
        this.rentDays = rentPara.rentDays;
        this.status = GroundStatus.RENTED;
    }
    public boolean inRenting() {
        return status == GroundStatus.RENTING;
    }
    public boolean isRented() {
        return status == GroundStatus.RENTED;
    }
    public boolean inSelling() {
        return status == GroundStatus.SELLING;
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
            giBuilder.setRentDaysMin(rentDaysMin);
            giBuilder.setRentDaysMax(rentDaysMax);
            giBuilder.setRentPreDay(rentPreDay);
            builder.setRent(giBuilder);
        }
        else if (isRented())
        {
            Gs.GroundInfo.Rent.Builder giBuilder = Gs.GroundInfo.Rent.newBuilder();
            giBuilder.setRenterId(Util.toByteString(renterId));
            giBuilder.setRentDays(rentDays);
            giBuilder.setRentBeginTs(rentBeginTs);
            giBuilder.setRentDaysMin(rentDaysMin);
            giBuilder.setRentDaysMax(rentDaysMax);
            giBuilder.setRentPreDay(rentPreDay);
            builder.setRent(giBuilder);
        }
        else if (inSelling())
        {
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
        status = GroundStatus.STATELESS;
    }

    public boolean canSell(UUID id) {
        return ownerId.equals(id) &&
                (status == GroundStatus.STATELESS
                        || status == GroundStatus.SELLING);
    }

    public boolean canRenting(UUID id)
    {
        return this.ownerId.equals(id) &&
                (this.status == GroundStatus.RENTING
                        || this.status == GroundStatus.STATELESS);
    }

    public void sell(int price)
    {
        this.sellPrice = price;
        this.status = GroundStatus.SELLING;
    }

    public void cancelSell()
    {
        this.sellPrice = 0;
        this.status = GroundStatus.STATELESS;
    }

    public void buyGround(UUID id)
    {
        GroundManager.instance().playerGround.computeIfPresent(ownerId,(k,v)->{
            v.remove(this);
            return v;
        });
        this.ownerId = id;
        this.sellPrice = 0;
        this.status = GroundStatus.STATELESS;
        GroundManager.instance().playerGround.computeIfAbsent(ownerId, k->new HashSet<>()).add(this);
    }

    public void renting(RentPara rentPara)
    {
        this.rentPreDay = rentPara.rentPreDay;
        this.rentDaysMin = rentPara.rentDaysMin;
        this.rentDaysMax = rentPara.rentDaysMax;
        this.status = GroundStatus.RENTING;
    }



    enum GroundStatus
    {
        STATELESS,RENTING,RENTED,SELLING
    }
}