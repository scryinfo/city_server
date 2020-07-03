package Statistic;

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

public class YesterdayJob implements org.quartz.Job {
    private static final Logger LOGGER = Logger.getLogger(YesterdayJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException
    {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS");
        StatisticSession.setIsReady(false);

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        
        Date endDate = calendar.getTime();
        long endTime=endDate.getTime();
        
        calendar.add(Calendar.DATE, -1);
        Date startDate = calendar.getTime();
        long startTime=startDate.getTime();

        long nowTime = System.currentTimeMillis();
        String timeStr = formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(nowTime), ZoneId.systemDefault()));
        LOGGER.debug("YesterdayJob start execute,time = " + timeStr);

        //Statistics yesterday include the transaction volume of previous npc purchases
        List<Document> documentList = LogDb.dayYesterdayExchangeAmount(endTime, LogDb.getNpcBuyInShelf());
        SummaryUtil.insertHistoryData(SummaryUtil.CountType.BYDAY, documentList, startTime, SummaryUtil.getDayNpcBuyInShelf());
        
        //Statistics yesterday include previous npc rental transaction volume
        documentList = LogDb.dayYesterdayExchangeAmount(endTime, LogDb.getNpcRentApartment());
        SummaryUtil.insertHistoryData(SummaryUtil.CountType.BYDAY, documentList, startTime, SummaryUtil.getDayNpcRentApartment());
        
        //Statistics yesterday including the previous player's transaction volume of buying land sold by others
        documentList = LogDb.dayYesterdayExchangeAmount(endTime, LogDb.getBuyGround());
        SummaryUtil.insertHistoryData(SummaryUtil.CountType.BYDAY, documentList, startTime, SummaryUtil.getDayPlayerBuyGround());
        
        //Statistics include yesterday’s volume of previous players’ purchases of shelf goods (including new research institute and data company transactions)
        documentList = LogDb.dayYesterdayExchangeAmount(endTime, LogDb.getBuyInShelf());
        SummaryUtil.insertHistoryData(SummaryUtil.CountType.BYDAY, documentList, startTime, SummaryUtil.getDayPlayerBuyInShelf());
        
        //Statistics of yesterday's transaction volume including previous players renting others' land
        documentList = LogDb.dayYesterdayExchangeAmount(endTime, LogDb.getRentGround());
        SummaryUtil.insertHistoryData(SummaryUtil.CountType.BYDAY, documentList, startTime, SummaryUtil.getDayPlayerRentGround());
        //Time-consuming statistics
        StatisticSession.setIsReady(true);
        long nowTime1 = System.currentTimeMillis();
        timeStr = formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(nowTime1), ZoneId.systemDefault()));
        LOGGER.debug(MessageFormat.format("YesterdayJob end execute, time = {0}, consume = {1} ms",
                timeStr, nowTime1 - nowTime));
    }
}
