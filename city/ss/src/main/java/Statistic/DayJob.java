package Statistic;

import Shared.LogDb;
import org.bson.Document;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.List;

import static Statistic.SummaryUtil.DAY_MILLISECOND;

public class DayJob implements org.quartz.Job {

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        long todayStartTime = SummaryUtil.todayStartTime();
        long yestodayStartTime = todayStartTime - DAY_MILLISECOND;

        //income
        List<Document> documentList = LogDb.daySummarySellGround(yestodayStartTime, todayStartTime,true);
        SummaryUtil.insertDaySellGround(SummaryUtil.Type.INCOME, documentList, yestodayStartTime);
        //pay
        documentList = LogDb.daySummarySellGround(yestodayStartTime, todayStartTime,false);
        SummaryUtil.insertDaySellGround(SummaryUtil.Type.PAY, documentList, yestodayStartTime);
    }
}
