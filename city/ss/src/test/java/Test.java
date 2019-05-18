import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import Statistic.Util.TotalUtil;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.quartz.JobExecutionException;

import com.mongodb.Block;
import com.mongodb.client.MongoCollection;

import Shared.LogDb;
import Shared.Util;
import Statistic.DayJob;
import Statistic.SummaryUtil;
import ss.Ss;

public class Test
{
    static final String p1 = "228953c5-7da9-4563-8856-166c41dbb19c";
    static final String p2 = "3ab1ad45-9575-4e79-9b96-336b489dfe97";

    @org.junit.Test
    public void querySexInfo()
    {
        LogDb.init("mongodb://192.168.0.51:27017", "city148");
        SummaryUtil.init();
        System.out.println(SummaryUtil.getSexInfo(true));
        System.out.println(SummaryUtil.getSexInfo(false));
    }

    @org.junit.Test
    public void insertSexInfo() throws InterruptedException
    {
        LogDb.init("mongodb://192.168.0.51:27017","city148");
        UUID player1 = UUID.fromString(p1);
        for (int i = 0; i < 38; i++)
        {
            LogDb.insertPlayerInfo(player1,true);
        }
        for (int i = 0; i < 25; i++)
        {
            LogDb.insertPlayerInfo(player1,false);
        }
        TimeUnit.SECONDS.sleep(5);
    }

    @org.junit.Test
    public void dayNpcGoodsNum()
    {
        LogDb.init("mongodb://192.168.0.51:27017", "city51");
        SummaryUtil.init();
//        //每种商品购买的npc人数,每小时统计一次
//       	long time=1552104000000l;
//    	List<Document> documentList = new ArrayList<>();
//    	MongoCollection<Document> collection=SummaryUtil.getDayGoodsNpcNum();
//        collection.find(and(
//    					eq("time",time),
//    					eq("type",2)
//    					))
//        			.projection(fields(include("time", "total", "id"), excludeId()))
//    		        .forEach((Block<? super Document>) document ->
//                    {   
//                    	documentList.add(document);
//                    });
//        System.out.println(documentList);
    	
    	Map<Long, Map> map=SummaryUtil.getNpcTypeNumHistoryData(LogDb.getNpcTypeNum());
    	if(map!=null&&map.size()>0){
    	  	for (Map.Entry<Long, Map> entry : map.entrySet()) { 
    	  		System.out.println(entry.getKey());
    	 		Map<Integer,Long> m=entry.getValue();
    	  		for (Map.Entry<Integer,Long> e : m.entrySet()) { 
    	  			System.out.println("key--"+e.getKey());
    	  			System.out.println("value--"+e.getValue());
    	  		}
    		}
    	}
    }
    
    @org.junit.Test
    public void getBuidingIncome()
    {
        UUID player1 = UUID.fromString(p1);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS");
        LogDb.init("mongodb://192.168.0.51:27017","city149");
        SummaryUtil.init();
        SummaryUtil.init();
        List<Ss.NodeIncome> list = SummaryUtil.getBuildDayIncomeById(player1);
        list.forEach(i ->{
            System.out.println(formatter.format(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(i.getTime()), ZoneId.systemDefault())) + " : " + i.getIncome());
        });
    }

    @org.junit.Test
    public void testBuildDayJob()
    {
        LogDb.init("mongodb://192.168.0.51:27017", "city149");
        SummaryUtil.init();
        long now = System.currentTimeMillis();
        long beforTime = SummaryUtil.getBeforeDayStartTime(32, now);
        long todayStartTime = SummaryUtil.todayStartTime(now);
        long i = 0L;
        for (i = beforTime; i < todayStartTime;
             i = i + SummaryUtil.DAY_MILLISECOND)
        {
            List<Document> documentList = LogDb.buildingDayIncomeSummary(i, i+SummaryUtil.DAY_MILLISECOND);
            SummaryUtil.insertBuildingDayIncome(documentList,i);
        }

    }
    @org.junit.Test
    public void insertBuidingIncome() throws InterruptedException
    {
        LogDb.init("mongodb://192.168.0.51:27017","city149");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS");
        UUID player1 = UUID.fromString(p1);
        UUID player2 = UUID.fromString(p2);
        long now = System.currentTimeMillis();
        long beforTime = SummaryUtil.getBeforeDayStartTime(32, now);
        long todayStartTime = SummaryUtil.todayStartTime(now);
        long i = 0L;
        for (i = beforTime; i < todayStartTime;
             i = i + SummaryUtil.DAY_MILLISECOND)
        {
            //for test , time need add to method
            long time =i + new Random().nextInt((int)SummaryUtil.DAY_MILLISECOND-1);
            LogDb.buildingIncome(player1, UUID.randomUUID(), 10, 0, 0, time);
            LogDb.buildingIncome(player2, UUID.randomUUID(), 10, 0, 0, time);
            time =i + new Random().nextInt((int)SummaryUtil.DAY_MILLISECOND-1);
            LogDb.buildingIncome(player2, UUID.randomUUID(), 11, 0, 0, time);
            LogDb.buildingIncome(player1, UUID.randomUUID(), 11, 0, 0, time);
        }
        System.out.println(formatter.format(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(todayStartTime), ZoneId.systemDefault())));
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
    @org.junit.Test
   public void testDayIncome(){
        LogDb.init("mongodb://192.168.0.51:27017","cityYetianyi");
        SummaryUtil.init();
        MongoCollection<Document> dayPlayerIncome = SummaryUtil.getDayPlayerPay();
        Map<Long, Document> map = new LinkedHashMap<>();
        dayPlayerIncome
                .find()
                .sort(Sorts.descending("time"))
                .forEach((Block<? super Document>) document ->
        {
            map.put(document.getLong("time"), document);
        });
        for (Map.Entry<Long, Document> entry : map.entrySet()) {
            System.out.println(entry.getKey()+"金额"+entry.getValue());
        }
        //查询统计信息
        Map<Long, Long> map1 = SummaryUtil.queryPlayerIncomePayCurve(SummaryUtil.getDayPlayerIncome(), UUID.fromString("bc7e5815-4dcb-4e5c-b4b2-ca5870ae57ac"));
        System.out.println(map1);
    }

    //统计收入支出信息
    @org.junit.Test
    public void testDayIncomAndPay(){
        LogDb.init("mongodb://192.168.0.51:27017","cityYetianyi");
        SummaryUtil.init();
        UUID id = UUID.fromString("9ba65634-4e0b-4f43-aa1a-8b8b573b822c");
        Map<Long, Long> playerIncomeMap=SummaryUtil.queryPlayerIncomePayCurve(SummaryUtil.getDayPlayerIncome(),id);
        Map<Long, Long> playerPayMap=SummaryUtil.queryPlayerIncomePayCurve(SummaryUtil.getDayPlayerPay(),id);
        //统计整理数据
        Map<Long, Long> monthTotalIncome = TotalUtil.getInstance().monthTotal(playerIncomeMap);
        Map<Long, Long> monthTotalpay = TotalUtil.getInstance().monthTotal(playerPayMap);
    }

    @org.junit.Test
    public void testDayIncomAndPay2(){
        LogDb.init("mongodb://192.168.0.51:27017","cityYetianyi");
        SummaryUtil.init();
        UUID id = UUID.fromString("9ba65634-4e0b-4f43-aa1a-8b8b573b822c");
        Map<Long, Long> playerIncomeMap=SummaryUtil.queryPlayerIncomePayCurve(SummaryUtil.getDayPlayerIncome(),id);
        Map<Long, Long> playerPayMap=SummaryUtil.queryPlayerIncomePayCurve(SummaryUtil.getDayPlayerPay(),id);
        //统计整理数据
        Map<Long, Long> monthTotalIncome = TotalUtil.getInstance().monthTotal(playerIncomeMap);
        Map<Long, Long> monthTotalpay = TotalUtil.getInstance().monthTotal(playerPayMap);

        Ss.PlayerIncomePayCurve.Builder builder=Ss.PlayerIncomePayCurve.newBuilder();
        builder.setId(Util.toByteString(id));
        //收入和支出前面29天，可以按照相同的时间存储，但是最后一天不行
      /*  chooseMap.forEach((k,v)->{
            Ss.PlayerIncomePayCurve.PlayerIncomePay.Builder b=builder.addPlayerIncomeBuilder();
            b.setTime(k);
            b.setIncome(v);
            b.setPay((monthTotalpay!=null&&monthTotalpay.size()>0&&monthTotalpay.get(k)!=null)?playerPayMap.get(k):0);
        });*/
        Map<Long,Ss.PlayerIncomePayCurve.PlayerIncomePay> totalMap = new TreeMap<>();
        //1.处理收入信息
        monthTotalIncome.forEach((k,v)->{
            Ss.PlayerIncomePayCurve.PlayerIncomePay.Builder b = Ss.PlayerIncomePayCurve.PlayerIncomePay.newBuilder();
            b.setTime(k);
            b.setIncome(v);
            b.setPay((monthTotalpay!=null&&monthTotalpay.size()>0&&monthTotalpay.get(k)!=null)?monthTotalpay.get(k):0);
            totalMap.put(k,b.build());
        });

        //2.处理支出信息
        for (Map.Entry<Long, Long> pay : monthTotalpay.entrySet()) {
            Ss.PlayerIncomePayCurve.PlayerIncomePay.Builder b = Ss.PlayerIncomePayCurve.PlayerIncomePay.newBuilder();
            //如果在收入中已经处理了，则跳过
            Long time = pay.getKey();
            if(totalMap.containsKey(time)){
                continue;
            }
            //添加其他的支出信息
            b.setTime(pay.getKey());
            b.setPay(pay.getValue());
            b.setIncome((monthTotalIncome!=null&&monthTotalIncome.size()>0&&monthTotalIncome.get(pay.getKey())!=null)?monthTotalIncome.get(pay.getKey()):0);
            totalMap.put(pay.getKey(),b.build());
        }
        builder.addAllPlayerIncome(totalMap.values());
        //获取今日收入信息
        Long todayIncome = TotalUtil.getInstance().todayIncomOrPay(playerIncomeMap);
        //获取今日支出信息
        Long todayPay = TotalUtil.getInstance().todayIncomOrPay(playerPayMap);
        //还需要处理最后一天的数据
        builder.setTodayIncome(todayIncome);
        builder.setTodayPay(todayPay);

        System.out.println(builder);

    }

}
