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

/*Eva tools*/
public class EvaTypeUtil {
    public static final int SCIENCE_TYPE=15;
    public static final int PROMOTE_TYPE=16;
    //    Judging the type of points (15 means technology points, 16 means market data)
    public static int judgeScienceType(Gs.Eva eva){
        return eva.getBt()==Gs.Eva.Btype.Brand ? PROMOTE_TYPE:SCIENCE_TYPE;
    }

    /*Get building point type*/
    public static int getEvaPointType(int type,int at){/*Parameter 1: Type of points added (technical points, market data)   bt  Eva's a type*/
        StringBuilder sb = new StringBuilder();
        /*Determine the big type*/
        if(type==SCIENCE_TYPE){
            sb.append("15000");
        }else{
            sb.append("16000");
        }
        /*Determine small type*/
        if(MetaGood.isItem(at)){//Commodities (representing processing plants)
            sb.append("12");
        }else if(MetaMaterial.isItem(at)){  //Representative raw material factory
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

    /*Get the point information of all current building types*/
    public static List<Gs.BuildingPoint> classifyBuildingTypePoint(UUID pId){/*Eva added points for various player types*/
        List<Gs.BuildingPoint> list = new ArrayList<>();
        Set<Integer> allBuildingType = MetaData.getAllBuildingType();/*All building types*/
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

    /*All building types*/
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

    /*Determine if there are enough points*/
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
