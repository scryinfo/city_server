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

        //统计AI均值  零售店、商品、商品大类、奢侈度、住宅
        SummaryUtil.insertAiBaseAvg(yestodayStartTime,todayStartTime, SummaryUtil.getDayAiBaseAvg());

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
        documentList = LogDb.hourPromotionRecord(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf());
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYDAY, SummaryUtil.ExchangeType.PUBLICITY, documentList, yestodayStartTime, SummaryUtil.getPlayerExchangeAmount());
        // Laboratory  research EvapointOrinvent
        documentList = LogDb.hourLaboratoryRecord(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf());
        SummaryUtil.insertPlayerExchangeData(SummaryUtil.CountType.BYDAY, SummaryUtil.ExchangeType.LABORATORY, documentList, yestodayStartTime, SummaryUtil.getPlayerExchangeAmount());

        // all industry income
        //material factory
        documentList = LogDb.daySummaryHistoryIncome(yestodayStartTime, todayStartTime,SummaryUtil.MATERIAL);
        SummaryUtil.insertDayIndustryIncomeData(SummaryUtil.IndustryType.MATERIAL, documentList, yestodayStartTime, SummaryUtil.getDayIndustryIncome());
        // produce factory
        documentList = LogDb.daySummaryHistoryIncome(yestodayStartTime, todayStartTime,SummaryUtil.PRODUCE);
        SummaryUtil.insertDayIndustryIncomeData(SummaryUtil.IndustryType.PRODUCE, documentList, yestodayStartTime, SummaryUtil.getDayIndustryIncome());
        //retailshop
        documentList = LogDb.daySummaryHistoryIncome(yestodayStartTime, todayStartTime, false);
        SummaryUtil.insertDayIndustryIncomeData(SummaryUtil.IndustryType.RETAIL, documentList, yestodayStartTime, SummaryUtil.getDayIndustryIncome());
        //apartment
        documentList = LogDb.daySummaryHistoryIncome(yestodayStartTime, todayStartTime, true);
        SummaryUtil.insertDayIndustryIncomeData(SummaryUtil.IndustryType.APARTMENT, documentList, yestodayStartTime, SummaryUtil.getDayIndustryIncome());
        //promote
        documentList = LogDb.daySummaryHistoryIncome(yestodayStartTime, todayStartTime,LogDb.PROMOTE); // 只会产生一条 document
        SummaryUtil.insertDayIndustryIncomeData(SummaryUtil.IndustryType.PROMOTE, documentList, yestodayStartTime, SummaryUtil.getDayIndustryIncome());
        // technology
        documentList = LogDb.daySummaryHistoryIncome(yestodayStartTime, todayStartTime,LogDb.TECHNOLOGY);
        SummaryUtil.insertDayIndustryIncomeData(SummaryUtil.IndustryType.TECHNOLOGY, documentList, yestodayStartTime, SummaryUtil.getDayIndustryIncome());
        // sellerGround
        documentList = LogDb.daySummaryGroundHistoryIncome(yestodayStartTime, todayStartTime, LogDb.getBuyGround());
        SummaryUtil.insertDayIndustryIncomeData(SummaryUtil.IndustryType.GROUND, documentList, yestodayStartTime, SummaryUtil.getDayIndustryIncome());

        //Average transaction price
        // ground
        documentList = LogDb.transactionPrice(yestodayStartTime, todayStartTime, LogDb.getBuyGround());
        SummaryUtil.insertAverageTransactionprice(SummaryUtil.IndustryType.GROUND, documentList, yestodayStartTime, SummaryUtil.getAverageTransactionPrice());
        // apartment
        documentList = LogDb.transactionPrice(yestodayStartTime, todayStartTime, LogDb.getNpcRentApartment());
        SummaryUtil.insertAverageTransactionprice(SummaryUtil.IndustryType.APARTMENT, documentList, yestodayStartTime, SummaryUtil.getAverageTransactionPrice());
        // material
        documentList = LogDb.transactionPrice(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf(), SummaryUtil.MATERIAL);
        SummaryUtil.insertAverageTransactionprice(SummaryUtil.IndustryType.MATERIAL, documentList, yestodayStartTime, SummaryUtil.getAverageTransactionPrice());
        // produce
        documentList = LogDb.transactionPrice(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf(), SummaryUtil.PRODUCE);
        SummaryUtil.insertAverageTransactionprice(SummaryUtil.IndustryType.PRODUCE, documentList, yestodayStartTime, SummaryUtil.getAverageTransactionPrice());
        //promote
        documentList = LogDb.transactionPrice(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf(), SummaryUtil.PROMOTE);
        SummaryUtil.insertAverageTransactionprice(SummaryUtil.IndustryType.PROMOTE, documentList, yestodayStartTime, SummaryUtil.getAverageTransactionPrice());
        // technology
        documentList = LogDb.transactionPrice(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf(), SummaryUtil.TECHNOLOGY);
        SummaryUtil.insertAverageTransactionprice(SummaryUtil.IndustryType.TECHNOLOGY, documentList, yestodayStartTime, SummaryUtil.getAverageTransactionPrice());
        // retailshop
        documentList = LogDb.retailshopTransactionPrice(yestodayStartTime, todayStartTime, LogDb.getNpcBuyInShelf());
        SummaryUtil.insertAverageTransactionprice(SummaryUtil.IndustryType.RETAIL, documentList, yestodayStartTime, SummaryUtil.getAverageTransactionPrice());

        //玩家当天开业情况
        documentList = LogDb.playerBuildingBusiness(yestodayStartTime, todayStartTime, LogDb.getPlayerBuildingBusiness(),0);
        SummaryUtil.insertPlayerIncomeOrPay(documentList, yestodayStartTime, SummaryUtil.getDayBuildingBusiness());


        //按天统计玩家收入支出
        //player income
        documentList=documentList = LogDb.dayPlayerIncomeOrPay(yestodayStartTime, todayStartTime, LogDb.getPlayerIncome());//统计天的收入量
        SummaryUtil.insertPlayerIncomeOrPay(documentList, yestodayStartTime, SummaryUtil.getDayPlayerIncome());
        //player pay
        documentList = LogDb.dayPlayerIncomeOrPay(yestodayStartTime, todayStartTime, LogDb.getPlayerPay());
        SummaryUtil.insertPlayerIncomeOrPay(documentList, yestodayStartTime, SummaryUtil.getDayPlayerPay());

        /*PlayerLoginTime(玩家每日登录时间统计)  YTY*/
        documentList=LogDb.dayPlayerLoginTime(yestodayStartTime, todayStartTime, LogDb.getPlayerLoginTime());
        SummaryUtil.insertDayPlayerLoginTime(documentList,yestodayStartTime,SummaryUtil.getDayPlayerLoginTime());


        // 城市交易量+商品销售额 (没有土地和住宅)
        documentList = LogDb.queryCityAllTransactionAmount(yestodayStartTime,todayStartTime,LogDb.getPlayerIncome());
        SummaryUtil.insertCityTransactionAmount(documentList, yestodayStartTime, SummaryUtil.getCityTransactionAmount(), 0,SummaryUtil.AllTurnover);
        // material
        documentList = LogDb.queryCityTransactionAmount(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf(),SummaryUtil.MATERIAL);
        SummaryUtil.insertCityTransactionAmount(documentList, yestodayStartTime, SummaryUtil.getCityTransactionAmount(), SummaryUtil.MATERIAL, SummaryUtil.ItemSales);
        // produce
        documentList = LogDb.queryCityTransactionAmount(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf(),SummaryUtil.PRODUCE);
        SummaryUtil.insertCityTransactionAmount(documentList, yestodayStartTime, SummaryUtil.getCityTransactionAmount(), SummaryUtil.PRODUCE, SummaryUtil.ItemSales);
        //promote
        documentList = LogDb.queryCityTransactionAmount(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf(),SummaryUtil.PROMOTE);
        SummaryUtil.insertCityTransactionAmount(documentList, yestodayStartTime, SummaryUtil.getCityTransactionAmount(), SummaryUtil.PROMOTE, SummaryUtil.ItemSales);
        //technology
        documentList = LogDb.queryCityTransactionAmount(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf(),SummaryUtil.TECHNOLOGY);
        SummaryUtil.insertCityTransactionAmount(documentList, yestodayStartTime, SummaryUtil.getCityTransactionAmount(), SummaryUtil.TECHNOLOGY, SummaryUtil.ItemSales);
        // retailshop
        documentList = LogDb.queryCityTransactionAmount(yestodayStartTime, todayStartTime, LogDb.getNpcBuyInShelf());
        SummaryUtil.insertCityTransactionAmount(documentList, yestodayStartTime, SummaryUtil.getCityTransactionAmount(), SummaryUtil.RETAIL, SummaryUtil.ItemSales);


        //accept all client request
        StatisticSession.setIsReady(true);
        long nowTime1 = System.currentTimeMillis();
        timeStr = formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(nowTime1), ZoneId.systemDefault()));
        LOGGER.debug(MessageFormat.format("DayJob end execute, time = {0}, consume = {1} ms",
                timeStr, nowTime1 - nowTime));
    }
}
