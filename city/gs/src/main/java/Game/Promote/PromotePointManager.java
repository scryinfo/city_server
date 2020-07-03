package Game.Promote;

import Game.Eva.EvaManager;
import Game.GameDb;
import Game.Technology.SciencePoint;
import Game.Technology.SciencePointManager;
import Game.Timers.PeriodicTimer;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class PromotePointManager {
    private PromotePointManager(){}
    private static PromotePointManager instance = new PromotePointManager();
    public static PromotePointManager getInstance()
    {
        return instance;
    }

    private Map<UUID, Set<PromotePoint>> promoteMap = new HashMap<UUID, Set<PromotePoint>>();//Cache all player promotion points
    public void init()
    {
        GameDb.getAllFromOneEntity(PromotePoint.class).forEach(
                promote ->{
                    promoteMap.computeIfAbsent(promote.getPid(),
                            k -> new HashSet<>()).add(promote);
                } );
    }

    /*Get all promotion points information of players*/
    public Set<PromotePoint> getPromotePointList(UUID playerId)
    {
        return promoteMap.get(playerId) == null ?
                new HashSet<PromotePoint>() : promoteMap.get(playerId);
    }

    /*Get specified promotion points information*/
    public PromotePoint getPromotePoint(UUID playerId,int type)
    {
        Set<PromotePoint> set=getPromotePointList(playerId);
        for (PromotePoint promote : set) {
            if(type==promote.type){
                return promote;
            }
        }
        return null;
    }

    /*Update promotion points*/
    public void updatePromotionPoint(PromotePoint promote) {
        Set<PromotePoint> s=promoteMap.get(promote.getPid());
        s.remove(getPromotePoint(promote.getPid(),promote.getType()));
        s.add(promote);
        promoteMap.put(promote.getPid(), s);
        GameDb.saveOrUpdate(promote);
    }
    /*Add promotion points in bulk (used during initialization)*/
    public void addPromotePointList(List<PromotePoint> promoteList){
        promoteList.forEach(p->{
            promoteMap.computeIfAbsent(p.getPid(),
                    k -> new HashSet<>()).add(p);
        });
        GameDb.saveOrUpdate(promoteList);
    }

    public Map<UUID, Set<PromotePoint>> getPromoteMap() {
        return promoteMap;
    }

    public PromotePoint updatePlayerPromotePoint(UUID pid, int type, int point){
        PromotePoint promotePoint = PromotePointManager.getInstance().getPromotePoint(pid, type);//Get the player's promotion type points
        if(point<0&&Math.abs(point)>promotePoint.promotePoint){/*If it is a deduction, it is necessary to determine whether the inventory is sufficient*/
            return null;
        }
        promotePoint.promotePoint += point;
        return promotePoint;
    }

    /*According to the type of building, find the player's point data*/
    public PromotePoint getPromotePointByBuildingType(UUID pId,int buildingType){
        //Type of stitching
        StringBuffer sb = new StringBuffer("16000");
        int pointType = Integer.parseInt(sb.append(buildingType).toString());
        PromotePoint promotePoint = getPromotePoint(pId, pointType);
        return promotePoint;
    }
}
