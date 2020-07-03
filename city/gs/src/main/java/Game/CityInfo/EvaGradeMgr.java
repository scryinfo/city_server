package Game.CityInfo;

import Game.Eva.Eva;
import Game.Eva.EvaManager;
import Game.Meta.MetaBuilding;
import gs.Gs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EvaGradeMgr {
    // Corresponds to bt in Eva
    private static final int QUALITY = 1;
    private static final int PROMOTION = 2;
    private static final int SPEED = 3;
    private static EvaGradeMgr instance = new EvaGradeMgr();
    public static EvaGradeMgr instance() {
        return instance;
    }

    public Map<Integer, Long> queryEvaGrade(int buidingType, int item, int type) {
        Map<Integer, Long> grade = new HashMap<>();
        List<Eva> evas = new ArrayList<>();
        switch (buidingType) {
            case MetaBuilding.MATERIAL:
                // Only quality level bt = 3
                return  grade = EvaManager.getInstance().getGrade(item, SPEED);
            case MetaBuilding.PRODUCE:
                if (type == Gs.EvaGrade.GradeType.Promotion_VALUE) {
                    grade = EvaManager.getInstance().getGrade(item, PROMOTION);

                } else if (type == Gs.EvaGrade.GradeType.Quality_VALUE) {
                    grade = EvaManager.getInstance().getGrade(item, QUALITY);

                } else {
                    grade = EvaManager.getInstance().getGrade(item, SPEED);
                }
                return grade;
            case MetaBuilding.RETAIL:
                if (type == Gs.EvaGrade.GradeType.Promotion_VALUE) {
                    grade = EvaManager.getInstance().getGrade(item, PROMOTION);
                } else if (type == Gs.EvaGrade.GradeType.Quality_VALUE) {
                    grade = EvaManager.getInstance().getGrade(item, QUALITY);
                }
                return grade;
            case MetaBuilding.APARTMENT:
                if (type == Gs.EvaGrade.GradeType.Promotion_VALUE) {
                    grade = EvaManager.getInstance().getGrade(item, PROMOTION);
                } else if (type == Gs.EvaGrade.GradeType.Quality_VALUE) {
                    grade = EvaManager.getInstance().getGrade(item, QUALITY);
                }
                return grade;
            case MetaBuilding.PROMOTE:
                // Only quality level bt = 3
                return grade = EvaManager.getInstance().getGrade(item, SPEED);
            case MetaBuilding.TECHNOLOGY:
                // Only quality level bt = 3
                return  grade = EvaManager.getInstance().getGrade(item, SPEED);
            default:
                return new HashMap<>();
        }
    }
}
