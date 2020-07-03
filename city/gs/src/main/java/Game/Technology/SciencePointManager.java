package Game.Technology;

import Game.GameDb;
import Game.Promote.PromotePoint;
import Game.Promote.PromotePointManager;

import java.util.*;

public class SciencePointManager {
    private SciencePointManager(){}
    private static SciencePointManager instance = new SciencePointManager();
    public static SciencePointManager getInstance()
    {
        return instance;
    }

    private Map<UUID, Set<SciencePoint>> sciencePointMap = new HashMap<UUID, Set<SciencePoint>>();//Cache all player technology points
    public void init()
    {
        GameDb.getAllFromOneEntity(SciencePoint.class).forEach(
                science ->{
                    sciencePointMap.computeIfAbsent(science.getPid(),
                            k -> new HashSet<>()).add(science);
                } );
    }

    /*Get all the technology points of players*/
    public Set<SciencePoint> getSciencePointList(UUID playerId)
    {
        return sciencePointMap.get(playerId) == null ?
                new HashSet<SciencePoint>() : sciencePointMap.get(playerId);
    }

    /*Get the specified technology points information*/
    public SciencePoint getSciencePoint(UUID playerId,int type)
    {
        Set<SciencePoint> set=getSciencePointList(playerId);
        for (SciencePoint science : set) {
            if(type==science.type){
                return science;
            }
        }
        return null;
    }

    /*Update promotion points*/
    public void updateSciencePoint(SciencePoint sciencePoint) {
        Set<SciencePoint> s=sciencePointMap.get(sciencePoint.getPid());
        s.remove(getSciencePoint(sciencePoint.getPid(),sciencePoint.getType()));
        s.add(sciencePoint);
        sciencePointMap.put(sciencePoint.getPid(), s);
        GameDb.saveOrUpdate(sciencePoint);
    }
    /*Add promotion points in bulk (used during initialization)*/
    public void addSciencePointList(List<SciencePoint> sciencePoints){
        sciencePoints.forEach(p->{
            sciencePointMap.computeIfAbsent(p.getPid(),
                    k -> new HashSet<>()).add(p);
        });
        GameDb.saveOrUpdate(sciencePoints);
    }
    public Map<UUID, Set<SciencePoint>> getSciencePointMap() {
        return sciencePointMap;
    }

    public SciencePoint updateSciencePoint(UUID pid,int type,int point){
        SciencePoint sciencePoint = SciencePointManager.getInstance().getSciencePoint(pid, type);
        if(point<0&&Math.abs(point)>sciencePoint.point){/*If it is a deduction, it is necessary to determine whether the inventory is sufficient*/
            return null;
        }
        sciencePoint.point += point;
        return sciencePoint;
    }
    /*According to the type of building, find the player's point data*/
    public SciencePoint getSciencePointByBuildingType(UUID pId,int buildingType){
        //Type of stitching
        StringBuffer sb = new StringBuffer("15000");
        int pointType = Integer.parseInt(sb.append(buildingType).toString());
        SciencePoint sciencePoint = getSciencePoint(pId, pointType);
        return sciencePoint;
    }
}
