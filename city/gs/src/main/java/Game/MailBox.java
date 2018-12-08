package Game;

import Shared.Package;
import gscode.GsCode;

import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

public class MailBox {
    private static MailBox instance = new MailBox();
    public static MailBox instance() {
        return instance;
    }
    protected MailBox() {}

    void sendMail(int type, UUID playerId) {
        Mail mail = new Mail(type, playerId);
        sendMailImpl(playerId, mail);
    }

    private void sendMailImpl(UUID playerId, Mail mail) {
        GameDb.saveOrUpdate(mail);
        GameServer.sendTo(Arrays.asList(playerId), Package.create(GsCode.OpCode.newMailInform_VALUE, mail.toProto()));
    }

    void sendMail(int type, UUID playerId, int[] paras) {
        Mail mail = new Mail(type, playerId, paras);
        sendMailImpl(playerId, mail);
    }

    Collection<Mail> getAllMails(UUID playerId) {
        return GameDb.getMail(playerId);
    }
}
