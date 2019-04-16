package Game.League;

import Game.*;
import Game.Meta.MetaData;
import Game.Timers.PeriodicTimer;
import Shared.Util;
import gs.Gs;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private Map<LeagueInfo.UID, LeagueInfo> leagueInfoMap = new HashMap<>();
    //key = buildingId
    private Map<UUID, Set<LeagueInfo.UID>> buildingLeagueInfo = new HashMap<>();
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
                leagueInfo.getMembersId().contains(building.id()))
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
}
