package Game;

import Game.Meta.MetaData;
import Game.Meta.MetaGroundAuction;
import Game.Timers.PeriodicTimer;

import java.util.*;
import java.util.concurrent.TimeUnit;

/*Prosperity*/
public class ProsperityManager {

    private ProsperityManager() {
        cityLen = MetaData.getCity().x > MetaData.getCity().y ? MetaData.getCity().x : MetaData.getCity().y;
        //Get all the parcels of land auction
        Collection<MetaGroundAuction> values = MetaData.getGroundAuction().values();
        values.forEach(g->{
            List<Coordinate> area = g.area;
            allGround.addAll(area);
        });
        long delay = 3600000 / allGround.size();
        timer = new PeriodicTimer((int) TimeUnit.MILLISECONDS.toMillis(delay),0);
    }

    private static ProsperityManager instance=new ProsperityManager();

    List<Coordinate> allGround = new ArrayList<>();//All plots in the city


    public static ProsperityManager instance(){
        return instance;
    }

    //1.The largest city
    int cityLen;

    //2.City traffic, you can first calculate the traffic of all plots
    int trafficTemp = 0;

    private Map<Coordinate, Integer> groundProsperityMap = new HashMap<>();//Prosperity of all land in the city

    List<Building> indexs = new ArrayList<>();

    //Timer (updated every hour)
    PeriodicTimer timer;

    int updateIndex=0;

    /*The sum of the prosperity value of the whole city, which is gradually updated according to the location of the plot within 1 hour of update.*/
    public void totalProsperity(long diffNano) {
        /*Update plots piece by piece instead of updating all the time until completion, update data in a loop and start updating from the beginning*/
        if (timer.update(diffNano)) {
            if(updateIndex==allGround.size()){
                updateIndex=0;
            }
            Coordinate coordinate = allGround.get(updateIndex);
            int value = computeGroundProsperity(coordinate.x, coordinate.y);
            groundProsperityMap.put(coordinate, value);
            if(updateIndex<allGround.size()){
                updateIndex++;
            }
        }
    }

    /*Calculate the prosperity of the building. The prosperity of the building should add up all the land included in the building*/
   /* private int computeBuildingProsperity(Building building) {  //Get the prosperity of coordinate points
        int buildingSumProsperity=0;
        ArrayList<Coordinate> coordinates = new ArrayList<>(building.area().toCoordinates());
        for (Coordinate coordinate : coordinates) {
            trafficTemp=0;//Clear traffic
            double disTemp = 0;//Positional relationship
            int prosperity = 0;//Land prosperity value
            for (int x = 1; x < cityLen; x++) {
                for (int y = 1; y < cityLen; y++) {
                    //1.Positional relationship
                    int absX = Math.abs(coordinate.x - x);
                    int absY = Math.abs(coordinate.y - y);
                    disTemp = absX > absY ? absX : absY;
                    //2.offsetTemp Position deviation ratio
                    double offsetTemp = 1 - (disTemp / cityLen);
                    //3.Statistically obtain the current location of people, screen the buildings within this range, if there are buildings, calculate the number of people
                    trafficTemp =City.instance().getTraffic(x,y);
                    //4.Calculate bustling value
                    prosperity += trafficTemp * offsetTemp;
                    buildingSumProsperity += prosperity;
                }
            }
        }
        return buildingSumProsperity;
    }*/

    /*Calculate the prosperity of the land*/
    private int computeGroundProsperity(int beginX,int beginY){
        double disTemp = 0;//distance
        int prosperity = 0;//Land prosperity value
        trafficTemp=0;
        for (int x = 1; x < cityLen; x++) {     //The initial coordinate starts from 1 100 times
            for (int y = 1; y < cityLen; y++) {
                //1.Positional relationship
                disTemp=Math.abs(beginX - x)>Math.abs(beginY - y)?Math.abs(beginX - x):Math.abs(beginY - y);
                //2.offsetTemp Position deviation ratio
                double offsetTemp = 1 - (disTemp / cityLen);
                //3.Statistically obtain the number of people in the current location (that is, whether there is a building built on this land, and the number of people in this building)
                trafficTemp= City.instance().getTraffic(x, y);//Get location traffic
                //4.Calculate bustling value
                prosperity += trafficTemp * offsetTemp;
            }
        }
        return prosperity;
    }

    //Gain land prosperity
    public int  getGroundProsperity(Coordinate c){
        return groundProsperityMap.getOrDefault(c, 0);
    }
    //Get building prosperity
    public int  getBuildingProsperity(Building building){
        //Building prosperity, directly obtain the prosperity of the land occupied by the building
        Collection<Coordinate> coordinates = building.area().toCoordinates();
        int sumProsperity=0;
        for (Coordinate coordinate : coordinates) {
            sumProsperity += groundProsperityMap.getOrDefault(coordinate,0);
        }
        return sumProsperity;
        //return buildingProsperityMap.getOrDefault(building,0);
    }

    /*Get the building prosperity score: Building prosperity = the total prosperity value of the current building including the plot / the total prosperity value of the whole city*/
    public double getBuildingProsperityScore(Building building){
        double localBuildProsperity = getBuildingProsperity(building);//Current building prosperity
        Integer sumProsperity = groundProsperityMap.values().stream().reduce(Integer::sum).orElse(0);
        if(sumProsperity==0)
            return 0;
        return localBuildProsperity/sumProsperity;
    }

    /*Get land prosperity score: current land prosperity value / total prosperity value of the whole city*/
    public double getGroundProsperityScore(int x,int y){
        Coordinate coordinate = new Coordinate(x, y);
        int groundProsperity = groundProsperityMap.getOrDefault(coordinate,0);
        Integer sumProsperity = groundProsperityMap.values().stream().reduce(Integer::sum).orElse(0);
        if(sumProsperity==0)
            return 0;
        return groundProsperity / sumProsperity;
    }
}
