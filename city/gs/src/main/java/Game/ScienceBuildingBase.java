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
import java.util.Map;
import java.util.UUID;

/*Institute and promotion company public information*/
@Entity
public abstract class ScienceBuildingBase extends Building{
    private static final int DB_UPDATE_INTERVAL_MS = 30000;
    //Technology Database
    @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "store_id")
    public ScienceStore store;
    //Technical information for sale
    @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "shelf_id")
    public ScienceShelf shelf;
    //production line
    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKeyColumn(name = "line_id")
    public List<ScienceLineBase> line= new ArrayList<>();


    @Transient
    protected ScienceLineBase delLine=null;//The current production line to be deleted

    public ScienceBuildingBase() {
    }
    public ScienceBuildingBase(MetaBuilding meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.store = new ScienceStore();
        this.shelf = new ScienceShelf();
    }

    @Transient
    protected PeriodicTimer dbTimer = new PeriodicTimer(DB_UPDATE_INTERVAL_MS, (int) (Math.random()*DB_UPDATE_INTERVAL_MS));

    protected abstract ScienceLineBase addLine(MetaItem item, int workerNum, int targetNum);
    protected abstract boolean shelfAddable(ItemKey k);
    protected abstract void _1() throws InvalidProtocolBufferException;

    protected void __addLine(ScienceLineBase newLine){
        if(line.indexOf(newLine.getId()) < 0){
            line.add(newLine);
        }
    }

    protected  boolean __hasLineRemained(){
        return line.size() > 0;
    }

    public ScienceLineBase __delLine(UUID lineId) {
        for (int i = line.size() - 1; i >= 0 ; i--) {
            if (line.get(i).getId().equals(lineId)){
                ScienceLineBase remove = this.line.remove(i);
                if(line.size() > 0){
                    if(i==0) {//If the deletion is the current production line, the first one, then set the first one after removal as the current production time
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

    public boolean shelfSet(Item item, int price,boolean autoRepOn) {
        ScienceShelf.Content content = this.shelf.getContent(item.getKey());
        if(content == null)
            return false;
        if(!autoRepOn) {
            int updateNum = content.n - item.getN();//Increase or decrease: current number of shelves-current number of shelves
            if (content.n == 0 && item.getN() == 0) {//If it is not automatic replenishment, and the number of shelves is 0, delete it directly
                content.autoReplenish=autoRepOn;
                delshelf(item.getKey(), content.n, true);
                return true;
            }
            boolean lock = false;
            if (updateNum < 0) {
                lock = this.store.lock(item.getKey(), Math.abs(updateNum));
            } else {
                lock = this.store.unLock(item.getKey(), updateNum);
            }
            if (lock) {
                content.price = price;
                content.n = item.getN();
                content.autoReplenish=autoRepOn;
                //Message push goods change
                this.sendToWatchers(id(), item.getKey().meta.id, item.getN(), price, content.autoReplenish, null);
                return true;
            } else
                return false;
        }else{
            //1.Set price
            content.price = price;
            content.autoReplenish = autoRepOn;
            updateAutoReplenish(item.getKey());//Update automatic replenishment
            //Push
            this.sendToWatchers(id(),item.key.meta.id,content.n,price,content.autoReplenish,null);//forward news
            return true;
        }
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
        if(autoReplenish){/*If it is automatic replenishment, set the quantity to the maximum quantity of the commodity in the warehouse*/
            item.n = this.store.getItemCount(item.getKey());
        }
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
            else{//If it is consumption, then the number of locks that need to be consumed
                this.store.consumeLock(id, n);
            }
            return true;
        }
        return false;
    }

    public void delComplementLine(List<UUID> completedLines){/*Delete completed line*/
        if (completedLines.size() > 0){
            UUID nextId = null;
            if(line.size() >= 2){
                nextId = line.get(1).id; //The second production line
            }
            ScienceLineBase l= __delLine(completedLines.get(0));
            delLine = l;
            if(nextId != null){
                this.sendToWatchers(Package.create(GsCode.OpCode.ftyDelLine_VALUE, Gs.DelLine.newBuilder().setBuildingId(Util.toByteString(id())).setLineId(Util.toByteString(l.id)).setNextlineId(Util.toByteString(nextId)).build()));
            }else{
                this.sendToWatchers(Package.create(GsCode.OpCode.ftyDelLine_VALUE, Gs.DelLine.newBuilder().setBuildingId(Util.toByteString(id())).setLineId(Util.toByteString(l.id)).build()));
            }
            //Production line completion notice
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
        //Delete production line
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

    public List<ScienceLineBase> getLine() {
        return line;
    }
    public boolean hasEnoughPintInStore(ItemKey key,int num){
        return this.store.has(key, num);
    }

    public int getIndex(UUID lineId){/*Get the subscript corresponding to the production line*/
        for (ScienceLineBase l : line) {
            if (l.id.equals(lineId)) {
                int index = line.indexOf(l);
                return index;
            }
        }
        return -1;
    }
    public Map<Item, Integer> getSaleDetail(int itemId) {
        return this.shelf.getSaleDetail(itemId);
    }
}
