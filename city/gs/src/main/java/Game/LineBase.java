package Game;

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

    protected LineBase() {
    }

    boolean isPause() {
        return count >= targetNum || workerNum == 0;
    }
    @Transient
    private PeriodicTimer timer = new PeriodicTimer((int) TimeUnit.SECONDS.toMillis(1));
//    public LineBase(Db.Lines.Line d) {
//        this.id = new ObjectId(d.getId().toByteArray());
//        this.item = MetaData.getItem(d.getItemId());
//        this.count = d.getNowCount();
//        this.targetNum = d.getTargetCount();
//        this.workerNum = d.getWorkerNum();
//    }
    public LineBase(MetaItem item, int targetNum, int workerNum) {
        this.id = UUID.randomUUID();
        this.item = item;
        this.count = 0;
        this.targetNum = targetNum;
        this.workerNum = workerNum;
    }
//    Db.Lines.Line toDbProto() {
//        return Db.Lines.Line.newBuilder()
//                .setId(Util.toByteString(id))
//                .setItemId(item.id)
//                .setNowCount(count)
//                .setTargetCount(targetNum)
//                .setWorkerNum(workerNum)
//                .build();
//    }
    Gs.Line toProto() {
        return Gs.Line.newBuilder()
                .setId(Util.toByteString(id))
                .setItemId(item.id)
                .setNowCount(count)
                .setTargetCount(targetNum)
                .setWorkerNum(workerNum)
                .build();
    }
    int update(long diffNano) {
        int add = 0;
        if(!isPause() && this.timer.update(diffNano))
        {
            add = (int) (item.n * this.workerNum);
            this.count += add;
        }
        return add;
    }
}
