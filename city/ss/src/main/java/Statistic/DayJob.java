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

import static Statistic.PerHourJob.BUYGROUND_ID;
import static Statistic.PerHourJob.RENTGROUND_ID;
import static Statistic.SummaryUtil.DAY_MILLISECOND;

public class DayJob implements org.quartz.Job {
    private static final Logger LOGGER = Logger.getLogger(DayJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException
    {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS");
        //refuse all client request
        StatisticSession.setIsReady(false);

        long todayStartTime = SummaryUtil.todayStartTime(System.currentTimeMillis());

        long yestodayStartTime = todayStartTime - DAY_MILLISECOND;


        long nowTime = System.currentTimeMillis();
        String timeStr = formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(nowTime), ZoneId.systemDefault()));
        LOGGER.debug("DayJob start execute,time = " + timeStr);

        //summary info to special collection once a day
        //save sell ground income
        List<Document> documentList = LogDb.daySummary1(yestodayStartTime, todayStartTime,LogDb.getBuyGround(),true);
        SummaryUtil.insertDaySummary1(SummaryUtil.Type.INCOME, documentList, yestodayStartTime, SummaryUtil.getDaySellGround());
        //buy ground pay
        documentList = LogDb.daySummary1(yestodayStartTime, todayStartTime, LogDb.getBuyGround(),false);
        SummaryUtil.insertDaySummary1(SummaryUtil.Type.PAY, documentList, yestodayStartTime, SummaryUtil.getDaySellGround());

        //rent ground income
        documentList = LogDb.daySummary1(yestodayStartTime, todayStartTime, LogDb.getRentGround(),true);
        SummaryUtil.insertDaySummary1(SummaryUtil.Type.INCOME, documentList, yestodayStartTime, SummaryUtil.getDayRentGround());
        //rent ground pay
        documentList = LogDb.daySummary1(yestodayStartTime, todayStartTime, LogDb.getRentGround(),false);
        SummaryUtil.insertDaySummary1(SummaryUtil.Type.PAY, documentList, yestodayStartTime, SummaryUtil.getDayRentGround());

        //transfer pay
        documentList = LogDb.daySummary1(yestodayStartTime, todayStartTime, LogDb.getPayTransfer(),false);
        SummaryUtil.insertDaySummary1(SummaryUtil.Type.PAY, documentList, yestodayStartTime, SummaryUtil.getDayTransfer());

        //salary pay
        documentList = LogDb.daySummary1(yestodayStartTime, todayStartTime, LogDb.getPaySalary(),false);
        SummaryUtil.insertDaySummary1(SummaryUtil.Type.PAY, documentList, yestodayStartTime, SummaryUtil.getDaySalary());

        //rent room income
        documentList = LogDb.daySummaryRoomRent(yestodayStartTime, todayStartTime);
        SummaryUtil.insertDaySummary1(SummaryUtil.Type.INCOME, documentList, yestodayStartTime, SummaryUtil.getDayRentRoom());

        //Goods Shelf income (contain npc shopping)
        documentList = LogDb.daySummaryShelf(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf(),true,true);
        SummaryUtil.insertDaySummaryWithTypeId(SummaryUtil.Type.INCOME, documentList, yestodayStartTime, SummaryUtil.getDayGoods());
        documentList = LogDb.daySummaryShelf(yestodayStartTime, todayStartTime, LogDb.getNpcBuyInShelf(), true, true);
        SummaryUtil.insertDaySummaryWithTypeId(SummaryUtil.Type.INCOME, documentList, yestodayStartTime, SummaryUtil.getDayGoods());

        //Goods Shelf pay
        documentList = LogDb.daySummaryShelf(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf(), true,false);
        SummaryUtil.insertDaySummaryWithTypeId(SummaryUtil.Type.PAY, documentList, yestodayStartTime, SummaryUtil.getDayGoods());

        //Goods Sold Num Amount
        documentList=documentList = LogDb.getDayGoodsSoldDetail(yestodayStartTime, todayStartTime, LogDb.getNpcBuyInShelf());
        SummaryUtil.insertNpcHistoryData(documentList, yestodayStartTime, SummaryUtil.getDayGoodsSoldDetail());

        //material Shelf income
        documentList = LogDb.daySummaryShelf(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf(), false,true);
        SummaryUtil.insertDaySummaryWithTypeId(SummaryUtil.Type.INCOME, documentList,yestodayStartTime,SummaryUtil.getDayMaterial());
        documentList = LogDb.daySummaryShelf(yestodayStartTime, todayStartTime, LogDb.getNpcBuyInShelf(), false,true);
        SummaryUtil.insertDaySummaryWithTypeId(SummaryUtil.Type.INCOME, documentList,yestodayStartTime,SummaryUtil.getDayMaterial());

        //material Shelf pay
        documentList = LogDb.daySummaryShelf(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf(), false,false);
        SummaryUtil.insertDaySummaryWithTypeId(SummaryUtil.Type.PAY, documentList, yestodayStartTime, SummaryUtil.getDayMaterial());

        // --player exchange info
        //buy ground
        documentList = LogDb.dayPlayerExchange1(yestodayStartTime, todayStartTime, LogDb.getBuyGround(), BUYGROUND_ID);
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYDAY, SummaryUtil.ExchangeType.GROUND, documentList, yestodayStartTime, SummaryUtil.getPlayerExchangeAmount());
        //rent ground
        documentList = LogDb.dayPlayerExchange1(yestodayStartTime, todayStartTime, LogDb.getRentGround(), RENTGROUND_ID);
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYDAY, SummaryUtil.ExchangeType.GROUND, documentList, yestodayStartTime, SummaryUtil.getPlayerExchangeAmount());
        //buy goods in Shelf
        documentList = LogDb.dayPlayerExchange2(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf(), true);
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYDAY, SummaryUtil.ExchangeType.GOODS, documentList, yestodayStartTime, SummaryUtil.getPlayerExchangeAmount());
        //buy material in Shelf
        documentList = LogDb.dayPlayerExchange2(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf(), false);
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYDAY, SummaryUtil.ExchangeType.MATERIAL, documentList, yestodayStartTime, SummaryUtil.getPlayerExchangeAmount());
        // PublicFacility Promotion buildingOrGoods
        documentList = LogDb.hourPromotionRecord(yestodayStartTime, todayStartTime, LogDb.getPromotionRecord());
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYDAY, SummaryUtil.ExchangeType.PUBLICITY, documentList, yestodayStartTime, SummaryUtil.getPlayerExchangeAmount());
        // Laboratory  research EvapointOrinvent
        documentList = LogDb.hourLaboratoryRecord(yestodayStartTime, todayStartTime, LogDb.getLaboratoryRecord());
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYDAY, SummaryUtil.ExchangeType.LABORATORY, documentList, yestodayStartTime, SummaryUtil.getPlayerExchangeAmount());

        //all types of buildings a day income
        //material factory and  produce factory
        documentList = LogDb.daySummaryHistoryIncome(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf());
        SummaryUtil.insertDayIndustryIncomeData(null, documentList, yestodayStartTime, SummaryUtil.getDayIndustryIncome());
        //retailshop
        documentList = LogDb.daySummaryHistoryIncome(yestodayStartTime, todayStartTime, LogDb.getNpcBuyInShelf());
        SummaryUtil.insertDayIndustryIncomeData(SummaryUtil.BuildingType.RETAIL, documentList, yestodayStartTime, SummaryUtil.getDayIndustryIncome());
        //apartment
        documentList = LogDb.daySummaryHistoryIncome(yestodayStartTime, todayStartTime, LogDb.getNpcRentApartment());
        SummaryUtil.insertDayIndustryIncomeData(SummaryUtil.BuildingType.APARTMENT, documentList, yestodayStartTime, SummaryUtil.getDayIndustryIncome());
        //...研究所和广告公司

        //accept all client request
        StatisticSession.setIsReady(true);
        long nowTime1 = System.currentTimeMillis();
        timeStr = formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(nowTime1), ZoneId.systemDefault()));
        LOGGER.debug(MessageFormat.format("DayJob end execute, time = {0}, consume = {1} ms",
                timeStr, nowTime1 - nowTime));
    }
}
