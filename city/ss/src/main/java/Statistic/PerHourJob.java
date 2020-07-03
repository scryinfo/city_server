package Statistic;

import Shared.LogDb;
import org.apache.log4j.Logger;
import org.bson.Document;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static Statistic.SummaryUtil.HOUR_MILLISECOND;

public class PerHourJob implements org.quartz.Job {
    private static final Logger LOGGER = Logger.getLogger(PerHourJob.class);
    //There is no id in the basic data table, specify the id to the client
    public final static int BUYGROUND_ID = 999;
    public final static int RENTGROUND_ID = 888;
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS");
        StatisticSession.setIsReady(false);

        long endTime = SummaryUtil.hourStartTime(System.currentTimeMillis());
        long startTime = endTime - HOUR_MILLISECOND;
        long nowTime = System.currentTimeMillis();
        String timeStr = formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(nowTime), ZoneId.systemDefault()));
        LOGGER.debug("PerHourJob start execute,time = " + timeStr);

        //The number of npc purchased for each product is counted every hour
        //And save the statistical results to the database
        List<Document> documentList = LogDb.dayNpcGoodsNum(startTime, endTime, LogDb.getNpcBuyInShelf());
        SummaryUtil.insertHistoryData(SummaryUtil.CountType.BYHOUR, documentList, endTime, SummaryUtil.getDayGoodsNpcNum());
        //apartment transaction
        documentList = LogDb.dayApartmentNpcNum(startTime, endTime, LogDb.getNpcRentApartment());
        SummaryUtil.insertHistoryData(SummaryUtil.CountType.BYHOUR, documentList, endTime, SummaryUtil.getDayApartmentNpcNum());

        // --player exchange info
        //buy ground
        documentList = LogDb.dayPlayerExchange1(startTime, endTime, LogDb.getBuyGround(), BUYGROUND_ID);
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYHOUR, SummaryUtil.ExchangeType.GROUND, documentList, endTime, SummaryUtil.getPlayerExchangeAmount());
        //rent ground
        documentList = LogDb.dayPlayerExchange1(startTime, endTime, LogDb.getRentGround(), RENTGROUND_ID);
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYHOUR, SummaryUtil.ExchangeType.GROUND, documentList, endTime, SummaryUtil.getPlayerExchangeAmount());
        //buy goods in Shelf
        documentList = LogDb.dayPlayerExchange2(startTime, endTime, LogDb.getBuyInShelf(), true);
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYHOUR, SummaryUtil.ExchangeType.GOODS, documentList, endTime, SummaryUtil.getPlayerExchangeAmount());
        //buy material in Shelf
        documentList = LogDb.dayPlayerExchange2(startTime, endTime, LogDb.getBuyInShelf(), false);
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYHOUR, SummaryUtil.ExchangeType.MATERIAL, documentList, endTime, SummaryUtil.getPlayerExchangeAmount());
        // PublicFacility
        documentList = LogDb.hourPromotionRecord(startTime, endTime, LogDb.getBuyInShelf());
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYHOUR, SummaryUtil.ExchangeType.PUBLICITY, documentList, endTime, SummaryUtil.getPlayerExchangeAmount());
        // Laboratory
        documentList = LogDb.hourLaboratoryRecord(startTime, endTime, LogDb.getBuyInShelf());
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYHOUR, SummaryUtil.ExchangeType.LABORATORY, documentList, endTime, SummaryUtil.getPlayerExchangeAmount());


        //player income changes from hourly statistics to minutely statistics
    /*    documentList = LogDb.dayPlayerIncomeOrPay(startTime, endTime, LogDb.getPlayerIncome());
        SummaryUtil.insertPlayerIncomeOrPay(documentList, startTime, SummaryUtil.getDayPlayerIncome());
        //player pay
        documentList = LogDb.dayPlayerIncomeOrPay(startTime, endTime, LogDb.getPlayerPay());
        SummaryUtil.insertPlayerIncomeOrPay(documentList, startTime, SummaryUtil.getDayPlayerPay());



    */
        //Time-consuming statistics
        StatisticSession.setIsReady(true);
        long nowTime1 = System.currentTimeMillis();
        timeStr = formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(nowTime1), ZoneId.systemDefault()));
        LOGGER.debug(MessageFormat.format("PerHourJob end execute, time = {0}, consume = {1} ms",
                timeStr, nowTime1 - nowTime));

    }
}
