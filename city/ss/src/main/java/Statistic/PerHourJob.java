package Statistic;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.log4j.Logger;
import org.bson.Document;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import Shared.LogDb;

public class PerHourJob implements org.quartz.Job {
    private static final Logger LOGGER = Logger.getLogger(PerHourJob.class);
    //基础数据表中没有id，给客户端指定id
    public final static int BUYGROUND_ID = 999;
    public final static int RENTGROUND_ID = 888;
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException
    {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS");
        StatisticSession.setIsReady(false);
        long time = System.currentTimeMillis();
        long endTime = time - time%(1000 * 60  * 60); //结束时间为当前时间-1小时
        long startTime = endTime - 1000 * 60 * 60;


        long nowTime = System.currentTimeMillis();
        String timeStr = formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(nowTime), ZoneId.systemDefault()));
        LOGGER.debug("PerHourJob start execute,time = " + timeStr);

        //每种商品购买的npc人数,每小时统计一次
        //并把统计结果保存到数据库
        List<Document> documentList = LogDb.dayNpcGoodsNum(startTime, endTime, LogDb.getNpcBuyInShelf());
        SummaryUtil.insertHistoryData(SummaryUtil.CountType.BYHOUR, documentList, startTime, SummaryUtil.getDayGoodsNpcNum());



        //buy ground
        documentList = LogDb.dayPlyaerExchange1(startTime, endTime, LogDb.getBuyGround(),BUYGROUND_ID);
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYHOUR, SummaryUtil.ExchangeType.GROUND, documentList, endTime, SummaryUtil.getPlayerExchangeAmount());
        //rent ground
        documentList = LogDb.dayPlyaerExchange1(startTime, endTime, LogDb.getRentGround(),RENTGROUND_ID);
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYHOUR, SummaryUtil.ExchangeType.GROUND, documentList, endTime, SummaryUtil.getPlayerExchangeAmount());

        //buy goods in Shelf
        documentList = LogDb.dayPlyaerExchange2(startTime, endTime, LogDb.getBuyInShelf(), true);
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYHOUR, SummaryUtil.ExchangeType.GOODS, documentList, endTime, SummaryUtil.getPlayerExchangeAmount());

        //buy material in Shelf
        documentList = LogDb.dayPlyaerExchange2(startTime, endTime, LogDb.getBuyInShelf(), false);
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYHOUR, SummaryUtil.ExchangeType.MATERIAL, documentList, endTime, SummaryUtil.getPlayerExchangeAmount());

        //player income 由每小时统计变为每分钟统计
    /*    documentList = LogDb.dayPlayerIncomeOrPay(startTime, endTime, LogDb.getPlayerIncome());
        SummaryUtil.insertPlayerIncomeOrPay(documentList, startTime, SummaryUtil.getDayPlayerIncome());
        //player pay
        documentList = LogDb.dayPlayerIncomeOrPay(startTime, endTime, LogDb.getPlayerPay());
        SummaryUtil.insertPlayerIncomeOrPay(documentList, startTime, SummaryUtil.getDayPlayerPay());
    */
        //统计耗时
        StatisticSession.setIsReady(true);
        long nowTime1 = System.currentTimeMillis();
        timeStr = formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(nowTime1), ZoneId.systemDefault()));
        LOGGER.debug(MessageFormat.format("PerHourJob end execute, time = {0}, consume = {1} ms",
                timeStr, nowTime1 - nowTime));

    }
}
