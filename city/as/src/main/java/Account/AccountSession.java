package Account;

import Shared.*;
import Shared.Package;
import as.As;
import com.google.protobuf.Message;
import com.yunpian.sdk.model.Result;
import com.yunpian.sdk.model.SmsSingleSend;
import common.Common;
import ga.Ga;
import gacode.GaCode;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import org.apache.log4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class AccountSession {
    private ChannelHandlerContext ctx;
    private static final Logger logger = Logger.getLogger(AccountSession.class);
    private boolean valid = false;
    private AccountInfo accInfo;
    private String accountName = "";
    private String authCode = "";
    private boolean canModifyPwd = false;
    private static ConcurrentHashMap<ChannelId, Long> channelInterval = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, Long> numberInterval = new ConcurrentHashMap<>();
    public boolean valid() {
        return valid;
    }

    public void logout() {
        AccountServer.clientAccountToChannelId.remove(this.accountName());
        channelInterval.remove(ctx.channel().id());
        if (GlobalConfig.product()) {
            //Validator.getInstance().unRegist(accountName, validateCode);
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

    public void modifyPwd(short cmd, Message message)
    {
        if (!this.canModifyPwd)
        {
            logger.fatal(accountName +" : modify pwd illegal");
            return;
        }
        String pwd = ((As.String) message).getCode();
        if (!validPwd(pwd))
        {
            return;
        }
        String md5Pwd = AccountServer.getMd5Str(pwd);
        AccountDb.modifyPwd(accountName, md5Pwd);
        this.write(Package.create(cmd));
    }

    private boolean validPwd(String pwd)
    {
        return true;
    }

    public void cancleModefyPwd(short cmd)
    {
        this.canModifyPwd = false;
    }

    public void modifyPwdVerify(short cmd, Message message)
    {
        As.VerifyInfo param = (As.VerifyInfo) message;
        String accountName = param.getPhoneNumber();
        String authCode = param.getAuthCode();
        if (!AccountDb.accountExist(accountName))
        {
            this.write(Package.create(cmd,
                    As.VerifyStatus.newBuilder()
                            .setStatus(As.VerifyStatus.Status.FAIL_ACCOUNT_UNREGISTER)
                            .build()));
            return;
        }
        if (!this.accountName.equals(accountName) || !this.authCode.equals(authCode))
        {
            this.write(Package.create(cmd,
                    As.VerifyStatus.newBuilder()
                            .setStatus(As.VerifyStatus.Status.FAIL_AUTHCODE_ERROR)
                            .build()));
            return;
        }
        this.canModifyPwd = true;
        this.write(Package.create(cmd,
                As.VerifyStatus.newBuilder()
                        .setStatus(As.VerifyStatus.Status.SUCCESS)
                        .build()));
    }

    public void createAccount(short cmd, Message message)
    {
        As.RegistAccount registAccount = (As.RegistAccount) message;
        String invitationCode = registAccount.getInvitationCode();
        String authCode = registAccount.getAuthCode();
        String account = registAccount.getPhoneNumber();
        String password = registAccount.getPassword();
        if (!validPwd(password))
        {
            return;
        }
        As.CreateResult.Builder builder = As.CreateResult.newBuilder();
        if (AccountDb.accountExist(account))
        {
            builder.setStatus(As.CreateResult.Status.FAIL_ACCOUNT_EXIST);
        }
        else if (!this.authCode.equals(authCode) || !this.accountName.equals(account))
        {
            builder.setStatus(As.CreateResult.Status.FAIL_AUTHCODE_ERROR);
        }
        else
        {
            String md5Pwd = AccountServer.getMd5Str(password);
            As.CreateResult.Status status = AccountDb.createAccount(account, md5Pwd, invitationCode);
            builder.setStatus(status);
            if (status == As.CreateResult.Status.SUCCESS)
            {
                this.authCode = "";
            }
        }
        this.write(Package.create(cmd, builder.build()));
    }



    public void getAuthCode(short cmd, Message message)
    {
        String phoneNumber = ((As.String) message).getCode();
        long interval = 49000;
        long now = System.currentTimeMillis();
        long oldSendTime = channelInterval.getOrDefault(ctx.channel().id(), 0L);
        long oldSendTime1 = numberInterval.getOrDefault(phoneNumber, 0L);
        if (now - oldSendTime < interval || now - oldSendTime1 < interval)
        {
            this.write(Package.fail(cmd,Common.Fail.Reason.highFrequency));
            return;
        }
        String authCode = YunSmsManager.numberAuthCode();
        Result<SmsSingleSend> result = YunSmsManager.getInstance().sendAuthCode(phoneNumber, authCode);
        if (result.getCode() == 0)
        {
            channelInterval.put(ctx.channel().id(), now);
            numberInterval.put(phoneNumber, now);
            this.authCode = authCode;
            this.accountName = phoneNumber;
            this.write(Package.create(cmd));
        }
        else
        {
            logger.error(result.toString());
            if (result.getCode() == 2) {
                this.write(Package.fail(cmd,Common.Fail.Reason.paramError));
            }
        }
    }

    public void verificationInvitationCode(short cmd, Message message)
    {
        As.CodeStatus status = AccountDb.invitationCardUseful(((As.String) message).getCode());
        this.write(Package.create(cmd,
                As.InvitationCodeStatus.newBuilder()
                        .setStatus(status)
                        .build()));
    }

    public void login(short cmd, Message message) {
        if (!valid()) {
            As.Login c = (As.Login)message;
            accountName = c.getAccount();
            authCode = "";
            String pwd = c.getPwd();
            accInfo = AccountDb.get(accountName);
            if (accInfo == null)
            {
                this.write(Package.create(cmd,As.LoginStatus.newBuilder()
                        .setStatus(As.LoginStatus.Status.FAIL_ACCOUNT_UNREGISTER).build()));
                return;
            }
            else if (!accInfo.getMd5Pwd().equals(AccountServer.getMd5Str(pwd)))
            {
                this.write(Package.create(cmd,As.LoginStatus.newBuilder()
                        .setStatus(As.LoginStatus.Status.FAIL_ERROR).build()));
                return;
            }
            if (accInfo.freezeTime.isAfter(Instant.now()))
            {
                this.write(Shared.Package.fail(cmd, Common.Fail.Reason.accountInFreeze));
                return;
            }

            this.write(Shared.Package.create(cmd,As.LoginStatus.newBuilder()
                    .setStatus(As.LoginStatus.Status.SUCCESS).build()));
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
//            try {
//                for(RoleBriefInfo briefInfo : AccountDb.getRoleBriefInfos(this.accountName, gsInfo.getGameDbUrl())) {
//                    cag.addBriefInfoBuilder()
//                            .setId(Util.toByteString(briefInfo.id))
//                            .setName(briefInfo.name)
//                            .setLastLoginTime(briefInfo.lastLoginTs);
//                }
//            } catch (SQLException e) {
//                e.printStackTrace();
//            }
            ca.addInfos(cag);
        }
        return ca.build();
    }
}
