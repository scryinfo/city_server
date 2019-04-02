package Game.Contract;


import gs.Gs;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.util.UUID;

@Embeddable
public class BuildingContract
{
    private long price = 0;
    private int durationHour = 0;
    private boolean isOpen;

    @Column(nullable = true)
    private UUID contractId;

    public Gs.ContractInfo toProto()
    {
        Gs.ContractInfo.Builder builder = Gs.ContractInfo.newBuilder();
        if (isOpen)
        {
            builder.setIsOpen(true)
                    .setPrice(price)
                    .setHours(durationHour);
            if (contractId != null)
            {
                Contract contract = ContractManager.getInstance().getContractById(contractId);
                builder.setContract(contract.toProto());
            }
        }
        else
        {
            builder.setIsOpen(false);
        }
        return builder.build();
    }

    public BuildingContract()
    {
    }

    public BuildingContract(long price, int durationHour, boolean isOpen)
    {
        this.price = price;
        this.durationHour = durationHour;
        this.isOpen = isOpen;
    }



    public void closeContract()
    {
        this.price = 0;
        this.durationHour = 0;
        this.contractId = null;
        isOpen = false;
    }

    public void openOrSetContract(long price,int hours)
    {
        this.price = price;
        this.durationHour = hours;
        isOpen = true;
    }



    public long getCost()
    {
        return price * durationHour;
    }

    public long getPrice()
    {
        return price;
    }

    public int getDurationHour()
    {
        return durationHour;
    }

    public boolean isOpen()
    {
        return isOpen;
    }

    public boolean isSign()
    {
        return contractId != null;
    }

    public UUID getContractId()
    {
        return contractId;
    }

    public void sign(UUID contractId)
    {
        this.contractId = contractId;
    }
}
