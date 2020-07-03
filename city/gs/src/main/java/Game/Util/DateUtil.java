package Game.Util;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class DateUtil{
	
	static SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	/**
	* Get this week monday
	*/
	public static String getMondayOfThisWeek() {
		Calendar c = Calendar.getInstance();
		int day_of_week = c.get(Calendar.DAY_OF_WEEK) - 1;
		if (day_of_week == 0)
		day_of_week = 7;
		c.add(Calendar.DATE, -day_of_week + 1);
		return format.format(c.getTime());
	}
	/**
	* Get 24 o'clock this Sunday
	*/
	public static long getSundayOfThisWeek() {
		Calendar c = Calendar.getInstance();
		int day_of_week = c.get(Calendar.DAY_OF_WEEK) - 1;
		if (day_of_week == 0)
		day_of_week = 7;
		c.add(Calendar.DATE, -day_of_week + 8);
	    c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
		return c.getTime().getTime();
	}
	/**
	* Get 0 o'clock today
	*/
	public static long getTodayStart(){
 	    Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
        Date endDate = calendar.getTime();
        return endDate.getTime();
    }
    /**
	* Get at 24 tonight
	*/
	public static long getTodayEnd(){
 	    Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DATE, +1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
        Date endDate = calendar.getTime();
        return endDate.getTime();
    }
	/**
	* Get the current hour 55 minutes
	*/
	public static long getCurrentHour55(){
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        Date startDate = calendar.getTime();
        long endTime=startDate.getTime()+1000 * 60  * 55; 
        return endTime;
    }
	public static void main(String[] args) throws IOException {
		//Monday this week
		getMondayOfThisWeek();
		//Get this week sunday
		getSundayOfThisWeek();
		System.out.println(format.format(getSundayOfThisWeek()));
		System.out.println(format.format(getTodayEnd()));
		System.out.println(format.format(getCurrentHour55()));
		System.out.println(format.format(getTodayStart()));
	}

	public static long  getNowSecond(){
		//Get the number of seconds elapsed today
		Calendar calendar = Calendar.getInstance();
		int hour = calendar.get(Calendar.HOUR_OF_DAY);
		int minute = calendar.get(Calendar.MINUTE);
		int seconds = calendar.get(Calendar.SECOND);
		int todaySecond = (hour * 3600 + minute * 60 + seconds);
		return todaySecond;
	}
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
}