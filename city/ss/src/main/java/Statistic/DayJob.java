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

import static Statistic.SummaryUtil.DAY_MILLISECOND;

public class DayJob implements org.quartz.Job {
    private static final Logger LOGGER = Logger.getLogger(DayJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException
    {
        //refuse all client request
        StatisticSession.setIsReady(false);

        long todayStartTime = SummaryUtil.todayStartTime();
        long yestodayStartTime = todayStartTime - DAY_MILLISECOND;

        //for test
        /*long yestodayStartTime = todayStartTime;
        todayStartTime = todayStartTime + DAY_MILLISECOND;*/

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS");
        long nowTime = System.currentTimeMillis();
        String timeStr = formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(nowTime), ZoneId.systemDefault()));
        LOGGER.debug("DayJob start execute,time = " + timeStr);

        //summary info to special collection once a day
        //save sell ground income
        List<Document> documentList = LogDb.daySummary1(yestodayStartTime, todayStartTime, LogDb.getIncomeBuyGround());
        SummaryUtil.insertDaySummary1(SummaryUtil.Type.INCOME, documentList, yestodayStartTime, SummaryUtil.getDaySellGround());
        //buy ground pay
        documentList = LogDb.daySummary1(yestodayStartTime, todayStartTime, LogDb.getBuyGround());
        SummaryUtil.insertDaySummary1(SummaryUtil.Type.PAY, documentList, yestodayStartTime, SummaryUtil.getDaySellGround());

        //rent ground income
        documentList = LogDb.daySummary1(yestodayStartTime, todayStartTime, LogDb.getIncomeRentGround());
        SummaryUtil.insertDaySummary1(SummaryUtil.Type.INCOME, documentList, yestodayStartTime, SummaryUtil.getDayRentGround());
        //rent ground pay
        documentList = LogDb.daySummary1(yestodayStartTime, todayStartTime, LogDb.getRentGround());
        SummaryUtil.insertDaySummary1(SummaryUtil.Type.PAY, documentList, yestodayStartTime, SummaryUtil.getDayRentGround());

        //transfer pay
        documentList = LogDb.daySummary1(yestodayStartTime, todayStartTime, LogDb.getPayTransfer());
        SummaryUtil.insertDaySummary1(SummaryUtil.Type.PAY, documentList, yestodayStartTime, SummaryUtil.getDayTransfer());

        //salary pay
        documentList = LogDb.daySummary1(yestodayStartTime, todayStartTime, LogDb.getPaySalary());
        SummaryUtil.insertDaySummary1(SummaryUtil.Type.PAY, documentList, yestodayStartTime, SummaryUtil.getDaySalary());

        //rent room income
        documentList = LogDb.daySummaryRoomRent(yestodayStartTime, todayStartTime);
        SummaryUtil.insertDaySummary1(SummaryUtil.Type.INCOME, documentList, yestodayStartTime, SummaryUtil.getDayRentRoom());

        //Goods Shelf income (contain npc shopping)
        documentList = LogDb.daySummaryShelf(yestodayStartTime, todayStartTime, LogDb.getIncomeInShelf(),true);
        SummaryUtil.insertDaySummaryWithTypeId(SummaryUtil.Type.INCOME, documentList,yestodayStartTime,SummaryUtil.getDayGoods());
        //Goods Shelf pay
        documentList = LogDb.daySummaryShelf(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf(), true);
        SummaryUtil.insertDaySummaryWithTypeId(SummaryUtil.Type.PAY, documentList, yestodayStartTime, SummaryUtil.getDayGoods());

        //material Shelf income
        documentList = LogDb.daySummaryShelf(yestodayStartTime, todayStartTime, LogDb.getIncomeInShelf(), false);
        SummaryUtil.insertDaySummaryWithTypeId(SummaryUtil.Type.INCOME, documentList,yestodayStartTime,SummaryUtil.getDayMaterial());

        //material Shelf pay
        documentList = LogDb.daySummaryShelf(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf(), false);
        SummaryUtil.insertDaySummaryWithTypeId(SummaryUtil.Type.PAY, documentList, yestodayStartTime, SummaryUtil.getDayMaterial());

        //accept all client request
        StatisticSession.setIsReady(true);
        long nowTime1 = System.currentTimeMillis();
        timeStr = formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(nowTime1), ZoneId.systemDefault()));
        LOGGER.debug(MessageFormat.format("DayJob end execute, time = {0}, consume = {1} ms",
                timeStr, nowTime1 - nowTime));
    }
}
