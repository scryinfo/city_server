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

public class MinuteJob implements org.quartz.Job{
    private static final Logger LOGGER = Logger.getLogger(SecondJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS");
        StatisticSession.setIsReady(false);
        long time = System.currentTimeMillis();
        long endTime = time - time%(1000 * 60); //Statistics every 1 minute
        long startTime = endTime - 1000 * 60;//1 minute before

        long nowTime = System.currentTimeMillis();
        String timeStr = formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(nowTime), ZoneId.systemDefault()));
        LOGGER.debug("MinuteJob start execute,time = " + timeStr);

        //player income
      /*  List<Document> documentList= LogDb.dayPlayerIncomeOrPay(startTime, endTime, LogDb.getPlayerIncome());//Statistics of revenue per minute
        SummaryUtil.insertPlayerIncomeOrPay(documentList, startTime, SummaryUtil.getDayPlayerIncome());
        //player pay
        documentList = LogDb.dayPlayerIncomeOrPay(startTime, endTime, LogDb.getPlayerPay());
        SummaryUtil.insertPlayerIncomeOrPay(documentList, startTime, SummaryUtil.getDayPlayerPay());*/

        //Time-consuming statistics
        StatisticSession.setIsReady(true);
        long nowTime1 = System.currentTimeMillis();
        timeStr = formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(nowTime1), ZoneId.systemDefault()));
        LOGGER.debug(MessageFormat.format("MinuteJob end execute, time = {0}, consume = {1} ms",
                timeStr, nowTime1 - nowTime));
    }
}
