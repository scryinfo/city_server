package Game.Util;

import Game.*;
import Game.Meta.MetaData;
import Shared.LogDb;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import org.bson.Document;

import java.util.*;

import static com.mongodb.client.model.Filters.*;


public class CityUtil {

    /*
     *统计多少天到多少天之间的的工资之和
     * */
    public static Long getSumSalaryByDays(int startday,int enday){
        MongoCollection<Document> paySalary = LogDb.getPaySalary();
        //1.首先计算出时间区间
        //开始时间
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DATE,c.get(Calendar.DATE)-(startday-1));
        c.set(Calendar.HOUR_OF_DAY,0);
        c.set(Calendar.MINUTE,0);
        c.set(Calendar.SECOND,0);
        c.set(Calendar.MILLISECOND,0);
        long startTime = c.getTime().getTime();
        //if endTime is 0,the time is today lastTime
        c.add(Calendar.DAY_OF_YEAR,startday-enday);
        c.add(Calendar.MILLISECOND,-1);
        long endTime=c.getTime().getTime();
        List<Document> documents = new ArrayList<>();
        Document projectObject = new Document()
            .append("s", "$s")
            .append("t","$t");
        paySalary.aggregate(
                Arrays.asList(
                        Aggregates.match(and(gte("t", startTime), lte("t", endTime))),
                        Aggregates.group(null, Accumulators.sum("s", "$s")),
                        Aggregates.project(projectObject)
                )
        ).forEach((Block<? super Document>) documents::add);
        Map<Long, Long> map = new LinkedHashMap<>();
        documents.forEach(p->{
            map.put(p.getLong("t"), p.getLong("s"));
        });
        Long salarys = 0L;
        for (Map.Entry<Long, Long> entry : map.entrySet()) {
            salarys = entry.getValue();
        }
        System.out.println("前"+startday+"天，到"+enday+"的人均总工资是"+salarys);
        return salarys;
    }

    //获取玩家的性别信息
    public static Map<String,Integer> genderSex(List<Player> players){
        Map<String, Integer> sex = new HashMap<>();
        int man=0;
        int woman=0;
        for (Player p : players) {
            if(p.isMale())
                man++;
            else
                woman++;
        }
        sex.put("girl", woman);
        sex.put("boy", man);
        return sex;
    }

    //获取城市的平均工资
    public static Long cityAvgSalary(){
        long avgSalary=0;
        long sumSalary=0;
        List<Building> buildings = new ArrayList<>();
        City.instance().forEachBuilding(b->buildings.add(b));
        for (Building b : buildings) {
            sumSalary += b.singleSalary();
        }
        if(avgSalary==0){
            return 0L;
        }else {
            return sumSalary / buildings.size();
        }
    }

    //平均资产
    public static Map<Integer, Long> cityAvgProperty(){//1表示福利npc、0表示员工的平均资产
        long socialSumMoney=0;//福利npc总资产
        long employeeSumMoney=0;//员工总资产
        int socialSize=0;
        int employeeSize=0;
        Map<Integer, Long> avgProperty = new HashMap<>();
        List<Npc> buildings = new ArrayList<>();
        for (Npc npc : NpcManager.instance().getAllNpc().values()) {
            if(npc.type()==11||npc.type()==10){
                socialSumMoney += npc.money();
                socialSize++;
            }else{
                employeeSumMoney+=npc.money();
                employeeSize++;
            }
        }
        if(socialSize==0){
            avgProperty.put(1, 0L);
        }else{
            avgProperty.put(1, socialSumMoney / socialSize);
        }
        if(employeeSize==0) {
            avgProperty.put(0, 0L);
        }
        else {
            avgProperty.put(0, employeeSumMoney / employeeSize);
        }
        return avgProperty;
    }

    //工资涨幅
    public static double increaseRatio(){//工资涨幅比例（一周统计一次）我现在要计算总共的涨幅。
        double increaseRatio=0d;
        Map<Integer, IndustryIncrease> map = City.instance().getIndustryMoneyMap();
        for (Map.Entry<Integer, IndustryIncrease> entry : map.entrySet()) {
            Map<Integer, Long> industryNpcNumMap=NpcManager.instance().countNpcByBuildingType();
            double r=entry.getValue().getIndustryMoney()/(double)industryNpcNumMap.get(entry.getKey());//涨幅
            increaseRatio+= r;
        }
        return increaseRatio/ map.size();
    }

    //社保福利(城市产业平均工资*社保比例)
    public static int socialMoney(){
        int v = (int) (City.instance().getAvgIndustrySalary() * MetaData.getCity().insuranceRatio);
        return v;
    }
    //获取城市税收
    public static long getTax(){
        long allTax = 0;
        List<Building> allBuilding = new ArrayList<>();
        City.instance().forEachBuilding(b->{
            allBuilding.add(b);
        });
        for (Building building : allBuilding) {
            allTax += building.allTax();
        }
        return allTax;
    }
}
