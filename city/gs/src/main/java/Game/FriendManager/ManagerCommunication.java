package Game.FriendManager;

import Game.GameDb;
import Game.GameServer;
import Game.GameSession;
import Game.Player;
import Shared.Package;
import Shared.Util;
import gs.Gs;
import gscode.GsCode;
import org.apache.log4j.Logger;

import java.util.UUID;

public class ManagerCommunication
{
    private static final Logger LOGGER = Logger.getLogger(ManagerCommunication.class);
    private static ManagerCommunication instance = new ManagerCommunication();
    private ManagerCommunication() { }
    public static ManagerCommunication getInstance()
    {
        return instance;
    }

    public void processing(Gs.CommunicationReq communicationReq, Player player)
    {
        switch (communicationReq.getChannel())
        {
            case WORLD:
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


    private void chatWithPerson(Gs.CommunicationReq communicationReq, Player player)
    {
        if (communicationReq.hasChannelId())
        {
            UUID friend_id = Util.toUuid(communicationReq.getChannelId().toByteArray());
            //blacklist
            if (GameDb.queryPlayer(friend_id).getBlacklist().contains(player.id()))
            {
                //邮件通知黑名单发送消息失败
                return;
            }
            else
            {
                GameSession gameSession = GameServer.allGameSessions.get(friend_id);
                OfflineMessage message = new OfflineMessage(player.id(),
                        Util.toUuid(communicationReq.getChannelId().toByteArray()),
                        communicationReq.getMsg(), player.getName());
                message.setTime(System.currentTimeMillis());

                if (gameSession != null)
                {
                    sendMsgToPersion(gameSession,message);
                }
                //offline save message
                else
                {
                    GameDb.saveOrUpdate(message);
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
