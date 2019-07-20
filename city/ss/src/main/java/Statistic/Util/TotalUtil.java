package Statistic.Util;

import Param.MetaBuilding;
import Shared.Util;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import ss.Ss;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static com.mongodb.client.model.Filters.*;

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

    //获取今日玩家收入支出最新数据（也需要累积）
    public  Long  todayIncomeOrPay(Map<Long, Long> sourceMap){
        //Long todayIncomeOrPay=0L;
        Map<Long, Long> today = new TreeMap<>();
        sourceMap.forEach((time,money)->{
            if(time>=TimeUtil.todayStartTime()){
                today.put(time, money);
            }
        });
        Long todayIncomeOrPay = today.values().stream().reduce(Long::sum).orElse(0L);
        return todayIncomeOrPay;
    }

    //获取今日最新收支信息
    public static Long getTodayPlayerLastPayOrIncome(MongoCollection<Document> collection, UUID pid, Long startTime){
        long account = 0L;
        Document first = collection.find(and(
                eq("p", pid),
                gte("t", startTime)
        )).sort(Sorts.descending("t")).first();
        if(first!=null){
            account = first.getLong("a");
        }
        return account;
    }

    /*参数1.要统计的销售详情,参数2.表示建筑类型，参数3.表示是否有今日收入统计（false 则直接设置受益为0）*/
    public static Ss.BuildingTodaySaleDetail.TodaySaleDetail totalBuildingSaleDetail(Document document,int buildingType, boolean isTodayIncome){
        Ss.BuildingTodaySaleDetail.TodaySaleDetail.Builder saleInfo = Ss.BuildingTodaySaleDetail.TodaySaleDetail.newBuilder();
        /*设置通用信息*/
        UUID producerId = (UUID) document.get("p");
        saleInfo.setItemId(document.getInteger("itemId"))
                .setProducerId(Util.toByteString(producerId));
        if(!isTodayIncome){
            saleInfo.setSaleAccount(0).setNum(0);
        }else{
            saleInfo.setNum(document.getInteger("num"))
                    .setSaleAccount(document.getLong("total"))
                    .setBrandName(document.getString("brand"));
        }
        return saleInfo.build();
    }
}
