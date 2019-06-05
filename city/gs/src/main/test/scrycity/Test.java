package scrycity;

import Game.GameDb;
import Game.Mail;
import Game.MailBox;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaGood;
import Shared.LogDb;
import org.bson.Document;

import java.text.DecimalFormat;
import java.util.*;

public class Test {
    @org.junit.Test
    public void queryPlayerInfo()
    {
//        DecimalFormat df = new DecimalFormat("#.00");
        LogDb.init("mongodb://192.168.0.51:27017", "cityLiuyi");
////        List<Document> documents = LogDb.queryHourMaterialInfo(new Date().getTime(), System.currentTimeMillis(), LogDb.getBuyInShelf());
////        System.out.println(documents);
//        List<Document> list = LogDb.dayPlayerExchange2(new Date().getTime(), System.currentTimeMillis(), LogDb.getBuyInShelf(), false);
//        System.out.println(list);

//        int category = MetaGood.category(2252203);
//        System.out.println(category);
//
//        int type = MetaBuilding.type(1400003);
//        System.out.println(type);

//        LogDb.promotionRecord(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 6, 66, 2251203, 51, false);
//        LogDb.promotionRecord(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 9, 99, 1400003, 14, true);
        long playerAmount = GameDb.getPlayerAmount();
        System.out.println(playerAmount);



    }

    @org.junit.Test
    public void query() {

    }
}
