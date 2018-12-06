package Statistic;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class WeekJob implements org.quartz.Job {
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        // LogDb.do_stat1();
        // LogDb.do_stat2();
    }
}
