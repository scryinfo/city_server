package Game.FriendManager;

import Game.GameDb;
import Game.GameServer;
import Game.GameSession;
import Game.Player;
import Shared.Package;
import Shared.Util;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import common.Common;
import gs.Gs;
import gscode.GsCode;
import org.apache.log4j.Logger;

import java.time.Duration;
import java.util.UUID;

public class ManagerCommunication
{
    private static final Logger LOGGER = Logger.getLogger(ManagerCommunication.class);
    //World Speech Frequency
    private static final long DELAY = 60000;
    private static ManagerCommunication instance = new ManagerCommunication();
    private ManagerCommunication() { }
    public static ManagerCommunication getInstance()
    {
        return instance;
    }

    private static LoadingCache<UUID, Long> worldLimit = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(3))
            .concurrencyLevel(1)
            .maximumSize(10000).build(new CacheLoader<UUID, Long>() {
                @Override
                public Long load(UUID key)
                {
                    return null;
                }
            });

    public void processing(Gs.CommunicationReq communicationReq, Player player)
    {
        switch (communicationReq.getChannel())
        {
            case WORLD:
                messageToEveryone(communicationReq, player);
                break;
            case GROUP:
                break;
            case FRIEND:
            case UNKNOWN:
                chatWithPerson(communicationReq, player);
                break;
            default:
                LOGGER.warn("Illegal session request，channel = " + communicationReq.getChannel().toString());
        }
    }

    private void messageToEveryone(Gs.CommunicationReq communicationReq, Player player)
    {
        if (!Strings.isNullOrEmpty(communicationReq.getMsg()))
        {
            Long old = worldLimit.getIfPresent(player.id());
            Long now = System.currentTimeMillis();
            if (old == null || now - old > DELAY)
            {
                worldLimit.put(player.id(), now);
                Gs.CommunicationProces.Builder builder = Gs.CommunicationProces.newBuilder();
                builder.setChannel(communicationReq.getChannel())
                        .setId(Util.toByteString(player.id()))
                        .setName(player.getName())
                        .setMsg(communicationReq.getMsg())
                        .setTime(now)
                        .setImage(player.getFaceId());
                GameServer.allGameSessions.values().forEach(gameSession ->{
                    if (!gameSession.getPlayer().getBlacklist().contains(player.id()))
                    {
                        gameSession.write(Package.create(GsCode.OpCode.roleCommunication_VALUE, builder.build()));
                    }
                });
            }
            else
            {
                GameServer.allGameSessions.get(player.id()).write(Package.fail((short) GsCode.OpCode.roleCommunication_VALUE, Common.Fail.Reason.highFrequency));
            }
        }
    }


    private void chatWithPerson(Gs.CommunicationReq communicationReq, Player player)
    {
        if (communicationReq.hasChannelId() &&
                !Strings.isNullOrEmpty(communicationReq.getMsg()))
        {
            UUID friend_id = Util.toUuid(communicationReq.getChannelId().toByteArray());
            //blacklist
            if (GameDb.queryPlayer(friend_id).getBlacklist().contains(player.id())
                    || player.getBlacklist().contains(friend_id))
            {
                GameServer.allGameSessions.get(player.id()).write(Package.fail((short) GsCode.OpCode.roleCommunication_VALUE, Common.Fail.Reason.notAllow));
            }
            else
            {
                OfflineMessage message = new OfflineMessage(player.id(),
                        Util.toUuid(communicationReq.getChannelId().toByteArray()),
                        communicationReq.getMsg(), player.getName());
                message.setTime(System.currentTimeMillis());
                //send to self
                sendMsgToPersion(GameServer.allGameSessions.get(player.id()), message);
                GameSession gameSession = GameServer.allGameSessions.get(friend_id);
                if (gameSession != null)
                {
                    sendMsgToPersion(gameSession, message);
                }
                //offline save message
                else
                {
                    GameDb.statelessInsert(message);
                }
            }
        }
    }

    public void sendMsgToPersion(GameSession gameSession,OfflineMessage message)
    {
        Gs.CommunicationProces.Builder builder = Gs.CommunicationProces.newBuilder();
        builder.setId(Util.toByteString(message.getFrom_id()))
                .setName(message.getFrom_name())
                .setMsg(message.getMsg());
        builder.setTime(message.getTime());
        builder.setChannelId(Util.toByteString(message.getTo_id()));

        //friend
        if (FriendManager.playerFriends.getUnchecked(message.getTo_id()).contains(message.getFrom_id()))
        {
            builder.setChannel(Gs.Channel.FRIEND);
            gameSession.write(Package.create(GsCode.OpCode.roleCommunication_VALUE, builder.build()));
        }
        //stranger
        else
        {
            builder.setChannel(Gs.Channel.UNKNOWN);
            gameSession.write(Package.create(GsCode.OpCode.roleCommunication_VALUE, builder.build()));
        }
    }
}
