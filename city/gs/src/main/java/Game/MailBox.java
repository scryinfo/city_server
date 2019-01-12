package Game;

import Game.Timers.PeriodicTimer;
import Shared.Package;
import gscode.GsCode;

import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MailBox {
    private static MailBox instance = new MailBox();

    public static MailBox instance() {
        return instance;
    }

    protected MailBox() {
    }

    void sendMail(int type, UUID playerId) {
        Mail mail = new Mail(type, playerId);
        sendMailImpl(playerId, mail);
    }

    void sendMail(int type, UUID playerId, int[] paras, UUID[] uuidParas, int[] intParasArr) {
        Mail mail = new Mail(type, playerId, paras, uuidParas, intParasArr);
        sendMailImpl(playerId, mail);
    }

    private void sendMailImpl(UUID playerId, Mail mail) {
        GameDb.saveOrUpdate(mail);
        GameServer.sendTo(Arrays.asList(playerId), Package.create(GsCode.OpCode.newMailInform_VALUE, mail.toProto()));
    }

    Collection<Mail> getAllMails(UUID playerId) {
        return GameDb.getMail(playerId);
    }

    void mailRead(UUID mailId) {
        GameDb.mailChangeRead(mailId);
    }

    void deleteMail(UUID mailId) {
        GameDb.delMail(mailId);
    }

    private PeriodicTimer timer = new PeriodicTimer((int) TimeUnit.HOURS.toMillis(2));
    void update(long diffNano) {
        if (this.timer.update(diffNano)) {
            GameDb.delOverdueMail();
        }
    }
}
