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

    //Get the player's gender information
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

    //Get the average salary in the city
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

    //Average assets
    public static Map<Integer, Long> cityAvgProperty(){//1 means welfare npc, 0 means the employee's average assets
        long socialSumMoney=0;//Welfare npc total assets
        long employeeSumMoney=0;//Employee total assets
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

    //Wage increase
    public static double increaseRatio(){//Wage increase rate (stated once a week) I now want to calculate the total increase.
        double increaseRatio=0d;
        Map<Integer, IndustryIncrease> map = City.instance().getIndustryMoneyMap();
        for (Map.Entry<Integer, IndustryIncrease> entry : map.entrySet()) {
            Map<Integer, Long> industryNpcNumMap=NpcManager.instance().countNpcByBuildingType();
            double r=entry.getValue().getIndustryMoney()/(double)industryNpcNumMap.get(entry.getKey());//Increase
            increaseRatio+= r;
        }
        return increaseRatio/ map.size();
    }

    //Social security benefits (urban industry average wage * social security ratio)
    public static int socialMoney(){
        int v = (int) (City.instance().getAvgIndustrySalary() * MetaData.getCity().insuranceRatio);
        return v;
    }
    //Get city tax
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
