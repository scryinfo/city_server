package Game.Meta;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class MetaCityLevel {
    public int lv;  //级别
    public long exp;//经验值
    public int baseSalary;//基础工资
    public int inventCount;//发明数量
    public List<Integer> inventItem = new ArrayList<>();//发明类型
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
