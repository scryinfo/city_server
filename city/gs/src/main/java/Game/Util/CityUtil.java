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
        long count=0;
        long sumSalary=0;

        List<Building> buildings = new ArrayList<>();
        City.instance().forEachBuilding(b->buildings.add(b));
        for (Building b : buildings) {
            if(!b.outOfBusiness()) {
                sumSalary += b.singleSalary();
                count++;
            }
        }
        return count == 0 ? 0L : sumSalary / count;
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
        avgProperty.put(1,socialSize==0?0L:socialSumMoney / socialSize);
        avgProperty.put(0, employeeSize == 0 ? 0L : employeeSumMoney / employeeSize);
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
