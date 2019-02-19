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

import java.time.Duration;
import java.util.UUID;

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

    public static Society createSociety(UUID createId, String name, String declaration)
    {
        Society society = new Society(createId,name,declaration);
        if (GameDb.saveOrUpdSociety(society))
        {
            societyCache.put(society.getId(), society);
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
                gameSession.write(Package.fail(cmd,Common.Fail.Reason.highFrequency));
            }
            else
            {
                society.setName(name);
                if (GameDb.saveOrUpdSociety(society))
                {
                    Gs.BytesStrings info = Gs.BytesStrings.newBuilder()
                            .setSocietyId(Util.toByteString(societyId))
                            .setStr(name)
                            .setCreateId(Util.toByteString(gameSession.getPlayer().id()))
                            .build();
                    GameServer.sendTo(society.getMemberIds(), Package.create(cmd, info));
                }
                else
                {
                    gameSession.write(Package.fail(cmd,Common.Fail.Reason.societyNameDuplicated));
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
            GameDb.saveOrUpdate(society);
            Gs.BytesStrings info = Gs.BytesStrings.newBuilder()
                    .setSocietyId(Util.toByteString(societyId))
                    .setStr(declaration)
                    .setCreateId(Util.toByteString(gameSession.getPlayer().id()))
                    .build();
            GameServer.sendTo(society.getMemberIds(), Package.create(cmd, info));
        }
    }
}
