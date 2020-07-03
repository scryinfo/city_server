package Statistic.Util;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/*Time tools, used to calculate time*/
public class TimeUtil {
    //Today's start time
    public static Long todayStartTime(){
        TimeZone timeZone = TimeZone.getTimeZone("GMT+8:00");
        Calendar calendar = Calendar.getInstance(timeZone);
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime().getTime();
    }
    //Start time of one month, start time of 30 days
    public static Long monthStartTime(){
        TimeZone timeZone = TimeZone.getTimeZone("GMT+8:00");
        Calendar calendar = Calendar.getInstance(timeZone);
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_MONTH,-30);
        return calendar.getTime().getTime();
    }
    //30 days before
    public static Calendar monthCalendar(){
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.add(Calendar.DATE, -30);
        return calendar;
    }
    //6 days before
    public static Calendar beforeSixDay(){
        TimeZone timeZone = TimeZone.getTimeZone("GMT+8:00");
        Calendar calendar = Calendar.getInstance(timeZone);
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.add(Calendar.DATE, -6);
        return calendar;
    }
    //7 days before
    public static Calendar beforeSevenDay(){
        TimeZone timeZone = TimeZone.getTimeZone("GMT+8:00");
        Calendar calendar = Calendar.getInstance(timeZone);
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.add(Calendar.DATE, -7);
        return calendar;
    }
    //Get the start time of the specified time
    public static Long getTimeDayStartTime(Long time){
        //Get the time in Dongba District
        TimeZone timeZone = TimeZone.getTimeZone("GMT+8:00");
        Calendar calendar = Calendar.getInstance(timeZone);
        calendar.setTime(new Date(time));
        //First set the time to the initial time
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime().getTime();
    }

    /*Get the start time of yesterday*/
    public static Long getYesterdayStartTime(){
        TimeZone timeZone = TimeZone.getTimeZone("GMT+8:00");
        Calendar calendar = Calendar.getInstance(timeZone);
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.add(Calendar.DATE, -1);
        return calendar.getTimeInMillis();
    }
}
