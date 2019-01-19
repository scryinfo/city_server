import Shared.LogDb;
import Shared.Util;
import Statistic.DayJob;
import Statistic.SummaryUtil;
import org.quartz.JobExecutionException;
import ss.Ss;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Test
{
    static final String p1 = "228953c5-7da9-4563-8856-166c41dbb19c";
    static final String p2 = "3ab1ad45-9575-4e79-9b96-336b489dfe97";

    @org.junit.Test
    public void getBuidingIncome()
    {
        UUID player1 = UUID.fromString(p1);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS");
        LogDb.init("mongodb://192.168.0.51:27017","city148");
        SummaryUtil.init();
        Map<Long,Long> map = SummaryUtil.getBuildIncomeById(player1);
        map.forEach((k, v) ->{
            System.out.println(formatter.format(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(k), ZoneId.systemDefault()))
                    + " : " + v);
        });
    }
    @org.junit.Test
    public void insertBuidingIncome() throws InterruptedException
    {
        LogDb.init("mongodb://192.168.0.51:27017","city148");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS");
        UUID player1 = UUID.fromString(p1);
        UUID player2 = UUID.fromString(p2);
        long now = System.currentTimeMillis();
        long beforTime = SummaryUtil.getBeforeDayStartTime(2, now);
        long fullTime = SummaryUtil.getLastFullTime(now);
        long i = 0L;
        for (i = fullTime; i >= beforTime - SummaryUtil.HOUR_MILLISECOND;
             i = i - SummaryUtil.HOUR_MILLISECOND)
        {
            //for test , time need add to method
            /*long time =i + new Random().nextInt((int)SummaryUtil.HOUR_MILLISECOND-1);
            LogDb.buildingIncome(player1, UUID.randomUUID(), 10, 0, 0, time);
            LogDb.buildingIncome(player2, UUID.randomUUID(), 10, 0, 0, time);
            time =i + new Random().nextInt((int)SummaryUtil.HOUR_MILLISECOND-1);
            LogDb.buildingIncome(player2, UUID.randomUUID(), 10, 0, 0, time);
            LogDb.buildingIncome(player1, UUID.randomUUID(), 10, 0, 0, time);*/
        }
        System.out.println(formatter.format(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(fullTime), ZoneId.systemDefault())));
        System.out.println(formatter.format(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(beforTime), ZoneId.systemDefault())));
        System.out.println(formatter.format(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(i), ZoneId.systemDefault())));
        TimeUnit.SECONDS.sleep(10);
    }

    @org.junit.Test
    public void queryPlayerInfo()
    {
        LogDb.init("mongodb://192.168.0.51:27017", "city148");
        SummaryUtil.init();
        Ss.EconomyInfos economyInfo = SummaryUtil.getPlayerEconomy(UUID.fromString(p1));
        //debug
        System.out.println("-------------");
    }

    @org.junit.Test
    public void testSummary() throws JobExecutionException, InterruptedException
    {
        LogDb.init("mongodb://192.168.0.51:27017", "city148");
        SummaryUtil.init();
        System.out.println("init success : ");
        new DayJob().execute(null);
        System.err.println("end--------------");
        TimeUnit.SECONDS.sleep(30);
    }
    @org.junit.Test
    public void insertData() throws InterruptedException
    {
        LogDb.init("mongodb://192.168.0.51:27017","city148");

        UUID player1 = UUID.fromString(p1);
        UUID player2 = UUID.fromString(p2);
        System.err.println("wxj-----------------------time=" + System.currentTimeMillis());
        System.err.println("player1 : " + player1.toString());
        System.err.println("player2 : " + player2.toString());
        System.err.println("wxj-----------------------");
        List<LogDb.Positon> list = new ArrayList<>();
        list.add(new LogDb.Positon(1, 1));
        for (int i = 0; i < 3; i++)
        {
            //土地交易
            LogDb.buyGround(player1, UUID.randomUUID(),  3, list);
            LogDb.buyGround(UUID.randomUUID(),player1 ,  4, list);

            LogDb.buyGround(player2, UUID.randomUUID(),  3, list);
            LogDb.buyGround(UUID.randomUUID(), player2,  4, list);

            //土地租赁
            LogDb.rentGround(player1,  UUID.randomUUID(), 3, list);
            LogDb.rentGround(UUID.randomUUID(),  player1, 4, list);

            LogDb.rentGround(player2,UUID.randomUUID(),3,list);
            LogDb.rentGround(UUID.randomUUID(),  player2, 4, list);

            //运费
            LogDb.payTransfer(player1,  3, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1);
            LogDb.payTransfer(player2, 3, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1);
            //工资
            LogDb.paySalary(player1,UUID.randomUUID(),3,1);
            LogDb.paySalary(player2,UUID.randomUUID(),3,1);
            //房租
            LogDb.incomeVisit(player1,  14, 3, UUID.randomUUID(), UUID.randomUUID());
            LogDb.incomeVisit(player2,  14, 3, UUID.randomUUID(), UUID.randomUUID());

            //商品
            LogDb.buyInShelf(player1,player2,1,3,
                    UUID.randomUUID(),UUID.randomUUID(),22,2251101);
            LogDb.buyInShelf(player2,player1,1,3,
                    UUID.randomUUID(),UUID.randomUUID(),22,2251101);

            LogDb.npcBuyInShelf(UUID.randomUUID(),player1,1,4,
                    UUID.randomUUID(),UUID.randomUUID(),22,2251101);
            LogDb.npcBuyInShelf(UUID.randomUUID(),player2,1,4,
                    UUID.randomUUID(),UUID.randomUUID(),22,2251101);

            LogDb.buyInShelf(player1,player2,1,3,
                    UUID.randomUUID(),UUID.randomUUID(),22,2251202);
            LogDb.buyInShelf(player2,player1,1,3,
                    UUID.randomUUID(),UUID.randomUUID(),22,2251202);

            LogDb.npcBuyInShelf(UUID.randomUUID(),player1,1,4,
                    UUID.randomUUID(),UUID.randomUUID(),22,2251203);
            LogDb.npcBuyInShelf(UUID.randomUUID(),player2,1,4,
                    UUID.randomUUID(),UUID.randomUUID(),22,2251203);

            //原料
            LogDb.buyInShelf(player1,player2,1,3,
                    UUID.randomUUID(),UUID.randomUUID(),21,2101001);
            LogDb.buyInShelf(player2,player1,1,3,
                    UUID.randomUUID(),UUID.randomUUID(),21,2101001 );

            LogDb.npcBuyInShelf(UUID.randomUUID(),player1,1,4,
                    UUID.randomUUID(),UUID.randomUUID(),21,2101001);
            LogDb.npcBuyInShelf(UUID.randomUUID(),player2,1,4,
                    UUID.randomUUID(),UUID.randomUUID(),21,2101001);

            LogDb.buyInShelf(player1,player2,1,3,
                    UUID.randomUUID(),UUID.randomUUID(),21,2102001 );
            LogDb.buyInShelf(player2,player1,1,3,
                    UUID.randomUUID(),UUID.randomUUID(),21,2102001);

            LogDb.npcBuyInShelf(UUID.randomUUID(),player1,1,4,
                    UUID.randomUUID(),UUID.randomUUID(),21,2102002);
            LogDb.npcBuyInShelf(UUID.randomUUID(),player2,1,4,
                    UUID.randomUUID(),UUID.randomUUID(),21,2102002);

            System.err.println("end--------------");
            TimeUnit.SECONDS.sleep(5);
        }
    }

}
