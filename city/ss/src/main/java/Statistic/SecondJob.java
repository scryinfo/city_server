package Statistic;

import static Statistic.PerHourJob.BUYGROUND_ID;
import static Statistic.PerHourJob.RENTGROUND_ID;
import static Statistic.SummaryUtil.SECOND_MILLISECOND;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.bson.Document;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import Shared.LogDb;

public class SecondJob implements org.quartz.Job {
    private static final Logger LOGGER = Logger.getLogger(SecondJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS");
        StatisticSession.setIsReady(false);
        long startTime = SummaryUtil.secondStartTime(System.currentTimeMillis());
        long endTime = startTime - SECOND_MILLISECOND;

        long nowTime = System.currentTimeMillis();
        String timeStr = formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(nowTime), ZoneId.systemDefault()));
        LOGGER.debug("SecondJob start execute,time = " + timeStr);

        //每种商品购买的npc人数,每10秒统计一次
        //并把统计结果保存到数据库
        List<Document> documentList = LogDb.dayNpcGoodsNum(startTime, endTime, LogDb.getNpcBuyInShelf());
        SummaryUtil.insertHistoryData(SummaryUtil.CountType.BYSECONDS, documentList, endTime, SummaryUtil.getDayGoodsNpcNum());


        //buy ground
        documentList = LogDb.dayPlayerExchange1(endTime,startTime,LogDb.getBuyGround(), BUYGROUND_ID);
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYSECONDS, SummaryUtil.ExchangeType.GROUND, documentList, startTime, SummaryUtil.getPlayerExchangeAmount());
        //rent ground
        documentList = LogDb.dayPlayerExchange1(endTime, startTime, LogDb.getRentGround(), RENTGROUND_ID);
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYSECONDS, SummaryUtil.ExchangeType.GROUND, documentList, startTime, SummaryUtil.getPlayerExchangeAmount());

        //buy goods in Shelf
        documentList = LogDb.dayPlayerExchange2(endTime, startTime, LogDb.getBuyInShelf(), true);
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYSECONDS, SummaryUtil.ExchangeType.GOODS, documentList, startTime, SummaryUtil.getPlayerExchangeAmount());

        //buy material in Shelf
        documentList = LogDb.dayPlayerExchange2(endTime, startTime, LogDb.getBuyInShelf(), false);
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYSECONDS, SummaryUtil.ExchangeType.MATERIAL, documentList, startTime, SummaryUtil.getPlayerExchangeAmount());

        // PublicFacility Promotion buildingOrGoods
        documentList = LogDb.hourPromotionRecord(endTime, startTime, LogDb.getPromotionRecord());
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYSECONDS, SummaryUtil.ExchangeType.PUBLICITY, documentList, startTime, SummaryUtil.getPlayerExchangeAmount());

        // Laboratory  research EvapointOrinvent
        documentList = LogDb.hourLaboratoryRecord(endTime, startTime, LogDb.getLaboratoryRecord());
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYSECONDS, SummaryUtil.ExchangeType.LABORATORY, documentList, startTime, SummaryUtil.getPlayerExchangeAmount());

        //统计耗时
        StatisticSession.setIsReady(true);
        long nowcurrTime = System.currentTimeMillis();
        timeStr = formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(nowcurrTime), ZoneId.systemDefault()));
        LOGGER.debug(MessageFormat.format("SecondJob end execute, time = {0}, consume = {1} ms",
                timeStr, nowcurrTime - nowTime));
    }
}
