package Game.FriendManager;

import Game.*;
import Shared.Package;
import Shared.Util;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import gs.Gs;
import gscode.GsCode;
import io.netty.channel.ChannelId;
import org.apache.log4j.Logger;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class FriendManager
{
    private static final Logger LOGGER = Logger.getLogger(FriendManager.class);
    private FriendManager() {}
    private static FriendManager instance = new FriendManager();
    public static FriendManager getInstance()
    {
        return instance;
    }

    public void init()
    {
        try
        {
            GameDb.createFriendTable();
        }
        catch (Throwable throwable)
        {
            LOGGER.fatal("Initial friend table failed." + throwable);
            throw new ExceptionInInitializerError(throwable);
        }
    }

    public static LoadingCache<UUID, Set<UUID>> playerFriends = CacheBuilder.newBuilder()
            .concurrencyLevel(1)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build(new CacheLoader<UUID, Set<UUID>>()
    {
        @Override
        public Set<UUID> load(UUID key)
        {
            return GameDb.queryFriends(key);
        }
    });

    public Set<Gs.ByteBool> getFriends(UUID pid)
    {
        Set<Gs.ByteBool> friends = new HashSet<>();
        playerFriends.getUnchecked(pid).forEach((v)-> {
            Gs.ByteBool.Builder builder = Gs.ByteBool.newBuilder();
            builder.setId(Util.toByteString(v));
            if (GameServer.allGameSessions.containsKey(v))
            {
                builder.setB(true);
            }
            else builder.setB(false);
            friends.add(builder.build());
        });
        return friends;
    }

    public void broadcastStatue(UUID pid,boolean online)
    {
        Set<ChannelId> channels = new HashSet<>();
        playerFriends.getUnchecked(pid).forEach(id->{
            if (GameServer.allGameSessions.get(id) != null)
            {
                channels.add(GameServer.allGameSessions.get(id).channelId());
            }
        });
        Package p = Package.create(GsCode.OpCode.roleStatusChange_VALUE,
                Gs.ByteBool.newBuilder()
                        .setId(Util.toByteString(pid))
                        .setB(online).build());
        if (!online)
        {
            playerFriends.invalidate(pid);
        }
        GameServer.sendTo(channels,p);
    }

    public List<Player.Info> searchPlayByName(String name)
    {
        return GameDb.queryPlayByPartialName(name);
    }

    public void saveFriendship(UUID id1,UUID id2)
    {
        //update cache and notify
        GameDb.addFriend(id1, id2);
        updateCache(id1, id2);
        updateCache(id2, id1);
    }

    private void updateCache(UUID id1, UUID id2)
    {
        Set<UUID> set =  playerFriends.getIfPresent(id1);
        if (set != null)
        {
            set.add(id2);
        }
    }

    public void deleteFriend(UUID pid,UUID fid)
    {
        GameDb.deleteFriend(pid, fid);
        deleteCache(pid, fid);
        deleteCache(fid, pid);
    }

    private void deleteCache(UUID id1, UUID id2)
    {
        Set<UUID> set = null;
        if ((set = playerFriends.getIfPresent(id1)) != null)
        {
            set.remove(id2);
        }
        GameSession gameSession = GameServer.allGameSessions.get(id1);
        if (gameSession != null)
        {
            Gs.Id delFirend = Gs.Id.newBuilder()
                    .setId(Util.toByteString(id2))
                    .build();
            gameSession.write(
                    Package.create(GsCode.OpCode.deleteFriend_VALUE, delFirend));
        }
    }
}
