package Game;

import Game.Meta.MetaItem;
import Game.Timers.PeriodicTimer;
import Shared.Util;
import gs.Gs;

import javax.persistence.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/*研究所生产线*/
@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class ScienceLine {
    @Id
   public UUID id;

    @Column(name = "itemId")
    @Convert(converter = MetaItem.Converter.class)
    public MetaItem item;

    @Column(nullable = false)
    public int count;

    @Column(nullable = false)
    public int targetNum;

    @Column(nullable = false)
    public int workerNum;

    @Column(nullable = false)
    public double accumulated;

    @Column(nullable = false)
    public boolean suspend = false;

    @Column(nullable = false)
    public long ts = 0;      //生产开始时间

    public ScienceLine() {
    }

    public ItemKey newItemKey(UUID pid){
        ItemKey itemKey = new ItemKey(this.item, pid);
        return itemKey;
    }
    public boolean isPause() {
        return isComplete() || workerNum == 0;
    }
    public boolean isComplete() {
        return count >= targetNum;
    }
    @Transient
    private PeriodicTimer timer = new PeriodicTimer((int) TimeUnit.SECONDS.toMillis(1));

    public ScienceLine(MetaItem item, int targetNum, int workerNum) {
        this.id = UUID.randomUUID();
        this.item = item;
        this.count = 0;
        this.targetNum = targetNum;
        this.workerNum = workerNum;
        this.ts = System.currentTimeMillis();
    }

    public void suspend(int add) {
        this.accumulated += add;
        this.suspend = true;
    }

    public boolean isSuspend() {
        return this.suspend;
    }

    public int left() {
        return (int) Math.floor(accumulated);
    }

    public void resume() {
        this.suspend = false;
    }

    public Gs.Line toProto() {
        return Gs.Line.newBuilder()
                .setId(Util.toByteString(id))
                .setItemId(item.id)
                .setNowCount(count)
                .setTargetCount(targetNum)
                .setWorkerNum(workerNum)
                .setTs(ts)
                .build();
    }

    public UUID getId() {
        return id;
    }

    public MetaItem getItem() {
        return item;
    }

    public int getCount() {
        return count;
    }

    public int getTargetNum() {
        return targetNum;
    }

    public int getWorkerNum() {
        return workerNum;
    }

    public double getAccumulated() {
        return accumulated;
    }

    public long getTs() {
        return ts;
    }

/*    public Technology getTechnology() {
        return technology;
    }*/

    public PeriodicTimer getTimer() {
        return timer;
    }

    public int update(long diffNano,UUID onwerId) {
        int add = 0;
        if(this.timer.update(diffNano))
        {
            accumulated += item.n * this.workerNum;//Todo 未加入eva加成
            add = accumulated >= 1 ? left():0;
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


}
