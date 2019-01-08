package Game;

import Shared.Util;
import gs.Gs;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
public class Talent {
    public int buildingType() {
        return MetaTalent.buildingType(metaId);
    }

    public void inBuilding(UUID id) {
        this.allocType = AllocType.IN_BUILDING;
        this.buildingId = id;
    }
    public void outBuilding(UUID id) {
        this.allocType = AllocType.NONE;
        this.buildingId = null;
    }

    public boolean isFree() {
        return this.allocType == AllocType.NONE;
    }

    public void addMoney(int cost) {
        this.money += cost;
        this.payed = true;
    }
    public boolean payed() {
        return this.payed;
    }

    public void setMoney(long money) {
        this.money = money;
    }

    public enum Type {
        ANY,
        ITEM,
        ITEM_SUB,
        ITEM_ID
    }
    public enum AllocType {
        NONE,
        IN_BUILDING,
        IN_TRADING
    }
    private AllocType allocType;
    public UUID id() {
        return id;
    }

    @Id
    private UUID id;
    public UUID getOwnerId() {
        return ownerId;
    }
    private UUID ownerId;
    private boolean payed;
    private long money;
    public long money() {
        return money;
    }
    private int metaId;
    private int lv;
    private int workDays;
    public int getWorkDays() {
        return workDays;
    }
    @Enumerated(EnumType.ORDINAL)
    private Type apply;
    private int itemV;

    @Embeddable
    public static final class Skill {
        public enum Type {
            SPEED,
            QTY,
            ADV
        }
        @Enumerated(EnumType.ORDINAL)
        Type type;
        int v;
    }
    public Gs.Talent toProto() {
        Gs.Talent.Builder builder = Gs.Talent.newBuilder();
        builder.setCreateTs(this.createTs)
                .setDurationDay(workDays)
                .setId(Util.toByteString(id()))
                .setKeepDay(keepDays)
                .setMId(metaId)
                .setSalaryRatio(salaryRatio)
                .setNameIdx(nameIdx);
        skills.forEach(s->builder.addSkill(Gs.Talent.Skill.newBuilder().setType(s.type.ordinal()).setV(s.v)));
        return builder.build();
    }
    @ElementCollection
    private List<Skill> skills = new ArrayList<>();
    private int keepDays;
    private int salaryRatio;
    public int getSalaryRatio() {
        return salaryRatio;
    }
    private int nameIdx;
    private long createTs;
    private UUID buildingId;
    public Talent(MetaTalent meta, UUID ownerId) {
        this.id = UUID.randomUUID();
        this.allocType = AllocType.NONE;
        this.metaId = meta.id;
        this.ownerId = ownerId;
        this.createTs = System.currentTimeMillis();
        this.lv = meta.lv;
        this.workDays = Util.random(meta.workDaysMin, meta.workDaysMax);
        this.keepDays = Util.random(meta.keepDaysMin, meta.keepDaysMax);
        this.salaryRatio = Util.random(meta.salaryRatioMin, meta.salaryRatioMax);
        this.apply = Type.values()[ProbBase.randomIdx(meta.itemWeights)];
        this.nameIdx = Util.random(meta.nameIdxMin, meta.nameIdxMax);
        this.payed = false;
        switch (this.apply) {
            case ANY:
                break;
            case ITEM:
                itemV = Util.randomChoose(meta.itemsV1);
                break;
            case ITEM_SUB:
                itemV = Util.randomChoose(meta.itemsV2);
                break;
            case ITEM_ID:
                itemV = Util.randomChoose(meta.itemsV3);
                break;
        }
        for (int i = 0; i < this.lv; i++) {
            Skill skill = new Skill();
            skill.type = Skill.Type.values()[ProbBase.randomIdx(meta.funcWeights)];
            switch (skill.type) {
                case SPEED:
                    skill.v = Util.random(meta.speedRatioMin, meta.speedRatioMax);
                    break;
                case QTY:
                    skill.v = Util.random(meta.qtyAddMin, meta.qtyAddMax);
                    break;
                case ADV:
                    skill.v = Util.random(meta.advRatioMin, meta.advRatioMax);
                    break;
            }
            skills.add(skill);
        }
    }
    public boolean apply(int metaId) {
        boolean apply = false;
        switch (this.apply) {
            case ANY:
                apply = true;
                break;
            case ITEM:
                apply = (itemV == MetaItem.type(metaId));
                break;
            case ITEM_SUB:
                apply = (itemV == MetaItem.baseId(metaId));
                break;
            case ITEM_ID:
                apply = (itemV == metaId);
                break;
        }
        return apply;
    }
}
