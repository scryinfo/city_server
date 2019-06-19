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

import static Statistic.SummaryUtil.DAY_MILLISECOND;
import static Statistic.SummaryUtil.HOUR_MILLISECOND;

public class PerHourJob implements org.quartz.Job {
    private static final Logger LOGGER = Logger.getLogger(PerHourJob.class);
    //基础数据表中没有id，给客户端指定id
    public final static int BUYGROUND_ID = 999;
    public final static int RENTGROUND_ID = 888;
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS");
        StatisticSession.setIsReady(false);

        long startTime = SummaryUtil.hourStartTime(System.currentTimeMillis());
        long endTime = startTime - HOUR_MILLISECOND;
        long nowTime = System.currentTimeMillis();
        String timeStr = formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(nowTime), ZoneId.systemDefault()));
        LOGGER.debug("PerHourJob start execute,time = " + timeStr);

        //每种商品购买的npc人数,每小时统计一次
        //并把统计结果保存到数据库
        List<Document> documentList = LogDb.dayNpcGoodsNum(startTime, endTime, LogDb.getNpcBuyInShelf());
        SummaryUtil.insertHistoryData(SummaryUtil.CountType.BYHOUR, documentList, startTime, SummaryUtil.getDayGoodsNpcNum());

        // --player exchange info
        //buy ground
        documentList = LogDb.dayPlayerExchange1(endTime, startTime, LogDb.getBuyGround(), BUYGROUND_ID);
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYHOUR, SummaryUtil.ExchangeType.GROUND, documentList, startTime, SummaryUtil.getPlayerExchangeAmount());
        //rent ground
        documentList = LogDb.dayPlayerExchange1(endTime, startTime, LogDb.getRentGround(), RENTGROUND_ID);
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYHOUR, SummaryUtil.ExchangeType.GROUND, documentList, startTime, SummaryUtil.getPlayerExchangeAmount());
        //buy goods in Shelf
        documentList = LogDb.dayPlayerExchange2(endTime, startTime, LogDb.getBuyInShelf(), true);
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYHOUR, SummaryUtil.ExchangeType.GOODS, documentList, startTime, SummaryUtil.getPlayerExchangeAmount());
        //buy material in Shelf
        documentList = LogDb.dayPlayerExchange2(endTime, startTime, LogDb.getBuyInShelf(), false);
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYHOUR, SummaryUtil.ExchangeType.MATERIAL, documentList, startTime, SummaryUtil.getPlayerExchangeAmount());
        // PublicFacility Promotion buildingOrGoods
        documentList = LogDb.hourPromotionRecord(endTime, startTime, LogDb.getPromotionRecord());
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYHOUR, SummaryUtil.ExchangeType.PUBLICITY, documentList, startTime, SummaryUtil.getPlayerExchangeAmount());
        // Laboratory  research EvapointOrinvent
        documentList = LogDb.hourLaboratoryRecord(endTime, startTime, LogDb.getLaboratoryRecord());
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYHOUR, SummaryUtil.ExchangeType.LABORATORY, documentList, startTime, SummaryUtil.getPlayerExchangeAmount());


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
