package Game;

import Game.Timers.PeriodicTimer;

import java.util.*;
import java.util.concurrent.TimeUnit;

/*繁华度*/
public class ProsperityManager {

    private ProsperityManager() {}

    private static ProsperityManager instance=new ProsperityManager();

    public static ProsperityManager instance(){
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

    /*全城繁华值总和,1小时更新内根据地块位置逐步更新.*/
    public void totalProsperity(long diffNano) {
        if (timer.update(diffNano)) {
            //统计全城所有建筑的繁华度
            City.instance().forEachBuilding(b->{
                trafficTemp=0;
                int prosperity = computeBuildingProsperity(b);
                buildingProsperityMap.put(b, prosperity);
            });
        }
    }

    /*计算建筑繁荣度，建筑繁荣度要把建筑包含的所有土地加起来*/
    public int computeBuildingProsperity(Building building) {  //获取坐标点的繁荣度
        int buildingSumProsperity=0;
        ArrayList<Coordinate> coordinates = new ArrayList<>(building.area().toCoordinates());
        for (Coordinate coordinate : coordinates) {
            trafficTemp=0;//清空人流量
            double disTemp = 0;//位置关系
            int prosperity = 0;//土地繁华度值
            for (int x = 1; x < cityLen; x++) {  //初始坐标从1开始 100次
                for (int y = 1; y < cityLen; y++) {//100次
                    //1.位置关系
                    int absX = Math.abs(coordinate.x - x);
                    int absY = Math.abs(coordinate.y - y);
                    disTemp = absX > absY ? absX : absY;
                    //2.offsetTemp 位置偏差比例
                    double offsetTemp = 1 - (disTemp / cityLen);
                    //3.统计获取当前位置的人流量,筛选这一片范围内的建筑，如果有在建筑上的，计算人流量
                    trafficTemp =City.instance().getTraffic(x,y);
                    //4.计算繁华值
                    prosperity += trafficTemp * offsetTemp;
                    buildingSumProsperity += prosperity;
                }
            }
        }
        return buildingSumProsperity;
    }

    /*查询土地的繁荣度*/
    public int getGroundProsperity(int beginX,int beginY){
        int disTemp = 0;//距离
        int prosperity = 0;//土地繁华度值
        trafficTemp=0;
        for (int x = 1; x < cityLen; x++) {     //初始坐标从1开始 100次
            for (int y = 1; y < cityLen; y++) {
                int absX = Math.abs(beginX - x);
                int absY = Math.abs(beginY - y);
                disTemp = absX > absY ? absX : absY;
                //2.offsetTemp 位置偏差比例
                int offsetTemp = 1 - (disTemp / cityLen);
                //3.统计获取当前位置的人流量(也就是获取这个土地上是否修建了建筑，获取这个建筑的人流量)
                trafficTemp= City.instance().getTraffic(x, y);//获取位置人流量
                //4.计算繁华值
                prosperity += trafficTemp * offsetTemp;
            }
        }
        return prosperity;
    }

    //获取建筑繁华度
    public int  getBuildingProsperity(Building building){
        return buildingProsperityMap.getOrDefault(building,0);
    }

    /*获取建筑繁荣度评分:建筑繁华度 = 当前建筑包含地块的繁华值总和 / 全城繁华值总和*/
    public double getBuildingProsperityScore(Building building){
        double localBuildProsperity = buildingProsperityMap.getOrDefault(building,0);//当前建筑繁荣度
        Integer sumProsperity = buildingProsperityMap.values().stream().reduce(Integer::sum).orElse(0);
        if(sumProsperity==0)
            return 0;
        return localBuildProsperity/sumProsperity;
    }

    /*获取土地繁荣度评分:当前土地繁华值 / 全城繁华值总和*/
    public double getGroundProsperityScore(int x,int y){
        int groundProsperity = getGroundProsperity(x, y);
        Integer sumProsperity = buildingProsperityMap.values().stream().reduce(Integer::sum).orElse(0);
        if(sumProsperity==0)
            return 0;
        return groundProsperity / sumProsperity;
    }
}
