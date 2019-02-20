package Game.FriendManager;

import Game.City;
import Game.GameDb;
import Game.Player;
import Shared.Util;
import com.google.common.collect.ImmutableSet;
import gs.Gs;

import javax.persistence.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Entity
public class Society
{
    @Id
    private UUID id;

    @Transient
    private AtomicInteger onlineCount = new AtomicInteger(0);

    @Column(nullable = false)
    private UUID createId;

    @Column(nullable = false)
    private long createTs;

    @Column(nullable = false)
    private long lastModify;

    @Column(nullable = false,unique = true)
    private String name;

    @Column(nullable = false)
    private String declaration;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "society_member", joinColumns = @JoinColumn(name = "society_id"))
    @MapKeyColumn(name = "member_id")
    private Map<UUID, SocietyMember> memberHashMap = new HashMap<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "society_notice", joinColumns = @JoinColumn(name = "society_id"))
    private List<SocietyNotice> noticeList = new ArrayList<>();

    public Society()
    {
    }

    public Society(UUID createId, String name, String declaration)
    {
        this.id = UUID.randomUUID();
        this.createId = createId;
        this.lastModify = this.createTs = System.currentTimeMillis();
        this.name = name;
        this.declaration = declaration;
        this.memberHashMap.put(createId, new SocietyMember(System.currentTimeMillis(), Gs.SocietyMember.Identity.CHAIRMAN_VALUE));
        this.noticeList.add(new SocietyNotice(createId, null, Gs.SocietyNotice.NoticeType.CREATE_SOCIETY_VALUE));
    }

    @Embeddable
    public static class SocietyMember
    {
        @Column(nullable = false)
        private long joinTs;

        @Column(nullable = false)
        private int identity;

        public SocietyMember(long joinTs, int identity)
        {
            this.joinTs = joinTs;
            this.identity = identity;
        }

        public SocietyMember()
        {
        }

        public Gs.SocietyMember toProto(UUID belongTo, Player player)
        {
            Gs.SocietyMember.Builder memberBuilder = Gs.SocietyMember.newBuilder();
            memberBuilder.setId(Util.toByteString(player.id()))
                    .setName(player.getName())
                    .setFaceId(player.getFaceId())
                    .setJoinTs(joinTs)
                    .setMoney(player.money());
            memberBuilder.setIdentity(Gs.SocietyMember.Identity.valueOf(identity));
            memberBuilder.setStaffCount(City.instance().calcuPlayerStaff(player.id()));
            memberBuilder.setBelongToId(Util.toByteString(belongTo));
            return memberBuilder.build();
        }
    }

    @Embeddable
    public static class SocietyNotice
    {
        @Column(nullable = false)
        private long createTs;

        private UUID createId;

        private UUID affectedId;

        @Column(nullable = false)
        private int noticeType;

        @Transient
        private static ImmutableSet<Integer> personalAct = ImmutableSet.of(
                Gs.SocietyNotice.NoticeType.EXIT_SOCIETY_VALUE,
                Gs.SocietyNotice.NoticeType.CREATE_SOCIETY_VALUE,
                Gs.SocietyNotice.NoticeType.MODIFY_DECLARATION_VALUE,
                Gs.SocietyNotice.NoticeType.MODIFY_NAME_VALUE
        );

        public SocietyNotice(UUID createId, UUID affectedId, int noticeType)
        {
            this.createTs = System.currentTimeMillis();
            this.createId = createId;
            this.affectedId = affectedId;
            this.noticeType = noticeType;
        }

        public SocietyNotice()
        {
        }

        public Gs.SocietyNotice toProto(UUID belongTo)
        {
            Gs.SocietyNotice.Builder noticeBuilder = Gs.SocietyNotice.newBuilder();
            noticeBuilder.setCreateTs(createTs);
            noticeBuilder.setType(Gs.SocietyNotice.NoticeType.valueOf(noticeType));

            Player tmpPlayer = GameDb.queryPlayer(createId);
            noticeBuilder.setCreateId(Util.toByteString(createId));
            noticeBuilder.setCreateFaceId(tmpPlayer.getFaceId());
            noticeBuilder.setCreateName(tmpPlayer.getName());
            noticeBuilder.setBelongToId(Util.toByteString(belongTo));

            //非个人行为
            if (!personalAct.contains(noticeType))
            {
                tmpPlayer = GameDb.queryPlayer(affectedId);
                noticeBuilder.setAffectedId(Util.toByteString(affectedId));
                noticeBuilder.setAffectedFaceId(tmpPlayer.getFaceId());
                noticeBuilder.setAffectedName(tmpPlayer.getName());
            }
            return noticeBuilder.build();
        }

    }

    public UUID getId()
    {
        return id;
    }

    public long getLastModify()
    {
        return lastModify;
    }

    public int getIdentity(UUID id)
    {
        return memberHashMap.get(id).identity;
    }

    public void setName(String name)
    {
        this.name = name;
        this.lastModify = System.currentTimeMillis();
    }

    public void setDeclaration(String declaration)
    {
        this.declaration = declaration;
    }

    public List<UUID> getMemberIds()
    {
        return new ArrayList<>(memberHashMap.keySet());
    }

    public void increaseCount()
    {
        this.onlineCount.incrementAndGet();
    }

    public int decrementCount()
    {
        return this.onlineCount.decrementAndGet();
    }

    public void addNotice(SocietyNotice notice)
    {
        this.noticeList.add(notice);
    }

    public void removeNotice(SocietyNotice notice)
    {
        this.noticeList.remove(notice);
    }

    public Gs.SocietyInfo toProto(boolean isSimple)
    {
        Gs.SocietyInfo.Builder builder = Gs.SocietyInfo.newBuilder();
        builder.setId(Util.toByteString(id))
                .setName(name)
                .setDeclaration(declaration)
                .setCreateTs(createTs)
                .setAllCount(memberHashMap.size())
                .setChairmanId(Util.toByteString(createId));
        Player player = GameDb.queryPlayer(createId);
        builder.setChairmanName(player.getName())
                .setChairmanFaceId(player.getFaceId());
        memberHashMap.forEach((memberId, member) ->
        {
            Player player1 = GameDb.queryPlayer(memberId);
            if (!isSimple)
            {
                builder.addMembers(member.toProto(id,player1));
            }
            builder.setAllMoney(builder.getAllMoney() + player1.money());
        });
        if (!isSimple)
        {
            noticeList.forEach(notice ->{
                builder.addNotice(notice.toProto(id));
            });
        }
        return builder.build();
    }
}





