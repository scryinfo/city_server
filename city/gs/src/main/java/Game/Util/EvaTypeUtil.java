package Game.Util;

import Game.Meta.*;
import Game.Promote.PromotePoint;
import Game.Promote.PromotePointManager;
import Game.Technology.SciencePoint;
import Game.Technology.SciencePointManager;
import Shared.Util;
import gs.Gs;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/*Eva工具*/
public class EvaTypeUtil {
    public static final int SCIENCE_TYPE=15;
    public static final int PROMOTE_TYPE=16;
    //    判断点数类型(15 表示科技点数，16表示市场数据)
    public static int judgeScienceType(Gs.Eva eva){
        return eva.getBt()==Gs.Eva.Btype.Brand ? PROMOTE_TYPE:SCIENCE_TYPE;
    }

    /*获取建筑加点类型*/
    public static int getEvaPointType(int type,int at){/*参数1：加点类型（科技点数、市场数据）   bt  Eva的a类型*/
        StringBuilder sb = new StringBuilder();
        /*确定大类型*/
        if(type==SCIENCE_TYPE){
            sb.append("15000");
        }else{
            sb.append("16000");
        }
        /*确定小类型*/
        if(MetaGood.isItem(at)){//商品（代表加工厂）
            sb.append("12");
        }else if(MetaMaterial.isItem(at)){  //代表原料厂
            sb.append("11");
        }else if(at==MetaBuilding.APARTMENT){
            sb.append(MetaBuilding.APARTMENT);
        }else if(at==MetaBuilding.RETAIL){
            sb.append(MetaBuilding.RETAIL);
        }else if(MetaPromotionItem.isItem(at)){
            sb.append(MetaBuilding.PROMOTE);
        }else if(MetaScienceItem.isItem(at)){
            sb.append(MetaBuilding.TECHNOLOGY);
        }
        int pointType = Integer.parseInt(sb.toString().intern());
        return pointType;
    }

    /*获取当前所有建筑类型的点数信息*/
    public static List<Gs.BuildingPoint> classifyBuildingTypePoint(UUID pId){/*分类玩家各种建筑类型的Eva加点*/
        List<Gs.BuildingPoint> list = new ArrayList<>();
        Set<Integer> allBuildingType = MetaData.getAllBuildingType();/*所有的建筑类型*/
        allBuildingType.forEach(bt->{
            SciencePoint sciencePoint = SciencePointManager.getInstance().getSciencePointByBuildingType(pId, bt);
            PromotePoint promotePoint = PromotePointManager.getInstance().getPromotePointByBuildingType(pId, bt);
            Gs.BuildingPoint.Builder buildingPoint = Gs.BuildingPoint.newBuilder();
            buildingPoint.setBuildingType(bt);
            if(sciencePoint!=null){
                Gs.BuildingPoint.PointInfo.Builder pointInfo = Gs.BuildingPoint.PointInfo.newBuilder();
                pointInfo.setPointId(sciencePoint.getType()).setPointNum(sciencePoint.point);
                buildingPoint.setSciencePoint(pointInfo);
            }
            if(promotePoint!=null){
                Gs.BuildingPoint.PointInfo.Builder pointInfo = Gs.BuildingPoint.PointInfo.newBuilder();
                pointInfo.setPointId(promotePoint.getType()).setPointNum(promotePoint.promotePoint);
                buildingPoint.setPromotionPoint(pointInfo);
            }
            list.add(buildingPoint.build());
        });
        return list;
    }

    /*获取建筑类型的点数信息*/
    public static Gs.BuildingPoint getBuildingTypePoint(UUID pId,int type){
        SciencePoint sciencePoint = SciencePointManager.getInstance().getSciencePointByBuildingType(pId, type);
        PromotePoint promotePoint = PromotePointManager.getInstance().getPromotePointByBuildingType(pId, type);
        Gs.BuildingPoint.Builder buildingPoint = Gs.BuildingPoint.newBuilder();
        buildingPoint.setBuildingType(type);
        if(sciencePoint!=null){
            Gs.BuildingPoint.PointInfo.Builder pointInfo = Gs.BuildingPoint.PointInfo.newBuilder();
            pointInfo.setPointId(sciencePoint.getType()).setPointNum(sciencePoint.point);
            buildingPoint.setSciencePoint(pointInfo);
        }
        if(promotePoint!=null){
            Gs.BuildingPoint.PointInfo.Builder pointInfo = Gs.BuildingPoint.PointInfo.newBuilder();
            pointInfo.setPointId(promotePoint.getType()).setPointNum(promotePoint.promotePoint);
            buildingPoint.setPromotionPoint(pointInfo);
        }
        return buildingPoint.build();
    }

    /*判断是否有足够的点数*/
    public static boolean hasEnoughPoint(Gs.Evas evas){
        UUID playerId=null;
        for (Gs.Eva eva : evas.getEvaList()) {
            if (playerId == null) {
                playerId = Util.toUuid(eva.getPid().toByteArray());
            }
            if(EvaTypeUtil.judgeScienceType(eva)==EvaTypeUtil.PROMOTE_TYPE){
                int pointType = EvaTypeUtil.getEvaPointType(EvaTypeUtil.PROMOTE_TYPE, eva.getAt());
                PromotePoint promotePoint = PromotePointManager.getInstance().getPromotePoint(playerId, pointType);
                if(promotePoint.promotePoint<eva.getCexp()){
                    return false;
                }
            }
            else {
                int pointType = EvaTypeUtil.getEvaPointType(EvaTypeUtil.SCIENCE_TYPE, eva.getAt());
                SciencePoint sciencePoint = SciencePointManager.getInstance().getSciencePoint(playerId, pointType);
                if (sciencePoint.point < eva.getCexp()) {
                    return false;
                }
            }
        }
        return true;
    }
}
