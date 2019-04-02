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
    private Map<UUID, Float> playerLiftMap = new HashMap<>();

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
        updatePlayerLiftMap();
    }

    //按服务器帧率检查失效契约
    public void update(long diffNano)
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

            building.getBuildingContract().closeContract();
            GameDb.saveOrUpdateAndDelete(Collections.singleton(building), Collections.singleton(contract));

            allContract.remove(contract.getId());
            updatePlayerLiftMapById(contract.getSignId(), -contract.getLift());
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
        GameDb.saveOrUpdate(Arrays.asList(building,contract));

        allContract.put(contract.getId(), contract);
        updatePlayerLiftMapById(contract.getSignId(), contract.getLift());
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

    private void updatePlayerLiftMap()
    {
        playerLiftMap.clear();
        allContract.values().forEach(contract -> {
            playerLiftMap.put(contract.getSignId(),
                    playerLiftMap.getOrDefault(contract.getSignId(), 0f) + contract.getLift());
        });
    }

    private void updatePlayerLiftMapById(UUID signId,float value)
    {
        playerLiftMap.put(signId, playerLiftMap.getOrDefault(signId, 0f) + value);
    }

    //按小时重置加成值
    public void hourTickAction(int nowHour)
    {
        updatePlayerLiftMap();
    }

    public float getPlayerADLift(UUID playerId)
    {
        return playerLiftMap.getOrDefault(playerId, 0f);
    }
}
