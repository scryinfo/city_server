package Game;

import Game.Meta.MetaItem;
import Game.Meta.MetaPublicFacility;
import Game.Timers.PeriodicTimer;
import Shared.LogDb;
import Shared.Package;
import Shared.Util;
import com.google.protobuf.Message;
import gs.Gs;
import gscode.GsCode;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Entity(name = "PublicFacility")
public class PublicFacility extends Building {
    public PublicFacility(MetaPublicFacility meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
        this.qty = meta.qty;
    }
    @Column(nullable = false)
    protected int qty;

    public int getMaxDayToRent() {
        return meta.maxDayToRent;
    }
    public int getMinDayToRent() { return meta.minDayToRent; }
    public int getMaxRentPreDay() {
        return meta.maxRentPreDay;
    }

    @Transient
    private MetaPublicFacility meta;
    private static final int MAX_SLOT_NUM = 999;

    public void setTickPrice(int price) {
        this.tickPrice = price;
    }

    @Override
    public int cost() {
        return this.tickPrice;
    }
    @Entity
    public static final class Slot {
        protected Slot(){}
        public Slot(int maxDayToRent, int minDayToRent, int rentPreDay, int deposit) {
            this.maxDayToRent = maxDayToRent;
            this.minDayToRent = minDayToRent;
            this.rentPreDay = rentPreDay;
            this.deposit = deposit;
        }
        @Id
        final UUID id = UUID.randomUUID();
        int maxDayToRent;
        int minDayToRent;
        int rentPreDay;
        int deposit;

        Gs.Advertisement.Slot toProto() {
            Gs.Advertisement.Slot.Builder builder = Gs.Advertisement.Slot.newBuilder();
            return builder.setId(Util.toByteString(id))
                    .setMaxDayToRent(maxDayToRent)
                    .setMinDayToRent(minDayToRent)
                    .setRentPreDay(rentPreDay)
                    .setDeposit(deposit)
                    .build();
        }
    }
    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKey(name = "id")
    @JoinColumn(name = "public_facility_id")
    private Map<UUID, Slot> slot = new HashMap<>();

    @Entity
    public static final class SlotRent {
        protected SlotRent(){}
        public SlotRent(Slot slot, int day, UUID renterId) {
            this.slot = slot;
            this.day = day;
            this.beginTs = System.currentTimeMillis();
            this.renterId = renterId;
            this.payTs = beginTs;
        }

        @Id
        private UUID id; // for hibernate only, don't use it

        @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
        @JoinColumn(name = "slot_id")
        @MapsId
        Slot slot;
        int day;
        long beginTs;
        UUID renterId;
        long payTs;
        Gs.Advertisement.SoldSlot toProto() {
            Gs.Advertisement.SoldSlot.Builder builder = Gs.Advertisement.SoldSlot.newBuilder();
            return builder.setS(slot.toProto())
                    .setBeginTs(beginTs)
                    .setDays(day)
                    .setRenterId(Util.toByteString(renterId))
                    .build();
        }
    }
    @Transient
    PeriodicTimer adTimer = new PeriodicTimer(5000);

    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKeyColumn(name = "slot_id")
    @JoinColumn(name = "public_facility_id")
    private Map<UUID, SlotRent> rent = new HashMap<>();

    @Entity
    public static final class Ad {
        public Ad(SlotRent sr, int metaId, Type type) {
            this.sr = sr;
            this.metaId = metaId;
            this.type = type;
            this.beginTs = System.currentTimeMillis();
        }

        @Id
        final UUID id = UUID.randomUUID();
        @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
        @JoinColumn(name = "slot_rent_id")
        SlotRent sr;

        // building type or good meta id
        int metaId;

        protected Ad() {
        }

        enum Type {
            GOOD,
            BUILDING
        }
        Type type;
        long beginTs;
        int npcFlow;

        Gs.Advertisement.Ad toProto() {
            Gs.Advertisement.Ad.Builder builder = Gs.Advertisement.Ad.newBuilder();
            if(sr != null)
                builder.setSlot(sr.toProto());
            return builder.setId(Util.toByteString(id))
                    .setMetaId(metaId)
                    .setType(Gs.Advertisement.Ad.Type.valueOf(type.ordinal()))
                    .setBeginTs(beginTs)
                    .setNpcFlow(npcFlow)
                    .build();
        }
    }

    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKey(name = "id")
    @JoinColumn(name = "public_facility_id")
    // key is ad.id not slot.id
    private Map<UUID, Ad> ad = new HashMap<>();


    public Slot addSlot(int maxDay, int minDay, int rent) {
        if(slot.size() >= MAX_SLOT_NUM)
            return null;
        Slot s = new Slot(maxDay, minDay, rent, rent*meta.depositRatio);
        this.slot.put(s.id, s);
        return s;
    }
    public boolean delSlot(UUID id) {
        if(rent.containsKey(id))
            return false;
        this.slot.remove(id);
        return true;
    }

    public Slot getSlot(UUID id) {
        return slot.get(id);
    }
    public boolean isSlotRentOut(UUID id) {return getRentSlot(id) != null;}
    public SlotRent getRentSlot(UUID id) {
        return rent.get(id);
    }
    public boolean slotCanBuy(UUID id) {
        return slot.containsKey(id) && !rent.containsKey(id);
    }
    public void buySlot(UUID id, int day, UUID renterId) {
        rent.put(id, new SlotRent(slot.get(id), day, renterId));
    }
    public boolean hasAd(UUID slotId) {
        for(Ad ad : ad.values()) {
            if(ad.sr != null && ad.sr.slot.id.equals(slotId))
                return true;
        }
        return false;
    }
    public PublicFacility.Ad getAd(UUID id) {
        return ad.get(id);
    }
    public void delAd(UUID id) {
        ad.remove(id);
        qty -= 1;
    }
    public Ad addAd(SlotRent sr, MetaItem m) {
        Ad ad = new Ad(sr, m.id, Ad.Type.GOOD);
        this.ad.put(ad.id, ad);
        return ad;
    }
    public Ad addAd(SlotRent sr, int buildingType) {
        Ad ad = new Ad(sr, buildingType, Ad.Type.BUILDING);
        this.ad.put(ad.id, ad);
        qty += 1;
        return ad;
    }
    protected PublicFacility() {}

    @Override
    public int quality() {
        return this.qty;
    }

    int tickPrice;
    @Override
    protected void enterImpl(Npc npc){
        this.ad.values().forEach(ad->{
            ad.npcFlow++;
            BrandManager.instance().update(ad.sr.renterId, ad.metaId, 1);
        });
        ++visitorCount;
    }

    @Override
    protected void leaveImpl(Npc npc) {
        --visitorCount;
    }
    private int visitorCount;

    @PostLoad
    protected void _1() {
        this.meta = (MetaPublicFacility) super.metaBuilding;
    }

    @Override
    public Message detailProto() {
        Gs.PublicFacility.Builder builder = Gs.PublicFacility.newBuilder();
        builder.setInfo(this.toProto());
        builder.setAd(genAdPart());
        builder.setQty(qty);
        builder.setTicketPrice(this.tickPrice);
        builder.setVisitorCount(visitorCount);
        return builder.build();
    }
    protected Gs.Advertisement genAdPart() {
        Gs.Advertisement.Builder builder = Gs.Advertisement.newBuilder();
        this.slot.values().forEach(v->builder.addAvailableSlot(v.toProto()));
        this.rent.values().forEach(v->builder.addSoldSlot(v.toProto()));
        this.ad.values().forEach(v->builder.addAd(v.toProto()));
        return builder.build();
    }
    @Override
    public void appendDetailProto(Gs.BuildingSet.Builder builder) {
        builder.addPublicFacility((Gs.PublicFacility) this.detailProto());
    }

    @Override
    protected void _update(long diffNano) {
        final long now = System.currentTimeMillis();
        if(adTimer.update(diffNano)){
            Set<UUID> ids = new HashSet<>();
            rent.forEach((k,v)->{
                if(now - v.beginTs >= TimeUnit.DAYS.toMillis(v.day)) {
                    ids.add(k);
                }
                if(now - v.payTs >= TimeUnit.DAYS.toMillis(1)) {
                    Player renter = GameDb.queryPlayer(v.renterId);
                    Player owner = GameDb.queryPlayer(this.ownerId());
                    if(!renter.decMoney(v.slot.rentPreDay)) {
                        long deposit = renter.spentLockMoney(v.slot.id);
                        owner.addMoney(deposit);
                        ids.add(v.slot.id);
                    }
                    else {
                        LogDb.buyAdSlot(renter.id(), renter.money(),owner.id(), this.id(), v.slot.id, v.slot.rentPreDay);
                        owner.addMoney(v.slot.rentPreDay);
                        LogDb.incomeAdSlot(owner.id(), owner.money(),renter.id(), this.id(), v.slot.id, v.slot.rentPreDay);
                        v.payTs = now;
                    }
                    GameDb.saveOrUpdate(Arrays.asList(renter, owner, this)); // seems we should disable select-before-update
                }
            });
            rent.entrySet().removeIf(e -> ids.contains(e.getKey()));
            for(UUID id : ids) {
                this.ad.entrySet().removeIf(e->{
                    if(e.getValue().sr != null && e.getValue().sr.slot.id.equals(id))
                        return true;
                    return false;
                });
            }
            if(!ids.isEmpty()) {
                GameDb.saveOrUpdate(this); // update the delete
                Gs.AdSlotTimeoutInform.Builder builder = Gs.AdSlotTimeoutInform.newBuilder();
                builder.setBuildingId(Util.toByteString(this.id()));
                ids.forEach(e->builder.addSlotId(Util.toByteString(e)));
                this.sendToWatchers(Package.create(GsCode.OpCode.adSlotTimeoutInform_VALUE, builder.build()));
            }
        }
    }
}
