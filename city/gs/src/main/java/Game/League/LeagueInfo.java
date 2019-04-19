package Game.League;

import Game.Building;
import Game.City;
import Shared.Util;
import gs.Gs;

import javax.persistence.*;
import java.io.Serializable;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Entity
public class LeagueInfo
{
    @EmbeddedId
    private UID uid;

    @Column(nullable = false)
    private boolean isOpen;

    private long price;
    private int maxHours;
    private int minHours;


    @ElementCollection(fetch = FetchType.EAGER)
    @Embedded
    @CollectionTable(name = "LeagueInfo_members")
    private Set<Member> members = new HashSet<>();


    public LeagueInfo(UID uid, boolean isOpen, long price, int maxHours, int minHours)
    {
        this.uid = uid;
        this.isOpen = isOpen;
        this.price = price;
        this.maxHours = maxHours;
        this.minHours = minHours;
    }

    protected void addMember(Member m)
    {
        members.add(m);
    }

    protected void memberForEach(Consumer<Member> action)
    {
        this.members.forEach(action);
    }

    protected Set<UUID> getMembersId()
    {
        return members.stream().map(Member::getBuildingId).collect(Collectors.toSet());
    }

    protected List<Member> memberOverdueAndRemove()
    {
        List<Member> list = new ArrayList<>();
        Iterator<Member> iterator = members.iterator();
        while (iterator.hasNext())
        {
            Member member = iterator.next();
            if (member.isOverdue())
            {
                list.add(member);
                iterator.remove();
            }
        }
        return list;
    }


    protected void openSetting(long price, int maxHours, int minHours)
    {
        this.isOpen = true;
        this.price = price;
        this.maxHours = maxHours;
        this.minHours = minHours;
    }

    protected void closeSetting()
    {
        this.isOpen = false;
        this.price = 0;
        this.maxHours = 0;
        this.minHours = 0;
    }

    public List<Gs.LeagueInfo.JoinMember> toJoinMemberProto()
    {
        List<Gs.LeagueInfo.JoinMember> joinMemberList = new ArrayList<>();
        members.forEach(member -> {
            Building building = City.instance().getBuilding(member.buildingId);
            if (building != null)
            {
                joinMemberList.add(Gs.LeagueInfo.JoinMember.newBuilder()
                        .setBuildingId(Util.toByteString(member.buildingId))
                        .setPlayerId(Util.toByteString(building.ownerId()))
                        .setStartTs(member.startTs)
                        .setHours(member.signHours)
                        .build());
            }

        });
        return joinMemberList;
    }

    public LeagueInfo.Member getMember(UUID buildingId)
    {
    	LeagueInfo.Member m=null;
        for (Member member : members) {
        	if(buildingId.equals(member.buildingId)){
    			m= member;
    			break;
    		}
		}
    	return m;
    }
    
    public Gs.LeagueSetting toSettingProto()
    {
        if (isOpen)
        {
            return Gs.LeagueSetting.newBuilder()
                    .setIsSettingOpen(true)
                    .setPrice(this.price)
                    .setMaxHours(this.maxHours)
                    .setMinHours(this.minHours)
                    .build();
        }
        else
        {
            return Gs.LeagueSetting.newBuilder().setIsSettingOpen(false).build();
        }
    }

    public boolean isOpen()
    {
        return isOpen;
    }
    public int getMemberSize()
    {
        return members.size();
    }

    public long getPrice()
    {
        return price;
    }

    public int getMaxHours()
    {
        return maxHours;
    }

    public Set<UUID> getMemberIds()
    {
        Set<UUID> set = new HashSet<>();
        members.forEach(m->set.add(m.buildingId));
        return set;
    }

    public int getMinHours()
    {
        return minHours;
    }

    public UID getUid()
    {
        return uid;
    }



    @Embeddable
    static class Member
    {
        @Column(nullable = false)
        private UUID buildingId;
        @Column(nullable = false)
        private long startTs;
        @Column(nullable = false)
        private int signHours;

        public UUID getBuildingId()
        {
            return buildingId;
        }

        public boolean isOverdue()
        {
            return System.currentTimeMillis() > startTs + signHours * 36000000;
        }
        
        public long leaveTime(){
        	return startTs + signHours * 36000000;
        }
        
        public Member()
        {
        }

        public Member(UUID buildingId, int signHours)
        {
            this.startTs = System.currentTimeMillis();
            this.buildingId = buildingId;
            this.signHours = signHours;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Member member = (Member) o;
            return Objects.equals(buildingId, member.buildingId);
        }

        @Override
        public int hashCode()
        {

            return Objects.hash(buildingId);
        }
    }

    public LeagueInfo() { }

    @Embeddable
    public static class UID implements Serializable
    {
        private static final long serialVersionUID = 8227555851300702207L;
        @Column(nullable = true)
        private UUID playerId;
        @Column(nullable = true)
        private int techId;

        public UID(UUID playerId, int techId)
        {
            this.playerId = playerId;
            this.techId = techId;
        }

        public UUID getPlayerId()
        {
            return playerId;
        }

        public int getTechId()
        {
            return techId;
        }

        public UID()
        {
        }

        public boolean isTech(int techId)
        {
            return this.techId == techId;
        }



        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UID uid = (UID) o;
            return techId == uid.techId &&
                    Objects.equals(playerId, uid.playerId);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(playerId, techId);
        }
    }
}



