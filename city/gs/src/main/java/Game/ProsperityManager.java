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

    private int getLocalProsperity(Building building) {  //经用于获取坐标的点的繁荣度，并非最终繁华度
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
                Coordinate coor = new Coordinate(x, y);
                int finalX = x;
                int finalY = y;
                City.instance().forEachBuilding(coor.toGridIndex(), b -> {
                    if(finalX >= b.area().l.x&&
                            finalX <= b.area().r.x&&
                            finalY >=b.area().l.y&&
                            finalY <= b.area().r.y) {
                        trafficTemp += b.getFlow();
                    }
                });
                //4.计算繁华值
                prosperity += trafficTemp * offsetTemp;
            }
        }
        return prosperity;
    }


    /*全城繁华值总和,1小时更新内根据地块位置逐步更新.*/
    public void totalProsperity(long diffNano) {
        if (timer.update(diffNano)) {
            //统计全城所有建筑的繁华度
            City.instance().forEachBuilding(b->{
                trafficTemp=0;
                int prosperity = getLocalProsperity(b);
                buildingProsperityMap.put(b, prosperity);
            });
            System.err.println(buildingProsperityMap);
        }
    }

    /*获取当前繁华度:建筑繁华度 = 当前建筑包含地块的繁华值总和 / 全城繁华值总和*/
    public double getBuildingProsperity(Building building){
        double localBuildProsperity = buildingProsperityMap.get(building);//当前建筑繁荣度
        Integer sumProsperity = buildingProsperityMap.values().stream().reduce(Integer::sum).orElse(0);
        return localBuildProsperity/sumProsperity;
    }




}
