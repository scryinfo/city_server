package Game;

import Game.Meta.MetaData;
import Game.Meta.MetaGroundAuction;
import Game.Timers.PeriodicTimer;
import org.checkerframework.checker.units.qual.A;

import java.time.Year;
import java.util.*;
import java.util.concurrent.TimeUnit;

/*繁华度*/
public class ProsperityManager {

    private ProsperityManager() {
        //获取土地拍卖的所有有地块
        Collection<MetaGroundAuction> values = MetaData.getGroundAuction().values();
        values.forEach(g->{
            List<Coordinate> area = g.area;
            allGround.addAll(area);
        });
        long delay = 3600000 / allGround.size();
        timer = new PeriodicTimer((int) TimeUnit.MILLISECONDS.toMillis(delay),0);
    }

    private static ProsperityManager instance=new ProsperityManager();

    List<Coordinate> allGround = new ArrayList<>();//全城所有的地块

    public static ProsperityManager instance(){
        return instance;
    }

    //1.城市最大范围
    int cityLen = City.instance().getMeta().x > City.instance().getMeta().y ? City.instance().getMeta().x : City.instance().getMeta().y;

    //2.城市人流量，可以先算出所有地块的的人流量
    int trafficTemp = 0;

    //3.缓存全城建筑的繁华度
    private Map<Building, Integer> buildingProsperityMap = new LinkedHashMap<>();//全城所有的繁荣度

    private Map<Coordinate, Integer> groundProsperityMap = new HashMap<>();//全城所有土地的繁华度

    List<Building> indexs = new ArrayList<>();

    //定时器（1小时统计一次）
    /*new PeriodicTimer((int) TimeUnit.HOURS.toMillis(1),0)*/
    PeriodicTimer timer;

    int updateIndex=0;

    /*全城繁华值总和,1小时更新内根据地块位置逐步更新.*/
    public void totalProsperity(long diffNano) {

        /*一块一块的去更新地块，而不是一直去更新，直到完成,循环的去更新数据，从头开始更新*/
        if (timer.update(diffNano)) {
            Coordinate coordinate = allGround.get(updateIndex);
            int value = computeGroundProsperity(coordinate.x, coordinate.y);
            groundProsperityMap.put(coordinate, value);
            if(updateIndex<allGround.size()-1){
                updateIndex++;
            }else{
                updateIndex=0;
            }
           /* this.allGround.forEach(ground->{
                int groundProsperity = computeGroundProsperity(ground.x, ground.y);
                groundProsperityMap.put(ground, groundProsperity);
            });*/
        }
    }

    /*计算建筑繁荣度，建筑繁荣度要把建筑包含的所有土地加起来(暂时不用)*/
    private int computeBuildingProsperity(Building building) {  //获取坐标点的繁荣度
        int buildingSumProsperity=0;
        ArrayList<Coordinate> coordinates = new ArrayList<>(building.area().toCoordinates());
        for (Coordinate coordinate : coordinates) {
            trafficTemp=0;//清空人流量
            double disTemp = 0;//位置关系
            int prosperity = 0;//土地繁华度值
            for (int x = 1; x < cityLen; x++) {
                for (int y = 1; y < cityLen; y++) {
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

    /*计算土地的繁荣度*/
    private int computeGroundProsperity(int beginX,int beginY){
        double disTemp = 0;//距离
        int prosperity = 0;//土地繁华度值
        trafficTemp=0;
        for (int x = 1; x < cityLen; x++) {     //初始坐标从1开始 100次
            for (int y = 1; y < cityLen; y++) {
                //1.位置关系
                disTemp=Math.abs(beginX - x)>Math.abs(beginY - y)?Math.abs(beginX - x):Math.abs(beginY - y);
                //2.offsetTemp 位置偏差比例
                double offsetTemp = 1 - (disTemp / cityLen);
                //3.统计获取当前位置的人流量(也就是获取这个土地上是否修建了建筑，获取这个建筑的人流量)
                trafficTemp= City.instance().getTraffic(x, y);//获取位置人流量
                //4.计算繁华值
                prosperity += trafficTemp * offsetTemp;
            }
        }
        return prosperity;
    }

    //获取土地繁荣度
    public int  getGroundProsperity(Coordinate c){
        return groundProsperityMap.getOrDefault(c, 0);
    }
    //获取建筑繁华度
    public int  getBuildingProsperity(Building building){
        //建筑繁荣度，直接获取建筑所占土地的繁荣度
        Collection<Coordinate> coordinates = building.area().toCoordinates();
        int sumProsperity=0;
        for (Coordinate coordinate : coordinates) {
            sumProsperity += groundProsperityMap.getOrDefault(coordinate,0);
        }
        return sumProsperity;
        //return buildingProsperityMap.getOrDefault(building,0);
    }

    /*获取建筑繁荣度评分:建筑繁华度 = 当前建筑包含地块的繁华值总和 / 全城繁华值总和*/
    public double getBuildingProsperityScore(Building building){
        double localBuildProsperity = getBuildingProsperity(building);//当前建筑繁荣度
        Integer sumProsperity = groundProsperityMap.values().stream().reduce(Integer::sum).orElse(0);
        if(sumProsperity==0)
            return 0;
        return localBuildProsperity/sumProsperity;
    }

    /*获取土地繁荣度评分:当前土地繁华值 / 全城繁华值总和*/
    public double getGroundProsperityScore(int x,int y){
        Coordinate coordinate = new Coordinate(x, y);
        int groundProsperity = groundProsperityMap.getOrDefault(coordinate,0);
        Integer sumProsperity = groundProsperityMap.values().stream().reduce(Integer::sum).orElse(0);
        if(sumProsperity==0)
            return 0;
        return groundProsperity / sumProsperity;
    }
}
