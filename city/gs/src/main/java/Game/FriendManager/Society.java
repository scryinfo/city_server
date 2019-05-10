package Game.FriendManager;

import Game.City;
import Game.GameDb;
import Game.GameServer;
import Game.Player;
import Shared.Util;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import gs.Gs;

import javax.persistence.*;
import java.util.*;

@Entity
public class Society
{
    @Id
    private UUID id;

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

    @Column(nullable = true)
    private String introduction;

    @Column(nullable = false)
    private int memberCount;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "society_member", joinColumns = @JoinColumn(name = "society_id"))
    @MapKeyColumn(name = "member_id")
    private Map<UUID, SocietyMember> memberHashMap = new HashMap<>();

    //添加公会通知时，请使用addNotice()方法，以便插入时清理过期数据
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "society_notice", joinColumns = @JoinColumn(name = "society_id"))
    @OrderColumn
    private List<SocietyNotice> noticeList = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "society_join_req", joinColumns = @JoinColumn(name = "society_id"))
    @MapKeyColumn(name = "player_id")
    private Map<UUID, String> joinMap = new HashMap<>();

    public void addMember(UUID id, SocietyMember member)
    {
        this.memberHashMap.put(id, member);
        this.memberCount = this.memberHashMap.size();
    }

    public SocietyMember delMember(UUID id)
    {
        return this.memberHashMap.remove(id);
    }

    @Transient
    private long lastClearTs = 0;

    public Society()
    {
    }

    public Society(UUID createId, String name, String declaration)
    {
        this.id = UUID.randomUUID();
        this.createId = createId;
        this.createTs = System.currentTimeMillis();
        this.lastModify = 0;
        this.name = name;
        this.declaration = declaration;
        addMember(createId, new SocietyMember(Gs.SocietyMember.Identity.CHAIRMAN_VALUE));
        this.noticeList.add(new SocietyNotice(createId, null, Gs.SocietyNotice.NoticeType.CREATE_SOCIETY_VALUE));
    }

    @Embeddable
    public static class SocietyMember
    {
        @Column(nullable = false)
        private long joinTs;

        @Column(nullable = false)
        private int identity;

        public SocietyMember(int identity)
        {
            this.joinTs = System.currentTimeMillis();
            this.identity = identity;
        }

        public SocietyMember()
        {
        }

        public int getIdentity()
        {
            return identity;
        }

        public void setIdentity(int identity)
        {
            this.identity = identity;
        }

        public Gs.SocietyMember toProto(UUID belongTo, UUID playerId)
        {
            Gs.SocietyMember.Builder memberBuilder = Gs.SocietyMember.newBuilder();
            memberBuilder.setId(Util.toByteString(playerId))
                    .setJoinTs(joinTs);
            memberBuilder.setIdentity(Gs.SocietyMember.Identity.valueOf(identity));
            memberBuilder.setStaffCount(City.instance().calcuPlayerStaff(playerId));
            memberBuilder.setBelongToId(Util.toByteString(belongTo));
            memberBuilder.setOnline(GameServer.isOnline(playerId));
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
                Gs.SocietyNotice.NoticeType.MODIFY_NAME_VALUE,
                Gs.SocietyNotice.NoticeType.MODIFY_INTRODUCTION_VALUE
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

            noticeBuilder.setCreateId(Util.toByteString(createId));

            noticeBuilder.setBelongToId(Util.toByteString(belongTo));

            //非个人行为
            if (!personalAct.contains(noticeType))
            {
                noticeBuilder.setAffectedId(Util.toByteString(affectedId));
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

    public Gs.SocietyMember.Identity getIdentityEnum(UUID id)
    {
        return Gs.SocietyMember.Identity.valueOf(memberHashMap.get(id).identity);
    }

    public void setName(String name)
    {
        this.name = name;
        this.lastModify = System.currentTimeMillis();
    }

    public UUID getCreateId()
    {
        return createId;
    }

    public void setCreateId(UUID createId)
    {
        this.createId = createId;
    }

    public void setDeclaration(String declaration)
    {
        this.declaration = declaration;
    }

    public void setIntroduction(String introduction)
    {
        this.introduction = introduction;
    }

    public List<UUID> getMemberIds()
    {
        return new ArrayList<>(memberHashMap.keySet());
    }

    public Map<UUID, SocietyMember> getMemberHashMap()
    {
        return memberHashMap;
    }

    public void addNotice(SocietyNotice notice)
    {
        this.noticeList.add(notice);
        long clearInterval = 3600000;
        long currentTimeMillis = System.currentTimeMillis();
        if (this.lastClearTs == 0)
        {
            this.lastClearTs = System.currentTimeMillis();
        }
        else if (currentTimeMillis - this.lastClearTs > clearInterval)
        {
            long clearTs = currentTimeMillis - 7 * 24 * 3600000;
            this.noticeList.removeIf(notice1 -> notice1.createTs < clearTs);
            this.lastClearTs = currentTimeMillis;
        }
    }

    public void removeNotice(SocietyNotice notice)
    {
        this.noticeList.remove(notice);
    }

    public Map<UUID, String> getJoinMap()
    {
        return joinMap;
    }
    protected Gs.SocietyInfo toSimpleProto()
    {
        Gs.SocietyInfo.Builder builder = Gs.SocietyInfo.newBuilder();
        builder.setId(Util.toByteString(id))
                .setName(name)
                .setDeclaration(declaration)
                .setCreateTs(createTs)
                .setAllCount(memberCount)
                .setChairmanId(Util.toByteString(createId))
                .setIntroduction(Strings.nullToEmpty(introduction));
        return builder.build();
    }

    //不要随便调用，需要做权限控制，请在society manager中使用
    protected Gs.SocietyInfo toDetailProto(boolean isPower)
    {
        Gs.SocietyInfo.Builder builder = Gs.SocietyInfo.newBuilder();
        builder.setId(Util.toByteString(id))
                .setName(name)
                .setDeclaration(declaration)
                .setCreateTs(createTs)
                .setAllCount(memberCount)
                .setChairmanId(Util.toByteString(createId))
                .setIntroduction(Strings.nullToEmpty(introduction));
        memberHashMap.forEach((memberId, member) ->
        {
            builder.addMembers(member.toProto(id,memberId));
        });

        noticeList.forEach(notice ->{
            builder.addNotice(notice.toProto(id));
        });
        if (isPower)
        {
            joinMap.forEach((k,v) -> builder.addReqs(Gs.JoinReq.newBuilder()
                    .setSocietyId(Util.toByteString(id))
                    .setPlayerId(Util.toByteString(k))
                    .setDescription(v)
                    .build()));
        }
        return builder.build();
    }
}





