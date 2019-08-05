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

    private Map<UUID, Set<SciencePoint>> sciencePointMap = new HashMap<UUID, Set<SciencePoint>>();//缓存玩家所有的科技点数
    public void init()
    {
        GameDb.getAllFromOneEntity(SciencePoint.class).forEach(
                science ->{
                    sciencePointMap.computeIfAbsent(science.getPid(),
                            k -> new HashSet<>()).add(science);
                } );
    }

    /*获取玩家的所有科技点数信息*/
    public Set<SciencePoint> getSciencePointList(UUID playerId)
    {
        return sciencePointMap.get(playerId) == null ?
                new HashSet<SciencePoint>() : sciencePointMap.get(playerId);
    }

    /*获取指定的科技点数信息*/
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

    /*更新推广点数*/
    public void updateSciencePoint(SciencePoint sciencePoint) {
        Set<SciencePoint> s=sciencePointMap.get(sciencePoint.getPid());
        s.remove(getSciencePoint(sciencePoint.getPid(),sciencePoint.getType()));
        s.add(sciencePoint);
        sciencePointMap.put(sciencePoint.getPid(), s);
        GameDb.saveOrUpdate(sciencePoint);
    }
    /*批量添加推广点数（初始化时用）*/
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
        if(point<0&&Math.abs(point)>sciencePoint.point){/*如果是扣除，还需判断库存是否充足*/
            return null;
        }
        sciencePoint.point += point;
        return sciencePoint;
    }
    /*根据建筑大类型，查找玩家的的点数资料*/
    public SciencePoint getSciencePointByBuildingType(UUID pId,int buildingType){
        //拼接类型
        StringBuffer sb = new StringBuffer("15000");
        int pointType = Integer.parseInt(sb.append(buildingType).toString());
        SciencePoint sciencePoint = getSciencePoint(pId, pointType);
        return sciencePoint;
    }
}
