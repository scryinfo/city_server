package Statistic.TimeUtil;

import java.util.Calendar;
import java.util.Date;
/*时间工具类，用于对时间的计算*/
public class TimeUtil {
    //今日的开始时间
    public static Long todayStartTime(){
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime().getTime();
    }
    //一个月的开始时间，30天的起始时间
    public static Long monthStartTime(){
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_MONTH,-30);
        return calendar.getTime().getTime();
    }
    //获取指定时间的起始时间
    public static Long getTimeDayStartTime(Long time){
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(time));
        //首先把时间设置到最初始时间
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime().getTime();
    }
}
