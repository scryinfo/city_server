package Game;

import Game.Meta.MetaData;
import Game.Meta.MetaGroundAuction;
import Game.Timers.PeriodicTimer;
import org.checkerframework.checker.units.qual.C;

import java.util.*;
import java.util.concurrent.TimeUnit;

/*繁华度*/
public class ProsperousDegreeManager {

    private ProsperousDegreeManager() {}

    private static ProsperousDegreeManager instance=new ProsperousDegreeManager();

    public static ProsperousDegreeManager instance(){
        return instance;
    }

    //1.城市最大范围
    int cityLen = City.instance().getMeta().x > City.instance().getMeta().y ? City.instance().getMeta().x : City.instance().getMeta().y;

    //2.城市人流量，可以先算出所有地块的的人流量
    int trafficTemp = 0;

    //缓存全城繁华度
    private Map<Building, Integer> buildingProsperityMap = new HashMap<>();//全城所有的繁荣度
    List<Building> indexs = new ArrayList<>();

    //定时器（1小时统计一次）
    PeriodicTimer timer = new PeriodicTimer((int) TimeUnit.HOURS.toMillis(1),0);

    /*public int getLocalProsperity(int groundX, int groundY) {
        int disTemp = 0;//距离
        int prosperity = 0;//土地繁华度值
        for (int x = 1; x < cityLen; x++) {  //初始坐标从1开始 100次
            for (int y = 1; y < cityLen; y++) {//100次

                //1.位置关系（不可优化，因为要跟着循环一起变化）
                int absX = Math.abs(groundX - x);
                int absY = Math.abs(groundY - y);
                disTemp = absX > absY ? absX : absY;

                //2.offsetTemp 位置偏差比例（不可优化）
                int offsetTemp = 1 - (disTemp / cityLen);


                //3.统计获取当前位置的人流量（其实统计的是建筑周围的人流量，可优化）
                GridIndex groundIndex = new Coordinate(x, y).toGridIndex();
                City.instance().forEachBuilding(groundIndex, b -> {
                    trafficTemp += b.getFlow();
                });
                //4.计算繁华值
                prosperity += trafficTemp * offsetTemp;
            }
        }
        return prosperity;
    }*/

    public int getBuildingProsperity(Building building) {
        Coordinate coordinate = building.coordinate();
        int disTemp = 0;//距离
        int prosperity = 0;//土地繁华度值
        for (int x = 1; x < cityLen; x++) {  //初始坐标从1开始 100次
            for (int y = 1; y < cityLen; y++) {//100次
                //1.位置关系
                int absX = Math.abs(coordinate.x - x);
                int absY = Math.abs(coordinate.y - y);
                disTemp = absX > absY ? absX : absY;
                //2.offsetTemp 位置偏差比例
                int offsetTemp = 1 - (disTemp / cityLen);
                //3.统计获取当前位置的人流量
                GridIndex groundIndex = new Coordinate(x, y).toGridIndex();
                City.instance().forEachBuilding(groundIndex, b -> {
                    trafficTemp += b.getFlow();
                });
                //4.计算繁华值
                prosperity += trafficTemp * offsetTemp;
            }
        }
        return prosperity;
    }


    /*全城繁华值总和，由于每次都是遍历全城土地，所以肯定不能一次全部统计完，目前采用1小时的时间去统计,1小时内根据地块位置逐步更新.*/
    public void totalProsperity(long diffNano) {
        if (timer.update(diffNano)) {
            //统计全城所有建筑的繁华度
            City.instance().forEachBuilding(b->{
                int prosperity = getBuildingProsperity(b);
                buildingProsperityMap.put(b, prosperity);
            });
        }
    }
}
