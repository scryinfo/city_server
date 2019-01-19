package Game.Meta;

import org.bson.Document;

import java.util.List;
import java.util.Objects;

public class MetaTalent {

    public static final class Key {
        public Key(int buildingType, int lv) {
            this.buildingType = buildingType;
            this.lv = lv;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Key key = (Key) o;
            return buildingType == key.buildingType &&
                    lv == key.lv;
        }

        @Override
        public int hashCode() {
            return Objects.hash(buildingType, lv);
        }

        int buildingType;
        int lv;
    }
    public static int buildingType(int id) {
        return MetaItem.baseId(id)%100;
    }
    MetaTalent(Document d) {
        this.id = d.getInteger("_id");
        this.workDaysMin = d.getInteger("workDaysMin");
        this.workDaysMax = d.getInteger("workDaysMax");

        this.itemWeights = ((List<Integer>)d.get("itemWeights")).stream().mapToInt(Integer::intValue).toArray();
        assert itemWeights.length == 4;
        this.itemsV1 = (List<Integer>)d.get("itemsV1");
        this.itemsV2 = (List<Integer>)d.get("itemsV2");
        this.itemsV3 = (List<Integer>)d.get("itemsV3");
        this.itemWeights = ((List<Integer>)d.get("funcWeights")).stream().mapToInt(Integer::intValue).toArray();
        assert funcWeights.length == lv;
        this.speedRatioMin = d.getInteger("speedRatioMin");
        this.speedRatioMax = d.getInteger("speedRatioMax");
        this.qtyAddMin = d.getInteger("qtyAddMin");
        this.qtyAddMax = d.getInteger("qtyAddMax");
        this.advRatioMin = d.getInteger("advRatioMin");
        this.advRatioMax = d.getInteger("advRatioMax");
        this.keepDaysMin = d.getInteger("keepDaysMin");
        this.keepDaysMax = d.getInteger("keepDaysMax");
        this.salaryRatioMin = d.getInteger("salaryRatioMin");
        this.salaryRatioMax = d.getInteger("salaryRatioMax");
        this.nameIdxMin = d.getInteger("nameIdxMin");
        this.nameIdxMax = d.getInteger("nameIdxMax");
    }
    public int id;
    public int createSec;
    public int lv;
    public int workDaysMin;
    public int workDaysMax;
    public int nameIdxMin;
    public int nameIdxMax;
    public int[] itemWeights;
    public List<Integer> itemsV1;
    public List<Integer> itemsV2;
    public List<Integer> itemsV3;
    public int[] funcWeights;
    public int speedRatioMin;
    public int speedRatioMax;
    public int qtyAddMin;
    public int qtyAddMax;
    public int advRatioMin;
    public int advRatioMax;
    public int keepDaysMin;
    public int keepDaysMax;
    public int salaryRatioMin;
    public int salaryRatioMax;
}
