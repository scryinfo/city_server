package Game.Meta;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class MetaCityLevel {
    public int lv;  //level
    public long exp;//Experience
    public int baseSalary;//Base salary
    public int inventCount;//Number of inventions
    public List<Integer> inventItem = new ArrayList<>();//Type of invention
    public MetaCityLevel() {
    }
    public MetaCityLevel(Document d) {
        super();
        this.lv = d.getInteger("lv");
        this.exp = d.getInteger("exp");
        this.baseSalary = d.getInteger("base_p");
        this.inventCount = d.getInteger("inventCount");
        inventItem = (List<Integer>) d.get("inventType");
    }

    public int getLv() {
        return lv;
    }

    public long getExp() {
        return exp;
    }

    public int getBaseSalary() {
        return baseSalary;
    }

    public int getInventCount() {
        return inventCount;
    }

    public List<Integer> getInventItem() {
        return inventItem;
    }
}
