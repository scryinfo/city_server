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

public class PerHourJob implements org.quartz.Job {
    private static final Logger LOGGER = Logger.getLogger(PerHourJob.class);

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
        Date startDate = calendar.getTime();
        long startTime=startDate.getTime()- 1000 * 60  * 5;
        

        long time1 = System.currentTimeMillis();
        long endTime = time1 - time1%(1000 * 60  * 60)- 1000 * 60  * 5;
    //  long startTime1 = endTime - 1000 * 60 * 60;
        
        long time=endTime+1000 * 60  * 5;  


        long nowTime = System.currentTimeMillis();
        String timeStr = formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(nowTime), ZoneId.systemDefault()));
        LOGGER.debug("PerHourJob start execute,time = " + timeStr);

        //每种商品购买的npc人数,每小时统计一次
        //并把统计结果保存到数据库
        List<Document> documentList = LogDb.dayNpcGoodsNum(startTime, endTime, LogDb.getNpcBuyInShelf());
        SummaryUtil.insertHistoryData(SummaryUtil.CountType.BYHOUR, documentList, time, SummaryUtil.getDayGoodsNpcNum());

        //统计耗时
        StatisticSession.setIsReady(true);
        long nowTime1 = System.currentTimeMillis();
        timeStr = formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(nowTime1), ZoneId.systemDefault()));
        LOGGER.debug(MessageFormat.format("PerHourJob end execute, time = {0}, consume = {1} ms",
                timeStr, nowTime1 - nowTime));
    }
}
