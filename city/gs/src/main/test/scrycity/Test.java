package scrycity;

import Game.GameDb;
import Game.Mail;
import Game.MailBox;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaGood;
import Shared.LogDb;
import com.mongodb.Block;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

import java.text.DecimalFormat;
import java.util.*;

public class Test {
    @org.junit.Test
    public void queryPlayerInfo()
    {
//        DecimalFormat df = new DecimalFormat("#.00");
        LogDb.init("mongodb://192.168.0.51:27017", "cityLiuyi");
//        LogDb.buyGround(UUID.randomUUID(), UUID.randomUUID(), 1000l, new ArrayList<>());
        MongoCollection<Document> buyGround = LogDb.getBuyGround();
        FindIterable<Document> documents = buyGround.find();
        documents.forEach((Block<? super Document>) document ->{
            System.out.println(document.getInteger("size").longValue());
        });

    }


}
