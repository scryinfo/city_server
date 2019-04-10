package Game.Contract;

import Game.Building;
import Game.City;
import Shared.Util;
import gs.Gs;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.UUID;

@Entity
public class Contract
{
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID signId;
    @Column(nullable = false)
    private UUID sellerId;
    @Column(nullable = false)
    private UUID sellerBuildingId;

    @Column(nullable = false)
    private long startTs;

    @Column(nullable = false)
    private int signingHours;

    @Column(nullable = false)
    private long price;


    public Contract(UUID signId, UUID sellerId, UUID sellerBuildingId, long startTs, int signingHours,long price)
    {
        this.id = UUID.randomUUID();
        this.signId = signId;
        this.sellerId = sellerId;
        this.sellerBuildingId = sellerBuildingId;
        this.startTs = startTs;
        this.signingHours = signingHours;
        this.price = price;
    }

    public long getEndTs()
    {
        return startTs + signingHours * 60 * 60 * 1000;
    }
    public Contract()
    {
    }

    public UUID getId()
    {
        return id;
    }

    public UUID getSignId()
    {
        return signId;
    }

    public UUID getSellerId()
    {
        return sellerId;
    }

    public UUID getSellerBuildingId()
    {
        return sellerBuildingId;
    }

    public long getStartTs()
    {
        return startTs;
    }

    public long getConsumeTs()
    {
        return System.currentTimeMillis() - startTs;
    }

    public float getLift()
    {
        Building building = City.instance().getBuilding(sellerBuildingId);
        if (building != null)
        {
            return building.getLift();
        }
        return 0;
    }

    public int getSigningHours()
    {
        return signingHours;
    }

    public Gs.Contract toProto()
    {
        Gs.Contract.Builder builder = Gs.Contract.newBuilder();
        builder.setContractId(Util.toByteString(id))
                .setSignId(Util.toByteString(signId))
                .setStartTs(startTs)
                .setPassTs(getConsumeTs())
                .setHours(signingHours)
                .setLift(getLift())
                .setBuildingId(Util.toByteString(sellerBuildingId))
                .setSellerId(Util.toByteString(sellerId))
                .setPrice(price);
        return builder.build();
    }

    public boolean isSelfSign()
    {
        return signId.equals(sellerId);
    }

    public boolean isOutOfDate()
    {
        return System.currentTimeMillis() >= getEndTs();
    }

    public long getCost()
    {
        return price * signingHours;
    }
}
