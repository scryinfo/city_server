package Game.Util;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class DateUtil{
	
	static SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	/**
	* 得到本周周一
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
	* 得到本周周日24点
	*/
	public static String getSundayOfThisWeek() {
		Calendar c = Calendar.getInstance();
		int day_of_week = c.get(Calendar.DAY_OF_WEEK) - 1;
		if (day_of_week == 0)
		day_of_week = 7;
		c.add(Calendar.DATE, -day_of_week + 8);
	    c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
		return format.format(c.getTime());
	}


	public static void main(String[] args) throws IOException {
		//本周周一
		getMondayOfThisWeek();
		//得到本周周日
		getSundayOfThisWeek();
		System.out.println(getSundayOfThisWeek());
	}
}