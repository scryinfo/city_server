package Game.FriendManager;

import Game.*;
import Shared.Package;
import Shared.Util;
/*import com.google.common.base.Optional;*/
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import common.Common;
import gs.Gs;
import gscode.GsCode;

import java.util.*;

public class SocietyManager
{
    private static LoadingCache<UUID, Optional<Society>> societyCache = CacheBuilder.newBuilder()
            .concurrencyLevel(1)
            .build(new CacheLoader<UUID, Optional<Society>>()
            {
                @Override
                public Optional<Society> load(UUID key)
                {
                    return Optional.ofNullable(GameDb.getSocietyById(key));
                }

            });

    private static Society getSocietyFromCache(UUID key)
    {
        Optional<Society>  opt = societyCache.getUnchecked(key);
        return opt.orElse(null);
    }

    private static final int modifyLevel = 8;
    private static final int chairmanLevel = 10;
    private static ImmutableMap<Gs.SocietyMember.Identity, Integer> permissionLevel = ImmutableMap.of(
            Gs.SocietyMember.Identity.CHAIRMAN, chairmanLevel,
            Gs.SocietyMember.Identity.VICE_CHAIRMAN, 9,
            Gs.SocietyMember.Identity.ADMINISTRATOR, modifyLevel,
            Gs.SocietyMember.Identity.MEMBER, 7
    );

    private static final long modifyInterval = 7 * 24 * 3600 * 1000;
    private static final long queryInterval = 5 * 60 * 1000;

    private static long lastQueryTime = 0L;
    private static Map<UUID,Gs.SocietyInfo> societyInfoMap = new HashMap<>();

    public static Collection<Gs.SocietyInfo> getSocietyList()
    {
        if (System.currentTimeMillis() - lastQueryTime > queryInterval)
        {
            GameDb.getAllSociety().forEach(society ->
                    societyInfoMap.put(society.getId(),society.toSimpleProto()));
            lastQueryTime = System.currentTimeMillis();
        }
        return societyInfoMap.values();
    }

    public static Society createSociety(UUID createId, String name, String declaration)
    {
        Society society = new Society(createId, name, declaration);
        if (GameDb.saveOrUpdSociety(society))
        {
            societyCache.put(society.getId(), Optional.of(society));
            societyInfoMap.put(society.getId(), society.toSimpleProto());
            return society;
        }
        return null;
    }

    public static Gs.SocietyInfo toSocietyDetailProto(Society society,Player player)
    {
        if (canModify(society.getIdentity(player.id())))
        {
            return society.toDetailProto(true);
        }
        return society.toDetailProto(false);
    }

    public static Society getSociety(UUID uuid)
    {
        return getSocietyFromCache(uuid);
    }

    public static void broadOnline(Player player)
    {
        if (player.getSocietyId() != null)
        {
            Society society = getSocietyFromCache(player.getSocietyId());
            if (society != null)
            {
                Gs.MemberChange.Builder builder = Gs.MemberChange.newBuilder();
                builder.setSocietyId(Util.toByteString(society.getId()))
                        .setPlayerId(Util.toByteString(player.id()))
                        .setType(Gs.MemberChange.ChangeType.ONLINE);
                GameServer.sendTo(society.getMemberIds(),Package.create(GsCode.OpCode.memberChange_VALUE,
                        Gs.MemberChanges.newBuilder()
                                .addAllChangeLists(Collections.singletonList(builder.build()))
                                .build()));
            }

        }
    }

    //必须在allGameSessions.remove(id())之后调用
    public static void broadOffline(Player player)
    {
        if (player.getSocietyId() != null)
        {
            Society society = getSocietyFromCache(player.getSocietyId());
            if (society != null)
            {
                Gs.MemberChange.Builder builder = Gs.MemberChange.newBuilder();
                builder.setSocietyId(Util.toByteString(society.getId()))
                        .setPlayerId(Util.toByteString(player.id()))
                        .setType(Gs.MemberChange.ChangeType.OFFLINE);

                GameServer.sendTo(society.getMemberIds(),Package.create(GsCode.OpCode.memberChange_VALUE,
                        Gs.MemberChanges.newBuilder()
                                .addAllChangeLists(Collections.singletonList(builder.build()))
                                .build()));
                if (Sets.intersection(GameServer.allGameSessions.keySet(),
                        new HashSet<>(society.getMemberIds())).isEmpty())
                {
                    societyCache.invalidate(society.getId());
                    //GameDb.evict(society);
                }
            }
        }
    }

    private static boolean canModify(int identity)
    {
        return permissionLevel.get(Gs.SocietyMember.Identity.valueOf(
                identity)) >= modifyLevel;
    }

    public static void modifySocietyName(UUID societyId, String name,
                                         GameSession gameSession, short cmd)
    {
        Society society = getSocietyFromCache(societyId);
        if (society != null
                && canModify(society.getIdentity(gameSession.getPlayer().id())))
        {
            if (System.currentTimeMillis() - society.getLastModify() < modifyInterval)
            {
                gameSession.write(Package.fail(cmd, Common.Fail.Reason.highFrequency));
            }
            else
            {
                society.setName(name);
                Society.SocietyNotice notice = new Society.SocietyNotice(
                        gameSession.getPlayer().id(),
                        null,
                        Gs.SocietyNotice.NoticeType.MODIFY_NAME_VALUE);
                society.addNotice(notice);
                //重名
                if (GameDb.saveOrUpdSociety(society))
                {
                    Gs.BytesStrings info = Gs.BytesStrings.newBuilder()
                            .setSocietyId(Util.toByteString(societyId))
                            .setStr(name)
                            .setCreateId(Util.toByteString(gameSession.getPlayer().id()))
                            .build();

                    GameServer.sendTo(society.getMemberIds(), Package.create(cmd, info));
                    GameServer.sendTo(society.getMemberIds(), Package.create(GsCode.OpCode.noticeAdd_VALUE, notice.toProto(societyId)));
                    societyInfoMap.put(society.getId(), society.toSimpleProto());
                }
                else
                {
                    society.removeNotice(notice);
                    gameSession.write(Package.fail(cmd, Common.Fail.Reason.societyNameDuplicated));
                }

            }

        }
    }

    public static void modifyDeclaration(UUID societyId, String declaration,
                                         GameSession gameSession, short cmd)
    {
        Society society = getSocietyFromCache(societyId);
        if (society != null
                && canModify(society.getIdentity(gameSession.getPlayer().id())))
        {
            society.setDeclaration(declaration);
            Society.SocietyNotice notice = new Society.SocietyNotice(gameSession.getPlayer().id(),
                    null,
                    Gs.SocietyNotice.NoticeType.MODIFY_DECLARATION_VALUE);
            society.addNotice(notice);
            GameDb.saveOrUpdate(society);

            Gs.BytesStrings info = Gs.BytesStrings.newBuilder()
                    .setSocietyId(Util.toByteString(societyId))
                    .setStr(declaration)
                    .setCreateId(Util.toByteString(gameSession.getPlayer().id()))
                    .build();
            GameServer.sendTo(society.getMemberIds(), Package.create(cmd, info));
            GameServer.sendTo(society.getMemberIds(), Package.create(GsCode.OpCode.noticeAdd_VALUE, notice.toProto(societyId)));
            //societyInfoMap.put(society.getId(), society.toProto(true));
        }
    }

    public static void modifyIntroduction(UUID societyId, String introduction,
                                         GameSession gameSession, short cmd)
    {
        Society society = getSocietyFromCache(societyId);
        if (society != null
                && canModify(society.getIdentity(gameSession.getPlayer().id())))
        {
            society.setIntroduction(introduction);
            Society.SocietyNotice notice = new Society.SocietyNotice(gameSession.getPlayer().id(),
                    null,
                    Gs.SocietyNotice.NoticeType.MODIFY_INTRODUCTION_VALUE);
            society.addNotice(notice);
            GameDb.saveOrUpdate(society);

            Gs.BytesStrings info = Gs.BytesStrings.newBuilder()
                    .setSocietyId(Util.toByteString(societyId))
                    .setStr(introduction)
                    .setCreateId(Util.toByteString(gameSession.getPlayer().id()))
                    .build();
            GameServer.sendTo(society.getMemberIds(), Package.create(cmd, info));
            GameServer.sendTo(society.getMemberIds(), Package.create(GsCode.OpCode.noticeAdd_VALUE, notice.toProto(societyId)));
            societyInfoMap.put(society.getId(), society.toSimpleProto());
        }
    }

    public static boolean reqJoinSociety(UUID societyId, Player player,String descp)
    {
        Society society = getSocietyFromCache(societyId);
        if (society != null)
        {
            society.getJoinMap().put(player.id(),descp);
            GameDb.saveOrUpdate(society);

            Gs.JoinReq.Builder builder = Gs.JoinReq.newBuilder();
            builder.setSocietyId(Util.toByteString(societyId))
                    .setPlayerId(Util.toByteString(player.id()))
                    .setDescription(descp);
            GameServer.sendTo(getmodifyPermissionIds(society),
                    Package.create(GsCode.OpCode.newJoinReq_VALUE, builder.build()));
            return true;
        }
        return false;
    }

    private static List<UUID> getmodifyPermissionIds(Society society)
    {
        List<UUID> list = new ArrayList<>();
        society.getMemberHashMap().forEach((k,v)->{
            if (canModify(v.getIdentity()))
            {
                list.add(k);
            }
        });
        return list;
    }

    public static void handleReqJoin(Gs.JoinHandle params, Player handler)
    {
        UUID societyId = Util.toUuid(params.getSocietyId().toByteArray()); // 拿到申请加入的公会id
        if (societyId.equals(handler.getSocietyId()))  //保证当前处理人已经在公会中
        {
            Society society = getSocietyFromCache(societyId);  // 缓存中查找公会
            UUID reqId = Util.toUuid(params.getPlayerId().toByteArray()); //获取申请人id
            if (society != null &&
                    !society.getMemberIds().contains(reqId)
                    && canModify(society.getIdentity(handler.id())))
            {
                Player reqPlayer = GameDb.getPlayer(reqId);  // 获取申请人信息
                Gs.JoinReq.Builder builder = Gs.JoinReq.newBuilder();
                society.getJoinMap().remove(reqId);
                GameDb.saveOrUpdate(society);
                boolean isSuccess = false;
                Society.SocietyNotice notice = new Society.SocietyNotice(handler.id(),
                        reqId,Gs.SocietyNotice.NoticeType.JOIN_SOCIETY_VALUE);  // 创建公会公告

                //已加入其他公会
                if (reqPlayer.getSocietyId() != null)
                {
                    builder.setSocietyId(Util.toByteString(societyId))
                            .setPlayerId(Util.toByteString(reqId))
                            .setHandleId(Util.toByteString(handler.id()))
                            .setServerFlag(false);
                }
                else if (params.getIsAgree())
                {
                    isSuccess = true;
                    society.addMember(reqId,
                            new Society.SocietyMember(Gs.SocietyMember.Identity.MEMBER_VALUE));
                    society.addNotice(notice);
                    reqPlayer.setSocietyId(societyId);
                    GameDb.saveOrUpdate(Arrays.asList(reqPlayer,society));

                    builder.setSocietyId(Util.toByteString(societyId))
                            .setPlayerId(Util.toByteString(reqId))
                            .setHandleId(Util.toByteString(handler.id()))
                            .setServerFlag(true)
                            .setHandleFlag(true);
                    /**
                     * TODO:
                     * 2019/2/25
                     * 发送邮件给申请人入会成功
                     */

                    Mail mail = new Mail(Mail.MailType.ADD_SOCIETY_SUCCESS.getMailType(),reqId,null,new UUID[]{societyId},null);
                    GameDb.saveOrUpdate(mail);
                    GameServer.sendTo(Arrays.asList(reqId), Package.create(GsCode.OpCode.newMailInform_VALUE, mail.toProto()));
                }
                //拒绝
                else
                {
                    builder.setSocietyId(Util.toByteString(societyId))
                            .setPlayerId(Util.toByteString(reqId))
                            .setHandleId(Util.toByteString(handler.id()))
                            .setServerFlag(true)
                            .setHandleFlag(false);
                    /**
                     * TODO:
                     * 2019/2/22
                     * 发送邮件给申请人入会请求被拒绝 reqId
                     */

                    Mail mail = new Mail(Mail.MailType.ADD_SOCIETY_FAIL.getMailType(),reqId,null,new UUID[]{societyId},null);
                    GameDb.saveOrUpdate(mail);
                    GameServer.sendTo(Arrays.asList(reqId), Package.create(GsCode.OpCode.newMailInform_VALUE, mail.toProto()));

                }
                //通知权限人清除该请求
                GameServer.sendTo(getmodifyPermissionIds(society),
                        Package.create(GsCode.OpCode.delJoinReq_VALUE, builder.build()));

                if (isSuccess)
                {
                    Gs.MemberChange.Builder mchange = Gs.MemberChange.newBuilder();
                    mchange.setSocietyId(Util.toByteString(societyId))
                            .setPlayerId(Util.toByteString(reqId))
                            .setType(Gs.MemberChange.ChangeType.JOIN)
                            .setInfo(society.getMemberHashMap().get(reqId).toProto(societyId, reqId));

                    //给申请人发送加入公会信息
                    GameServer.sendTo(Collections.singletonList(reqId),
                            Package.create(GsCode.OpCode.joinHandle_VALUE,
                                    toSocietyDetailProto(society,reqPlayer)));

                    List<UUID> list = society.getMemberIds();
                    list.remove(reqId);
                    //通知所有人角色变更
                    GameServer.sendTo(list,Package.create(GsCode.OpCode.memberChange_VALUE,
                            Gs.MemberChanges.newBuilder()
                                    .addAllChangeLists(Collections.singletonList(mchange.build()))
                                    .build()));
                    //入会公告
                    GameServer.sendTo(list,Package.create(GsCode.OpCode.noticeAdd_VALUE,
                            notice.toProto(societyId)));

                }
            }
        }
    }


    public static boolean exitSociety(UUID societyId, Player player)
    {
        Society society = getSocietyFromCache(societyId);
        if (society != null && society.getMemberIds().contains(player.id()))
        {
            if (permissionLevel.get(
                    Gs.SocietyMember.Identity.valueOf(society.getIdentity(player.id())))
                    != chairmanLevel)
            {
                player.setSocietyId(null);
                society.delMember(player.id());
                Society.SocietyNotice notice = new Society.SocietyNotice(player.id(), null,
                        Gs.SocietyNotice.NoticeType.EXIT_SOCIETY_VALUE);
                society.addNotice(notice);
                GameDb.saveOrUpdate(Arrays.asList(player, society));


                Gs.MemberChange.Builder mChange = Gs.MemberChange.newBuilder();
                mChange.setSocietyId(Util.toByteString(societyId))
                        .setPlayerId(Util.toByteString(player.id()))
                        .setType(Gs.MemberChange.ChangeType.EXIT);

                GameServer.sendTo(society.getMemberIds(),Package.create(GsCode.OpCode.memberChange_VALUE,
                        Gs.MemberChanges.newBuilder()
                                .addAllChangeLists(Collections.singletonList(mChange.build()))
                                .build()));

                GameServer.sendTo(society.getMemberIds(),Package.create(GsCode.OpCode.noticeAdd_VALUE,
                        notice.toProto(societyId)));
                return true;
            }
            else if (society.getMemberIds().size() == 1 )
            {
                player.setSocietyId(null);
                GameDb.saveOrUpdateAndDelete(Collections.singletonList(player), Collections.singletonList(society));
                societyCache.invalidate(societyId);
                societyInfoMap.remove(societyId);
                return true;
            }
        }
        return false;
    }

    public static boolean kickMember(UUID societyId, Player player, UUID kickId)
    {
        Society society = getSocietyFromCache(societyId);
        if (society != null && society.getMemberIds().contains(kickId))
        {
            if (canOperation(society,player.id(),kickId))
            {
                Player kickPlayer = GameDb.getPlayer(kickId);
                kickPlayer.setSocietyId(null);
                society.delMember(kickId);
                Society.SocietyNotice notice = new Society.SocietyNotice(player.id(), kickId,
                        Gs.SocietyNotice.NoticeType.KICK_OUT_SOCIETY_VALUE);
                society.addNotice(notice);
                GameDb.saveOrUpdate(Arrays.asList(kickPlayer, society));

                GameServer.sendTo(society.getMemberIds(), Package.create(GsCode.OpCode.noticeAdd_VALUE,
                        notice.toProto(societyId)));
                Gs.MemberChange.Builder mChange = Gs.MemberChange.newBuilder();
                mChange.setSocietyId(Util.toByteString(societyId))
                        .setPlayerId(Util.toByteString(kickId))
                        .setType(Gs.MemberChange.ChangeType.EXIT);

                GameServer.sendTo(society.getMemberIds(), Package.create(GsCode.OpCode.memberChange_VALUE,
                        Gs.MemberChanges.newBuilder()
                                .addAllChangeLists(Collections.singletonList(mChange.build()))
                                .build()));
                return true;
            }
        }
        return false;
    }

    private static boolean canOperation(Society society, UUID drivingId, UUID affectId)
    {
        return permissionLevel.get(society.getIdentityEnum(drivingId)) >
                permissionLevel.get(society.getIdentityEnum(affectId));
    }

    public static boolean appointerPost(Player player, Gs.AppointerReq params)
    {
        UUID societyId = Util.toUuid(params.getSocietyId().toByteArray());
        UUID appointId = Util.toUuid(params.getPlayerId().toByteArray());
        boolean isSuccess = false;
        Society society = getSocietyFromCache(societyId);
        if (society != null && society.getMemberIds().contains(appointId)
                && canOperation(society, player.id(), appointId))
        {
            Gs.SocietyMember.Identity identity = params.getIdentity();
            Society.SocietyNotice notice = new Society.SocietyNotice(player.id(), appointId,
                    getTypeByIdentity(identity).getNumber());
            if (identity.equals(Gs.SocietyMember.Identity.CHAIRMAN) &&
                    society.getIdentity(player.id()) == Gs.SocietyMember.Identity.CHAIRMAN_VALUE
                    && society.getCreateId().equals(player.id()))
            {
                society.setCreateId(appointId);
                society.getMemberHashMap().get(player.id()).setIdentity(Gs.SocietyMember.Identity.MEMBER_VALUE);
                society.getMemberHashMap().get(appointId).setIdentity(Gs.SocietyMember.Identity.CHAIRMAN_VALUE);
                society.addNotice(notice);
                GameDb.saveOrUpdate(society);
                societyInfoMap.put(societyId, society.toSimpleProto());

                GameServer.sendTo(society.getMemberIds(), Package.create(GsCode.OpCode.noticeAdd_VALUE,
                        notice.toProto(societyId)));
                Gs.MemberChange.Builder mChange = Gs.MemberChange.newBuilder();
                mChange.setSocietyId(Util.toByteString(societyId))
                        .setPlayerId(Util.toByteString(appointId))
                        .setType(Gs.MemberChange.ChangeType.IDENTITY)
                        .setIdentity(identity);
                Gs.MemberChange.Builder mChange1 = Gs.MemberChange.newBuilder();
                mChange1.setSocietyId(Util.toByteString(societyId))
                        .setPlayerId(Util.toByteString(player.id()))
                        .setType(Gs.MemberChange.ChangeType.IDENTITY)
                        .setIdentity(Gs.SocietyMember.Identity.MEMBER);
                GameServer.sendTo(society.getMemberIds(), Package.create(GsCode.OpCode.memberChange_VALUE,
                        Gs.MemberChanges.newBuilder()
                                .addAllChangeLists(Arrays.asList(mChange.build(), mChange1.build()))
                                .build()));

                isSuccess = true;
            }
            else if (permissionLevel.get(society.getIdentityEnum(player.id())) >
                    permissionLevel.get(identity))
            {
                society.getMemberHashMap().get(appointId).setIdentity(identity.getNumber());

                society.addNotice(notice);
                GameDb.saveOrUpdate(society);

                GameServer.sendTo(society.getMemberIds(), Package.create(GsCode.OpCode.noticeAdd_VALUE,
                        notice.toProto(societyId)));

                Gs.MemberChange.Builder mChange = Gs.MemberChange.newBuilder();
                mChange.setSocietyId(Util.toByteString(societyId))
                        .setPlayerId(Util.toByteString(appointId))
                        .setType(Gs.MemberChange.ChangeType.IDENTITY)
                        .setIdentity(identity);
                GameServer.sendTo(society.getMemberIds(), Package.create(GsCode.OpCode.memberChange_VALUE,
                        Gs.MemberChanges.newBuilder()
                                .addAllChangeLists(Collections.singletonList(mChange.build()))
                                .build()));
                isSuccess = true;
            }
        }
        return isSuccess;
    }

    private static Gs.SocietyNotice.NoticeType getTypeByIdentity(Gs.SocietyMember.Identity identity)
    {
        switch (identity) {
            case VICE_CHAIRMAN:
                return Gs.SocietyNotice.NoticeType.APPOINT_TO_VICE_CHAIRMAN;
            case ADMINISTRATOR:
                return Gs.SocietyNotice.NoticeType.APPOINT_TO_ADMINISTRATOR;
            case CHAIRMAN:
                return Gs.SocietyNotice.NoticeType.APPOINT_TO_CHAIRMAN;
            case MEMBER:
            default:
                return Gs.SocietyNotice.NoticeType.APPOINT_TO_MEMBER;
        }
    }

    public static void sendMessageToEveryOne(Gs.CommunicationReq communicationReq, Player player)
    {
        UUID societyId = Util.toUuid(communicationReq.getChannelId().toByteArray());
        if (societyId.equals(player.getSocietyId()))
        {
            Society society = getSocietyFromCache(societyId);
            if (society != null)
            {
                Gs.CommunicationProces.Builder builder = Gs.CommunicationProces.newBuilder();
                builder.setId(Util.toByteString(player.id()))
                        .setMsg(communicationReq.getMsg())
                        .setTime(System.currentTimeMillis())
                        .setChannel(communicationReq.getChannel())
                        .setChannelId(communicationReq.getChannelId());
                GameServer.sendTo(society.getMemberIds(), Package.create(GsCode.OpCode.roleCommunication_VALUE,
                        builder.build()));
            }
        }
    }

    public static Gs.SocietyInfo toSocietyDetailProto(Society society)
    {
            return society.toDetailProto(true);
    }
}