package Account;

import Shared.*;
import Shared.Package;
import as.As;
import com.google.protobuf.Message;
import common.Common;
import ga.Ga;
import gacode.GaCode;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import org.apache.log4j.Logger;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;


public class AccountSession {
    private ChannelHandlerContext ctx;
    private static final Logger logger = Logger.getLogger(AccountSession.class);
    private boolean valid = false;
    private AccountInfo accInfo;
    private String accountName;
    private String validateCode;

    public boolean valid() {
        return valid;
    }

    public void logout() {
        AccountServer.clientAccountToChannelId.remove(this.accountName());
        if (GlobalConfig.product()) {
            Validator.getInstance().unRegist(accountName, validateCode);
        }
    }


    public final String accountName() {
        return accInfo.name;
    }

    public AccountSession(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }
    public void write(Package pack) {
        ctx.channel().writeAndFlush(pack);
    }
    public void login(short cmd, Message message) {
        if (!valid()) {
            As.Login c = (As.Login)message;
            accountName = c.getAccount();
            validateCode = "";

            boolean product = GlobalConfig.product();
            if (product) {
                if (Validator.getInstance().validate(accountName, "") == 0)
                    return;
            }

            accInfo = AccountDb.get(accountName);
            if (accInfo == null) {
                accInfo = AccountDb.create(accountName);
                if (accInfo == null) {
                    return;
                }
            }

            if (accInfo.freezeTime.isAfter(Instant.now())) {
                this.write(Shared.Package.fail(cmd, Common.Fail.Reason.accountInFreeze));
                return;
            }

            this.write(Shared.Package.create(cmd));
            valid = true;
            AccountServer.allClientChannels.add(ctx.channel());
            AccountServer.clientAccountToChannelId.put(this.accountName(), ctx.channel().id());
            logger.debug(this.accountName + " login");
        }
    }

    public void chooseGameServer(short cmd, Message message) {
        As.ChoseGameServer c = (As.ChoseGameServer)message;
        int chooseId = c.getServerId();
        GameServerInfo gsInfo = ServerCfgDb.getGameServerInfo(chooseId);
        if (gsInfo == null)
            return;

        Instant now = Instant.now();
        if (gsInfo.getCreateTime().isAfter(now) || gsInfo.getMaintainEndTime().isAfter(now))
            return;
        ChannelId chId = AccountServer.gsIdToChannelId.get(chooseId);
        if(chId == null)
            return;
        Channel gs = AccountServer.allGsChannels.find(chId);
        if (gs != null) {
            Ga.ValidationCode.Builder cv = Ga.ValidationCode.newBuilder();
            cv.setAccountName(accountName);
            cv.setCode(UUID.randomUUID().toString());
            logger.debug("rely token to game server " + cv.getCode());
            gs.writeAndFlush(Shared.Package.create(GaCode.OpCode.validateInfo_VALUE, cv.build()));
        } else {
            this.write(Shared.Package.fail(cmd, Common.Fail.Reason.gameServerNotOnline));
        }
    }

    public void getServerList(short cmd) {
        this.write(Package.create(cmd, getServersInfo()));
    }

    private As.AllGameServerInfo getServersInfo() {
        As.AllGameServerInfo.Builder ca = As.AllGameServerInfo.newBuilder();
        List<GameServerInfo> allGs = ServerCfgDb.getGameServerInfoList();
        for (int i = 0; i < allGs.size(); ++i) {
            final GameServerInfo gsInfo = allGs.get(i);
            As.GameServerInfo.Builder cag = As.GameServerInfo.newBuilder();
            cag.setServerId(gsInfo.getId());
            cag.setName(gsInfo.getName());
            cag.setMaintainEndTime((int) (gsInfo.getMaintainEndTime().toEpochMilli() / 1000));
            cag.setIp(gsInfo.getIp());
            cag.setPort(gsInfo.getPort());
            cag.setCreateTime((int) (gsInfo.getCreateTime().toEpochMilli() / 1000));
            cag.setTag(0);
            cag.setSsIp(gsInfo.getSsIp());
            cag.setSsPort(gsInfo.getSsPort());
            if(AccountServer.gsIdToChannelId.containsKey(gsInfo.getId()))
                cag.setAvailable(true);
            else
                cag.setAvailable(false);
            try {
                for(RoleBriefInfo briefInfo : AccountDb.getRoleBriefInfos(this.accountName, gsInfo.getGameDbUrl())) {
                    cag.addBriefInfoBuilder()
                            .setId(Util.toByteString(briefInfo.id))
                            .setName(briefInfo.name)
                            .setLastLoginTime(briefInfo.lastLoginTs);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            ca.addInfos(cag);
        }
        return ca.build();
    }
}
