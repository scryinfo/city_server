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

/*Statistical tools*/
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

    //Statistics for the first 29 days
    public  Map<Long,Long> monthTotal(Map<Long, Long> sourceMap){
        Map<Long, Long> total = new TreeMap<>();
        //1.Process data older than 29 days, and sum by day statistics
        sourceMap.forEach((time,money)->{
            //Get the start time of the day
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

    //Get the latest data on today's player income and expenditure (also need to be accumulated)
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

    //Get the latest revenue and expenditure information today
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

    /*Parameter 1. The sales details to be counted, parameter 2. indicates the type of building, parameter 3. indicates whether there is today's income statistics (false then directly sets the revenue to 0)*/
    public static Ss.BuildingTodaySaleDetail.TodaySaleDetail totalBuildingSaleDetail(Document document,int buildingType, boolean isTodayIncome){
        Ss.BuildingTodaySaleDetail.TodaySaleDetail.Builder saleInfo = Ss.BuildingTodaySaleDetail.TodaySaleDetail.newBuilder();
        /*Set general information*/
        long num=0;
        long account=0;
        UUID producerId = document.get("p",UUID.class);
        saleInfo.setItemId(document.getInteger("itemId"))
                .setProducerId(Util.toByteString(producerId))
                .setIncreasePercent(1);//Set the default lift ratio to 100
        System.err.println("商品的生产者Id是:"+producerId);
        if(isTodayIncome){
            num = document.getLong("num");
            account = document.getLong("total");
        }else{
            saleInfo.setIncreasePercent(0);//For non-today revenue sales, the promotion ratio is set to 0
        }
        saleInfo.setNum((int) num)
                .setSaleAccount(account);
        if(buildingType==MetaBuilding.PRODUCE||buildingType==MetaBuilding.RETAIL){
            saleInfo.setBrandName(document.getString("brand"));
        }
        return saleInfo.build();
    }
}
