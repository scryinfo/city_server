package Statistic.TimeUtil;

import java.util.Map;
import java.util.TreeMap;

/*统计工具类*/
public class TotalUtil {
    private static TotalUtil totalUtil = null;

    private TotalUtil() {
    }

    public static TotalUtil getInstance() {
        if( totalUtil == null ) {
            synchronized( TotalUtil.class ) {
                if( totalUtil == null ) {
                    totalUtil = new TotalUtil();
                }
            }
        }
        return totalUtil;
    }

    //统计前面29天的数据
    public  Map<Long,Long> monthTotal(Map<Long, Long> sourceMap){
        Map<Long, Long> total = new TreeMap<>();
        Map<Long, Long> today = new TreeMap<>();
        //1.处理29天以前的数据，以天数统计求和
        sourceMap.forEach((time,money)->{
            //处理29天以前的数据
            if(time<=TimeUtil.todayStartTime()-1&&time>=TimeUtil.monthStartTime()){
                //获取当天开始时间
                Long st = TimeUtil.getTimeDayStartTime(time);
                if(total.containsKey(st)) {
                    total.put(st, total.get(st) + money);
                }
                else {
                    total.put(st, money);
                }
            }
        });
        return total;
    }

    //获取今日玩家收入支出最新数据
    public  Long  todayIncomOrPay(Map<Long, Long> sourceMap){
        Long todayIncomeOrPay=0L;
        Map<Long, Long> today = new TreeMap<>();
        sourceMap.forEach((time,money)->{
            if(time>=TimeUtil.todayStartTime()){
                today.put(time, money);
            }
        });
        Map.Entry<Long, Long> entry = ((TreeMap<Long, Long>) today).lastEntry();
        if(entry!=null)
            todayIncomeOrPay = entry.getValue();
        return todayIncomeOrPay;
    }
}
