package Game;

import Game.Timers.PeriodicTimer;
import Shared.Package;
import Shared.Util;
import gs.Gs;
import gscode.GsCode;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Entity(name = "PublicFacility")
public class PublicFacility extends Building {
    @Transient
    private MetaPublicFacility meta;
    private static final int MAX_SLOT_NUM = 999;
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

        Gs.PublicFacility.Slot toProto() {
            Gs.PublicFacility.Slot.Builder builder = Gs.PublicFacility.Slot.newBuilder();
            return builder.setId(Util.toByteString(id))
                    .setMaxDayToRent(maxDayToRent)
                    .setMinDayToRent(minDayToRent)
                    .setDeposit(deposit)
                    .setRentPreDay(rentPreDay)
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
        Gs.PublicFacility.SoldSlot toProto() {
            Gs.PublicFacility.SoldSlot.Builder builder = Gs.PublicFacility.SoldSlot.newBuilder();
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
        }

        @Id
        final UUID id = UUID.randomUUID();
        @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
        @JoinColumn(name = "slot_rent_id")
        SlotRent sr;
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

        Gs.PublicFacility.Ad toProto() {
            Gs.PublicFacility.Ad.Builder builder = Gs.PublicFacility.Ad.newBuilder();
            return builder.setSlot(sr.toProto())
                    .setId(Util.toByteString(id))
                    .setMetaId(metaId)
                    .setType(Gs.PublicFacility.Ad.Type.valueOf(type.ordinal()))
                    .setBeginTs(beginTs)
                    .setNpcFlow(npcFlow)
                    .build();
        }
    }

    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKeyColumn(name = "id")
    @JoinColumn(name = "public_facility_id")
    // key is ad.id not slot.id
    private Map<UUID, Ad> ad = new HashMap<>();


    public Slot addSlot(int maxDay, int minDay, int rent, int deposit) {
        if(slot.size() >= MAX_SLOT_NUM)
            return null;
        Slot s = new Slot(maxDay, minDay, rent, deposit);
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
    }
    public void addAd(SlotRent sr, MetaItem m) {
        Ad ad = new Ad(sr, m.id, Ad.Type.GOOD);
        this.ad.put(ad.id, ad);
    }
    public void addAd(SlotRent sr, MetaBuilding m) {
        Ad ad = new Ad(sr, m.id, Ad.Type.BUILDING);
        this.ad.put(ad.id, ad);
    }
    public PublicFacility() {
    }

    @PostLoad
    private void _1() {
        this.meta = (MetaPublicFacility) super.metaBuilding;
    }

    @Override
    public Gs.PublicFacility detailProto() {
        Gs.PublicFacility.Builder builder = Gs.PublicFacility.newBuilder();
        this.slot.values().forEach(v->builder.addAvailableSlot(v.toProto()));
        this.rent.values().forEach(v->builder.addSoldSlot(v.toProto()));
        this.ad.values().forEach(v->builder.addAd(v.toProto()));
        return builder.build();
    }

    @Override
    public void appendDetailProto(Gs.BuildingSet.Builder builder) {
        builder.addPublicFacility(this.detailProto());
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
                        owner.addMoney(v.slot.rentPreDay);
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
                Gs.Bytes.Builder builder = Gs.Bytes.newBuilder();
                ids.forEach(e->builder.addIds(Util.toByteString(e)));
                GameServer.sendTo(this.detailWatchers, Package.create(GsCode.OpCode.adSlotTimeoutInform_VALUE, builder.build()));
            }
        }
    }

    public PublicFacility(MetaPublicFacility meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
    }

}
