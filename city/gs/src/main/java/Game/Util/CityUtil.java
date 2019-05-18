package Game.Util;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.lte;

import java.util.*;

import Game.Building;
import Game.City;
import Game.Player;
import org.bson.Document;

import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;

import Shared.LogDb;

/*
*统计多少天到多少天之间的的工资之和
* */
public class CityUtil {


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
        return sumSalary / buildings.size();
    }
}
