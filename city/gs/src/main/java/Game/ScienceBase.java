package Game;

import Game.Meta.MetaBuilding;
import Game.Meta.MetaItem;
import Game.Timers.PeriodicTimer;
import Shared.Package;
import Shared.Util;
import com.google.protobuf.InvalidProtocolBufferException;
import gs.Gs;
import gscode.GsCode;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/*研究所与推广公司公用信息*/
@Entity
public abstract class ScienceBase extends Building{
    private static final int DB_UPDATE_INTERVAL_MS = 30000;
    //科技资料库
    @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "store_id")
    protected ScienceStore store;
    //科技资料出售
    @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "shelf_id")
    protected ScienceShelf shelf;
    //生产线
    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKeyColumn(name = "line_id")
    public List<ScienceLine> line= new ArrayList<>();


    @Transient
    protected ScienceLine delLine=null;//当前要删除的生产线

    public ScienceBase() {
    }
    public ScienceBase(MetaBuilding meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.store = new ScienceStore();
        this.shelf = new ScienceShelf();
    }

    @Transient
    protected PeriodicTimer dbTimer = new PeriodicTimer(DB_UPDATE_INTERVAL_MS, (int) (Math.random()*DB_UPDATE_INTERVAL_MS));

    protected abstract ScienceLine addLine(MetaItem item, int workerNum, int targetNum);
    protected abstract boolean shelfAddable(ItemKey k);
    protected abstract void _1() throws InvalidProtocolBufferException;

    protected void __addLine(ScienceLine newLine){
        if(line.indexOf(newLine.getId()) < 0){
            line.add(newLine);
        }
    }

    protected  boolean __hasLineRemained(){
        return line.size() > 0;
    }

    public ScienceLine __delLine(UUID lineId) {
        for (int i = line.size() - 1; i >= 0 ; i--) {
            if (line.get(i).getId().equals(lineId)){
                ScienceLine remove = line.remove(i);
                if(line.size() > 0){
                    if(i==0) {//如果删除的就是当前生产线，第一条，则设置移除后的第一条为当前生产时间
                        line.get(0).ts = System.currentTimeMillis();
                    }
                }
                return remove;
            }
        }
        return null;
    }

    public boolean setAutoReplenish(ItemKey key, boolean autoRepOn) {
        ScienceShelf.Content i = this.shelf.getContent(key);
        if(i == null)
            return false;
        this.shelf.add(new Item(key,0),i.price,autoRepOn);
        return  true;
    }

    public void updateAutoReplenish(ItemKey key){
        this.shelf.updateAutoReplenish(this,key);
    }

    public boolean shelfSet(Item item, int price) {
        ScienceShelf.Content content = this.shelf.getContent(item.getKey());
        if(content == null)
            return false;
        //不需要在此处处理自动补货，自动补货是一个单独的协议
        int updateNum = content.n - item.getN();//增加或减少：当前货架数量-现在货架数量
        if(content.n==0&&item.getN()==0){//若非自动补货，切货架数量为0，直接删除
            delshelf(item.getKey(), content.n, true);
            return true;
        }
        boolean lock = false;
        if (updateNum < 0) {
            lock = this.store.lock(item.getKey(),Math.abs(updateNum));
        } else {
            lock = this.store.unLock(item.getKey(), updateNum);
        }
        if (lock) {
            content.price = price;
            content.n = item.getN();
            //消息推送货物发生改变
            this.sendToWatchers(id(),item.getKey().meta.id,item.getN(),price,content.autoReplenish,null);
            return true;
        } else
            return false;
    }

    public boolean checkShelfSlots(ItemKey key,int num){
        ScienceShelf.Content content = this.shelf.getContent(key);
        if(content==null||content.n<num){
            return false;
        }else{
            return true;
        }
    }

    public boolean addshelf(Item item, int price, boolean autoReplenish) {
        if(!shelfAddable(item.getKey()) || !this.store.has(item.getKey(),item.getN()))
            return false;
        if(this.shelf.add(item, price,autoReplenish)) {
            this.store.lock(item.getKey(), item.getN());
            return true;
        }
        else
            return false;
    }

    public boolean delshelf(ItemKey id, int n, boolean unLock) {
        if(this.shelf.del(id, n)) {
            if(unLock)
                this.store.unLock(id, n);
            else{//如果是消费，那么需要消费lock的数量
                this.store.consumeLock(id, n);
            }
            return true;
        }
        return false;
    }

    public void delComplementLine(List<UUID> completedLines){/*删除已完成的线*/
        if (completedLines.size() > 0){
            UUID nextId = null;
            if(line.size() >= 2){
                nextId = line.get(1).id; //第二条生产线
            }
            ScienceLine l= __delLine(completedLines.get(0));
            delLine = l;
            if(nextId != null){
                this.sendToWatchers(Package.create(GsCode.OpCode.ftyDelLine_VALUE, Gs.DelLine.newBuilder().setBuildingId(Util.toByteString(id())).setLineId(Util.toByteString(l.id)).setNextlineId(Util.toByteString(nextId)).build()));
            }else{
                this.sendToWatchers(Package.create(GsCode.OpCode.ftyDelLine_VALUE, Gs.DelLine.newBuilder().setBuildingId(Util.toByteString(id())).setLineId(Util.toByteString(l.id)).build()));
            }
            //生产线完成通知
            MailBox.instance().sendMail(Mail.MailType.PRODUCTION_LINE_COMPLETION.getMailType(), ownerId(), new int[]{metaBuilding.id}, new UUID[]{this.id()}, new int[]{l.item.id, l.targetNum});
        }
    }

    public void saveAndUpdate(long diffNano){
        if(this.dbTimer.update(diffNano)) {
            GameDb.saveOrUpdate(this);
            if(delLine!=null){
                GameDb.delete(delLine);
                delLine=null;
            }
        }
    }
    public ScienceShelf.Content getContent(ItemKey key){
        return  this.shelf.getContent(key);
    }

    public void cleanData(){
        //删除生产线
        GameDb.delete(line);
        this.line.clear();
        this.store.cleanData();
        this.shelf.cleanData();
    }

    public ScienceStore getStore() {
        return store;
    }

    public ScienceShelf getShelf() {
        return shelf;
    }

    public List<ScienceLine> getLine() {
        return line;
    }
}
