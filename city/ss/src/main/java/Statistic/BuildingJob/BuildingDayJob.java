package Statistic.BuildingJob;

import Shared.LogDb;
import Statistic.StatisticSession;
import Statistic.SummaryUtil;
import org.apache.log4j.Logger;
import org.bson.Document;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static Statistic.SummaryUtil.DAY_MILLISECOND;

public class BuildingDayJob implements Job
{
    private static final Logger LOGGER = Logger.getLogger(BuildingDayJob.class);
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException
    {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS");
        long nowTime = System.currentTimeMillis();
        long todayStartTime = SummaryUtil.todayStartTime(nowTime);
        long yestodayStartTime = todayStartTime - DAY_MILLISECOND;
        String timeStr = formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(nowTime), ZoneId.systemDefault()));
        LOGGER.info("BuildingDay start execute,time = " + timeStr);

        List<Document> documentList = LogDb.buildingDayIncomeSummary(yestodayStartTime, todayStartTime);
        SummaryUtil.insertBuildingDayIncome(documentList,yestodayStartTime);

        long nowTime1 = System.currentTimeMillis();
        timeStr = formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(nowTime1), ZoneId.systemDefault()));
        LOGGER.info(MessageFormat.format("BuildingDay end execute, time = {0}, consume = {1} ms",
                timeStr, nowTime1 - nowTime));
    }
}
