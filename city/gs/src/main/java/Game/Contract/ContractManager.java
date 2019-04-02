package Game.Contract;

import Game.Building;
import Game.City;
import Game.GameDb;
import Game.Player;

import java.util.*;

public class ContractManager
{
    private ContractManager() { }
    private static ContractManager instance = new ContractManager();
    private Map<UUID, Contract> allContract = new HashMap<>();

    public static ContractManager getInstance()
    {
        return instance;
    }

    public void init()
    {
        GameDb.getAllContract().forEach(contract ->
                {
                    allContract.put(contract.getId(), contract);
                    clearByOutOfDate(contract);
                });
    }

    public void update()
    {
        allContract.values().forEach(this::clearByOutOfDate);
    }

    public void clearByOutOfDate(Contract contract)
    {
        if (!contract.isSelfSign() && contract.isOutOfDate())
        {
            this.deleteContract(contract);
        }
    }

    public boolean deleteContract(Contract contract)
    {
        return this.deleteContract(contract, null);
    }

    public boolean deleteContract(Contract contract,IBuildingContract building)
    {
        if (building == null)
        {
            building = (IBuildingContract) City.instance().getBuilding(contract.getSellerBuildingId());
        }
        if (contract.getSellerBuildingId().equals(((Building)building).id()))
        {
            allContract.remove(contract.getId());
            building.getBuildingContract().closeContract();
            /**
             * TODO:
             * 2019/3/29
             * 契约失效影响
             */
            GameDb.saveOrUpdateAndDelete(Collections.singleton(building), Collections.singleton(contract));
            return true;
        }
        return false;
    }

    public Contract signContract(Player player, IBuildingContract building)
    {
        Building building1 = (Building) building;
        Contract contract = null;
        if (player.id().equals((building1.ownerId())))
        {
            contract = new Contract(player.id(), building1.ownerId(), building1.id(),
                    System.currentTimeMillis(), 0, 0);
        }
        else
        {
            contract = new Contract(player.id(), building1.ownerId(), building1.id(), System.currentTimeMillis(),
                    building.getBuildingContract().getDurationHour(),
                    building.getBuildingContract().getPrice());
        }
        building.getBuildingContract().sign(contract.getId());
        /**
         * TODO:
         * 2019/4/1
         * 契约生效影响
         */


        GameDb.saveOrUpdate(Arrays.asList(building,contract));
        allContract.put(contract.getId(), contract);
        return contract;
    }

    public Contract getContractById(UUID id)
    {
        return allContract.get(id);
    }

    public List<Contract> getContractsBySignId(UUID signId)
    {
        return Arrays.asList(allContract.values()
                .stream()
                .filter(contract -> contract.getSignId().equals(signId))
                .toArray(Contract[]::new));
    }
    
}
