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
    private volatile  static TotalUtil totalUtil = null;//禁止指令重排序

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
            //获取当天开始时间
            Long st = TimeUtil.getTimeDayStartTime(time);
            if(total.containsKey(st)) {
                total.put(st, total.get(st) + money);
            }
            else {
                total.put(st, money);
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

    /*参数1.要统计的销售详情,参数2.表示建筑类型，参数3.表示是否有今日收入统计（false 则直接设置收益为0）*/
    public static Ss.BuildingTodaySaleDetail.TodaySaleDetail totalBuildingSaleDetail(Document document,int buildingType, boolean isTodayIncome){
        Ss.BuildingTodaySaleDetail.TodaySaleDetail.Builder saleInfo = Ss.BuildingTodaySaleDetail.TodaySaleDetail.newBuilder();
        /*设置通用信息*/
        long num=0;
        long account=0;
        UUID producerId = document.get("p",UUID.class);
        saleInfo.setItemId(document.getInteger("itemId"))
                .setProducerId(Util.toByteString(producerId))
                .setIncreasePercent(1);//设置默认的提升比例为100
        System.err.println("商品的生产者Id是:"+producerId);
        if(isTodayIncome){
            num = document.getLong("num");
            account = document.getLong("total");
        }else{
            saleInfo.setIncreasePercent(0);//非今日收入销售则把提升比例设置为0
        }
        saleInfo.setNum((int) num)
                .setSaleAccount(account);
        if(buildingType==MetaBuilding.PRODUCE||buildingType==MetaBuilding.RETAIL){
            saleInfo.setBrandName(document.getString("brand"));
        }
        return saleInfo.build();
    }
}
