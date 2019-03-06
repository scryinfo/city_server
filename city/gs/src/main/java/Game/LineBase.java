package Game;

import Game.Meta.MetaItem;
import Game.Timers.PeriodicTimer;
import Shared.Util;
import gs.Gs;

import javax.persistence.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class LineBase {
    @Id
    UUID id;

    @Column(name = "itemId")
    @Convert(converter = MetaItem.Converter.class)
    MetaItem item;

    @Column(nullable = false)
    int count;

    @Column(nullable = false)
    int targetNum;

    @Column(nullable = false)
    int workerNum;

    @Column(nullable = false)
    double accumulated;

    @Column(nullable = false) // if don't save this, we need to query player in each produce action
    int itemLevel;

    @Column(nullable = false)
    boolean suspend = false;

    @Column(nullable = false)
    long ts = 0;      //生产开始时间

    protected LineBase() {
    }
    public abstract ItemKey newItemKey(UUID producerId, int qty);
    public boolean isPause() {
        return isComplete() || workerNum == 0;
    }
    public boolean isComplete() {
        return count >= targetNum;
    }
    @Transient
    private PeriodicTimer timer = new PeriodicTimer((int) TimeUnit.SECONDS.toMillis(1));

    public LineBase(MetaItem item, int targetNum, int workerNum, int itemLevel) {
        this.id = UUID.randomUUID();
        this.item = item;
        this.count = 0;
        this.targetNum = targetNum;
        this.workerNum = workerNum;
        this.itemLevel = itemLevel;
        this.ts = System.currentTimeMillis();
    }

    Gs.Line toProto() {
        return Gs.Line.newBuilder()
                .setId(Util.toByteString(id))
                .setItemId(item.id)
                .setNowCount(count)
                .setTargetCount(targetNum)
                .setWorkerNum(workerNum)
                .setTs(ts)
                .build();
    }

    boolean materialConsumed = false;
    int update(long diffNano) {
        int add = 0;
        if(this.timer.update(diffNano))
        {
            accumulated += item.n * this.workerNum;
//            add = left(); //奇怪为什么这里要用四舍五入的算法
            add = accumulated >= 1 ? 1:0;
            if(add > 0) {
                this.count += add;
                if(this.count >= this.targetNum) {
                    add -= this.count - this.targetNum;
                    this.count = this.targetNum;
                    this.accumulated = 0;
                }
                else
                    accumulated -= add;
            }
        }
        return add;
    }

    public void suspend(int add) {
        this.accumulated += add;
        this.suspend = true;
    }

    public boolean isSuspend() {
        return this.suspend;
    }

    public int left() {
        return (int) Math.round(accumulated);
    }

    public void resume() {
        this.suspend = false;
    }
}
