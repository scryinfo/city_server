package Game.Meta;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;

import javax.persistence.AttributeConverter;

import org.bson.Document;

import Game.CoordPair;
import Game.Coordinate;

public class MetaBuilding {
    public static final int TRIVIAL = 10;
    public static final int MATERIAL = 11;
    public static final int PRODUCE = 12;
    public static final int RETAIL = 13;
    public static final int APARTMENT = 14;
    public static final int LAB = 18;  //todo(以后删除)
    public static final int TECHNOLOGY=15;//新版研究所
    public static final int PUBLIC = 16;
    public static final int PROMOTE=17;//新版推广公司
    public static final int TALENT = 17;
    public static final int WAREHOUSE=17;//集散中心
    public static final int MAX_TYPE_ID = 20;

    public static boolean isBuilding(int id) {
        return id / MetaData.ID_RADIX <= PUBLIC;
    }

    public static boolean canAd(int type) {
        return type == RETAIL || type == APARTMENT || type == PUBLIC || type == TALENT;
    }
    public static int type(int id) {
        return id/MetaData.ID_RADIX;
    }
    MetaBuilding(Document d) {
        this.id = d.getInteger("_id");
        this.x = d.getInteger("x");
        this.y = d.getInteger("y");
        if(d.containsKey("workerNum")) {
            this.workerNum = d.getInteger("workerNum");
            this.effectRange = d.getInteger("effectRange");
            for (int i = 0; i < 4; i++) {
                int type = d.getInteger("npcType" + i);
                int n = d.getInteger("npcNum" + i);
                if (n > 0)
                    npc.put(type, n);
            }
//            if (npc.values().stream().mapToInt(n -> n).sum() != workerNum)
//                throw new IllegalArgumentException();
            for (int i = 0; i < startWorkHour.length; i++) {
                int[] startWorkHour = ((List<Integer>)d.get("startWorkHour" + i)).stream().mapToInt(Integer::valueOf).toArray();
                int[] workingHours = ((List<Integer>)d.get("workingHours" + i)).stream().mapToInt(Integer::valueOf).toArray();
                if(startWorkHour.length != workingHours.length)
                    throw new IllegalArgumentException();
                this.startWorkHour[i] = startWorkHour;
                this.endWorkHour[i] = IntStream.range(0, startWorkHour.length).map(idx->startWorkHour[idx]+workingHours[idx]).toArray();
                Arrays.sort(this.startWorkHour[i]);
                Arrays.sort(this.endWorkHour[i]);
            }
            this.salary = d.getInteger("salary");
            this.talentNum = d.getInteger("talentNum");
        }
    }
    public int id;
    public int x;
    public int y;
    public int workerNum;
    public int effectRange;
    public Map<Integer, Integer> npc = new TreeMap<>();
    public int[][] startWorkHour = {{},{},{},{}};
    public int[][] endWorkHour = {{},{},{},{}};
    public int salary;
    public int talentNum;
    public static boolean legalType(int type) {
        return type >= TRIVIAL && type <= TALENT;
    }
    
    public static final class Converter implements AttributeConverter<MetaBuilding, Integer> {
        @Override
        public Integer convertToDatabaseColumn(MetaBuilding attribute) {
            return attribute.id;
        }

        @Override
        public MetaBuilding convertToEntityAttribute(Integer dbData) {
            return MetaData.getBuilding(dbData);
        }
    }
    public CoordPair area(Coordinate pos) {
        return new CoordPair(pos, pos.offset(this.x-1, this.y-1));
    }

    /*判断是否是建筑，根据基础type，比如14 、13....*/
    public static boolean isBuildingByBaseType(int type){
        return  type<=PUBLIC&&type>=TRIVIAL;
    }
}
