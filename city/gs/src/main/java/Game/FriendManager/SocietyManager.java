package Game.FriendManager;

import Game.GameDb;
import Game.GameServer;
import Game.GameSession;
import Shared.Package;
import Shared.Util;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import common.Common;
import gs.Gs;
import gscode.GsCode;

import java.time.Duration;
import java.util.*;

public class SocietyManager
{
    private static LoadingCache<UUID, Society> societyCache = CacheBuilder.newBuilder()
            .concurrencyLevel(1)
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener(objectInfo ->
                    GameDb.evict(objectInfo.getValue()))
            .build(new CacheLoader<UUID, Society>()
            {
                @Override
                public Society load(UUID key)
                {
                    return GameDb.getSocietyById(key);
                }

            });
    private static ImmutableSet<Integer> modifyPermission = ImmutableSet.of(
            Gs.SocietyMember.Identity.ADMINISTRATOR_VALUE,
            Gs.SocietyMember.Identity.CHAIRMAN_VALUE,
            Gs.SocietyMember.Identity.VICE_CHAIRMAN_VALUE
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
                    societyInfoMap.put(society.getId(),society.toProto(true)));
            lastQueryTime = System.currentTimeMillis();
        }
        return societyInfoMap.values();
    }

    public static Society createSociety(UUID createId, String name, String declaration)
    {
        Society society = new Society(createId, name, declaration);
        if (GameDb.saveOrUpdSociety(society))
        {
            societyCache.put(society.getId(), society);
            societyInfoMap.put(society.getId(), society.toProto(true));
            return society;
        }
        return null;
    }

    public static Society getSociety(UUID uuid)
    {
        return societyCache.getUnchecked(uuid);
    }

    public static void increaseCount(UUID societyId)
    {
        societyCache.getUnchecked(societyId).increaseCount();
    }

    public static void decrementCount(UUID societyId)
    {
        Society society = societyCache.getIfPresent(societyId);
        if (society != null && society.decrementCount() < 1)
        {
            societyCache.invalidate(society);
            GameDb.evict(society);
        }
    }

    public static void modifySocietyName(UUID societyId, String name,
                                         GameSession gameSession, short cmd)
    {
        Society society = societyCache.getUnchecked(societyId);
        if (society != null
                && modifyPermission.contains(society.getIdentity(gameSession.getPlayer().id())))
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
                    societyInfoMap.put(society.getId(), society.toProto(true));
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
        Society society = societyCache.getUnchecked(societyId);
        if (society != null
                && modifyPermission.contains(society.getIdentity(gameSession.getPlayer().id())))
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
            societyInfoMap.put(society.getId(), society.toProto(true));
        }
    }

    public static void modifyIntroduction(UUID societyId, String introduction,
                                         GameSession gameSession, short cmd)
    {
        Society society = societyCache.getUnchecked(societyId);
        if (society != null
                && modifyPermission.contains(society.getIdentity(gameSession.getPlayer().id())))
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
            societyInfoMap.put(society.getId(), society.toProto(true));
        }
    }

}
