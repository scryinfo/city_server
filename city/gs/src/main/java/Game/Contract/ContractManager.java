package Game.Contract;

import Game.Building;
import Game.City;
import Game.GameDb;
import Game.Player;
import Game.Timers.PeriodicTimer;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class ContractManager
{
    private ContractManager() { }
    private static ContractManager instance = new ContractManager();
    private Map<UUID, Contract> allContract = new HashMap<>();  //UUID: Contract.id
    private Map<UUID, Float> playerLiftMap = new HashMap<>();   //UUID: signId

    private PeriodicTimer timer = new PeriodicTimer((int) TimeUnit.SECONDS.toMillis(1));

    public static ContractManager getInstance()
    {
        return instance;
    }

    public void init()
    {
        GameDb.getAllContract().forEach(contract ->
                {
                    if (isOutOfDate(contract))
                    {
                        deleteContract(contract);
                    }
                    else
                    {
                        allContract.put(contract.getId(), contract);
                    }
                });
        updatePlayerLiftMap();
    }

    //检查失效契约
    public void update(long diffNano)
    {
        if (timer.update(diffNano))
        {
            List<Contract> deletes = new ArrayList<>();
            allContract.values().forEach(contract -> {
                if (isOutOfDate(contract)) {
                    deletes.add(contract);
                }
            });
            deletes.forEach(this::deleteContract);
        }
    }

    public boolean isOutOfDate(Contract contract)
    {
        return !contract.isSelfSign() && contract.isOutOfDate();
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
        List<Object> updates = new ArrayList<>();
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
            Player seller = GameDb.getPlayer(building1.ownerId());
            player.decMoney(contract.getCost());
            seller.addMoney(contract.getCost());
            updates.add(player);
            updates.add(seller);
        }
        updates.add(contract);
        updates.add(building);
        building.getBuildingContract().sign(contract.getId());

        GameDb.saveOrUpdate(updates);

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

    //获取玩家人流量推广能力，20.5  --> 20.5%
    public float getPlayerADLift(UUID playerId)
    {
        return playerLiftMap.getOrDefault(playerId, 0f);
    }
}
