package Game.League;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import Game.Building;
import Game.City;
import Game.Eva.EvaManager;
import Game.GameDb;
import Game.Player;
import Game.Eva.Eva;
import Game.Meta.MetaData;
import Game.Timers.PeriodicTimer;
import Shared.LogDb;
import Shared.Util;
import gs.Gs;

public class LeagueManager
{
    private LeagueManager()
    {
    }
    private static LeagueManager instance = new LeagueManager();
    public static LeagueManager getInstance()
    {
        return instance;
    }

    //任何对leagueInfoMap作出的改变，必要信息一定要同步到buildingLeagueInfo中
    private Map<LeagueInfo.UID, LeagueInfo> leagueInfoMap = new HashMap<>();//存的是加盟玩家建筑所加盟的所有信息，k是加盟建筑的id，key是该建筑加盟的所有的加盟信息uid
    //key = buildingId
    private Map<UUID, Set<LeagueInfo.UID>> buildingLeagueInfo = new HashMap<>();
    private Map<UUID, Set<BrandLeague>> brandLeagueMap = new HashMap<UUID, Set<BrandLeague>>();
    private PeriodicTimer timer = new PeriodicTimer((int) TimeUnit.SECONDS.toMillis(1));

    public void init()
    {
        GameDb.getAllFromOneEntity(LeagueInfo.class).forEach(
                leagueInfo ->{
                    leagueInfoMap.put(leagueInfo.getUid(), leagueInfo);
                    leagueInfo.memberForEach(member -> {
                        buildingLeagueInfo.computeIfAbsent(member.getBuildingId(),
                                k -> new HashSet<>()).add(leagueInfo.getUid());
                    });
                } );
        GameDb.getAllFromOneEntity(BrandLeague.class).forEach(
        		bl ->{
        			brandLeagueMap.computeIfAbsent(bl.getBuildingId(),
        					k -> new HashSet<>()).add(bl);
        		} );
    }

    public void update(long diffNano)
    {
        if (timer.update(diffNano))
        {
            List<LeagueInfo> updateList = new ArrayList<>();
            //过期检查
            leagueInfoMap.values().forEach(leagueInfo ->
            {
                List<LeagueInfo.Member> removes = leagueInfo.memberOverdueAndRemove();
                if (!removes.isEmpty())
                {
                    updateList.add(leagueInfo);
                }
                removes.forEach(member -> {
                    buildingLeagueInfo.get(member.getBuildingId()).remove(leagueInfo.getUid());
                    
                    //删除建筑某项过期的加盟技术
        			delBrandLeague(member.getBuildingId(),leagueInfo.getUid().getTechId(),leagueInfo.getUid().getPlayerId());
                });
            });
            GameDb.Update(updateList);
        }
    }

    public Boolean settingLeagueInfo(UUID playerId, Gs.LeagueInfoSetting setting)
    {
        int techId = setting.getTechId();
        long price = setting.getSetting().getPrice();
        int minHours = setting.getSetting().getMinHours();
        int maxHours = setting.getSetting().getMaxHours();
        if (price >= 0 && minHours > 0 && minHours <= maxHours)
        {
            LeagueInfo.UID uid = new LeagueInfo.UID(playerId, techId);
            LeagueInfo leagueInfo = leagueInfoMap.get(uid);
            if (leagueInfo == null)
            {
                leagueInfo = new LeagueInfo(uid, true, price, maxHours, minHours);
                GameDb.saveOrUpdate(leagueInfo);
                leagueInfoMap.put(uid, leagueInfo);
            }
            else
            {
                leagueInfo.openSetting(price, maxHours, minHours);
                GameDb.saveOrUpdate(leagueInfo);
            }
            return true;
        }
        return false;
    }

    public boolean closeLeagueInfo(UUID playerId,int techId)
    {
        LeagueInfo.UID uid = new LeagueInfo.UID(playerId, techId);
        LeagueInfo leagueInfo = leagueInfoMap.get(uid);
        if (leagueInfo != null)
        {
            leagueInfo.closeSetting();
            GameDb.saveOrUpdate(leagueInfo);
            return true;
        }
        return false;
    }


    public Gs.LeagueInfo queryProtoLeagueInfo(UUID playerId, int techId)
    {
        Gs.LeagueInfo.Builder builder = Gs.LeagueInfo.newBuilder();
        builder.setTechId(techId);
        builder.addAllTechInfo(getAllTechInfo(playerId, techId));

        LeagueInfo.UID uid = new LeagueInfo.UID(playerId, techId);
        LeagueInfo leagueInfo = leagueInfoMap.get(uid);
        if (leagueInfo != null)
        {
            builder.setSetting(leagueInfo.toSettingProto());
            builder.addAllMember(leagueInfo.toJoinMemberProto());
        }
        else
        {
            builder.setSetting(Gs.LeagueSetting.newBuilder().setIsSettingOpen(false).build());
        }
        return builder.build();
    }
    
    public long queryProtoLeagueMemberLeaveTime(UUID playerId, int techId, UUID buildingId)
    {
    	LeagueInfo.UID uid = new LeagueInfo.UID(playerId, techId);
    	LeagueInfo leagueInfo = leagueInfoMap.get(uid);
    	LeagueInfo.Member member=leagueInfo.getMember(buildingId);
    	long leaveTime=member.leaveTime();
    	return leaveTime;
    }

    private List<Gs.LeagueInfo.TechInfo> getAllTechInfo(UUID playerId, int techId)
    {
        List<Eva> evaList = GameDb.getEvaInfo(playerId, techId);
        List<Gs.LeagueInfo.TechInfo> techInfoList = new ArrayList<>();
        Gs.LeagueInfo.TechInfo.Builder builder = Gs.LeagueInfo.TechInfo.newBuilder();
        evaList.forEach(eva -> {
            builder.clear();
            builder.setType(Gs.Eva.Btype.valueOf(eva.getBt()));
            if (eva.getBt() == Gs.Eva.Btype.Brand_VALUE)
            {
                builder.setValue(eva.getB());
            }
            else
            {
                builder.setValue(eva.getLv());
            }
            techInfoList.add(builder.build());
        });
        return techInfoList;
    }

    public List<Gs.LeagueInfo.TechInfo> getLeagueTechInfo(LeagueInfo leagueInfo)
    {
        return getAllTechInfo(leagueInfo.getUid().getPlayerId(), leagueInfo.getUid().getTechId());
    }


    public List<LeagueInfo> getOpenedLeagueInfoByTechId(int techId)
    {
        List<LeagueInfo> leagueInfos = new ArrayList<>();
        leagueInfoMap.forEach((k,v)->{
            if (k.isTech(techId) && v.isOpen())
            {
                leagueInfos.add(v);
            }
        });
        return leagueInfos;
    }

    public int getLeagueInfoOwnerBcount(LeagueInfo leagueInfo)
    {
        UUID ownerId = leagueInfo.getUid().getPlayerId();
        int techId = leagueInfo.getUid().getTechId();

        Set<Integer> buildingBTypes = MetaData.getBuildingTypeByTech(techId);
        return buildingBTypes.stream().mapToInt(k->City.instance().getPlayerBcountByBtype(ownerId,k)).sum();
    }

    public List<Building> getBuildingListByPlayerTech(UUID playerId, int techId)
    {
        List<Building> buildings = new ArrayList<>();
        LeagueInfo.UID uid = new LeagueInfo.UID(playerId, techId);
        LeagueInfo leagueInfo = leagueInfoMap.get(uid);
        if (leagueInfo != null) {
            leagueInfo.getMemberIds().forEach(id ->
            {
                Building b;
                if ((b = City.instance().getBuilding(id)) != null)
                {
                    buildings.add(b);
                }
            });
        }

        Set<Integer> buildingBTypes = MetaData.getBuildingTypeByTech(techId);
        buildings.addAll(
                buildingBTypes.stream()
                        .flatMap(k -> City.instance().getPlayerBListByBtype(playerId, k).stream())
                        .collect(Collectors.toList()));
        return buildings;
    }

    public boolean joinLeague(Player player, Gs.JoinLeague joinLeague)
    {
        Building building = City.instance().getBuilding(Util.toUuid(joinLeague.getBuildingId().toByteArray()));
        if (building == null || !building.ownerId().equals(player.id()))
        {
            return false;
        }

        LeagueInfo.UID uid = new LeagueInfo.UID(Util.toUuid(joinLeague.getLeagueId().getId().toByteArray())
                , joinLeague.getLeagueId().getNum());
        LeagueInfo leagueInfo = leagueInfoMap.get(uid);
        if (leagueInfo == null) return false;

        if (leagueInfo.getPrice() != joinLeague.getPrice() ||
                leagueInfo.getMinHours() > joinLeague.getHours() ||
                leagueInfo.getMaxHours()< joinLeague.getHours()  ||
                leagueInfo.getMembersId().contains(building.id()))//不可重复加盟
        {
            return false;
        }

        if (player.money() < building.area().toCoordinates().size() * leagueInfo.getPrice())
        {
            return false;
        }

        if (!MetaData.getTechsByBuilding(building).contains(leagueInfo.getUid().getTechId()))
        {
            return false;
        }

        Player seller = GameDb.getPlayer(leagueInfo.getUid().getPlayerId());
        player.decMoney(leagueInfo.getPrice());
        seller.addMoney(leagueInfo.getPrice());
        LogDb.playerPay(player.id(), leagueInfo.getPrice());
        LogDb.playerIncome(seller.id(), leagueInfo.getPrice());
        LeagueInfo.Member member = new LeagueInfo.Member(building.id(), joinLeague.getHours());
        leagueInfo.addMember(member);
        GameDb.Update(Arrays.asList(player, seller, leagueInfo));
        buildingLeagueInfo.computeIfAbsent(member.getBuildingId(), k -> new HashSet<>()).add(uid);
        return true;
    }

    //获取建筑加盟技术列表,playerId,techId
    public Set<LeagueInfo.UID> getBuildingLeagueTech(UUID buildingId)
    {
         return buildingLeagueInfo.get(buildingId) == null ?
                 new HashSet<LeagueInfo.UID>() : buildingLeagueInfo.get(buildingId);
    }

    public List<Gs.BuildingTech.Infos> getBuildingLeagueTech(UUID buildingId, int techId)
    {

        List<Gs.BuildingTech.Infos> builders = new ArrayList<>();
        if (buildingLeagueInfo.get(buildingId) != null) {
            buildingLeagueInfo.get(buildingId).forEach(uid ->
            {
                if (uid.getTechId() == techId)
                {
                    LeagueInfo info = leagueInfoMap.get(uid);
                    Gs.BuildingTech.Infos.Builder builder = Gs.BuildingTech.Infos.newBuilder();
                    builder.setPId(Util.toByteString(uid.getPlayerId()));
                    EvaManager.getInstance().getEva(uid.getPlayerId(), uid.getTechId()).forEach(eva -> builder.addTechInfo(eva.toTechInfo()));
                    info.memberForEach(member -> {
                        if (member.getBuildingId().equals(buildingId))
                        {
                            builder.setStartTs(member.getStartTs()).setHours(member.getSignHours());
                        }
                    });
                    builders.add(builder.build());
                }
            });
        }
        return builders;
    }

    
    public Set<BrandLeague> getBrandLeagueList(UUID buildingId)
    {
    	return brandLeagueMap.get(buildingId) == null ?
    			new HashSet<BrandLeague>() : brandLeagueMap.get(buildingId);
    }
    
    public BrandLeague getBrandLeague(UUID buildingId,int techId)
    {
    	Set<BrandLeague> set=getBrandLeagueList(buildingId);
    	for (BrandLeague brandLeague : set) {
    		if(techId==brandLeague.getTechId()){
    			return brandLeague;
    		}
		}
		return null;
    }
    
    public void addBrandLeague(UUID buildingId,int techId,UUID playerId)
    {
    	BrandLeague bl=new BrandLeague(buildingId,techId,playerId);
    	Set<BrandLeague> set=getBrandLeagueList(buildingId);
    	set.add(bl);
    	brandLeagueMap.put(buildingId, set);
    	GameDb.saveOrUpdate(bl);
    }
    
    public void delBrandLeague(UUID buildingId,int techId,UUID playerId)
    {
    	BrandLeague bl=new BrandLeague(buildingId,techId,playerId);
    	Set<BrandLeague> set=getBrandLeagueList(buildingId);
    	set.remove(bl);
    	brandLeagueMap.put(buildingId, set);
    	GameDb.delete(bl);
    }
}
