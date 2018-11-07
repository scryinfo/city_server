package Game;

import gs.Gs;

import java.util.*;

public class Ground {
    boolean rentOut(Collection<Coordinate> coordinates, int rentPreDay, int paymentCycleDays, int deposit, int rentDays) {
        if(!unused.containsAll(coordinates))
            return false;
        coordinates.forEach(c->rent.add(new RentInfo(c.x, c.y, rentPreDay, paymentCycleDays, deposit, rentDays)));
        unused.removeAll(coordinates);
        return true;
    }
    List<Gs.GroundInfo> toProto() {
        List<Gs.GroundInfo> res = new ArrayList<>(unused.size() + rent.size() + sell.size());
        unused.forEach(c->res.add(Gs.GroundInfo.newBuilder().setX(c.x).setY(c.y).build()));
        rent.forEach(c->res.add(c.toProto()));
        sell.forEach(c->res.add(c.toProto()));
        return res;
    }

        public static final class SellInfo {

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SellInfo sellInfo = (SellInfo) o;
            return x == sellInfo.x &&
                    y == sellInfo.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }

        int x;
        int y;
        int price;

        Gs.GroundInfo toProto() {
            return Gs.GroundInfo.newBuilder()
                    .setX(x)
                    .setY(y)
                    .setSell(Gs.GroundInfo.Sell.newBuilder()
                        .setPrice(price))
                    .build();
        }
    }

    public static final class RentInfo {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RentInfo rentInfo = (RentInfo) o;
            return x == rentInfo.x &&
                    y == rentInfo.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }

        public RentInfo(int x, int y, int rentPreDay, int paymentCycleDays, int deposit, int rentDays) {
            this.x = x;
            this.y = y;
            this.rentPreDay = rentPreDay;
            this.paymentCycleDays = paymentCycleDays;
            this.deposit = deposit;
            this.rentDays = rentDays;
        }

        protected RentInfo() {}

        int x;
        int y;
        int rentPreDay;
        int paymentCycleDays;
        int deposit;
        int rentDays;

        Gs.GroundInfo toProto() {
            return Gs.GroundInfo.newBuilder()
                    .setX(x)
                    .setY(y)
                    .setRent(Gs.GroundInfo.Rent.newBuilder()
                        .setRentPreDay(rentPreDay)
                        .setPaymentCycleDays(paymentCycleDays)
                        .setDeposit(deposit)
                        .setRentDays(rentDays))
                    .build();
        }
    }
    private Set<Coordinate> unused = new HashSet<>();
    private Set<RentInfo> rent = new HashSet<>();
    private Set<SellInfo> sell = new HashSet<>();

    public boolean containAll(CoordPair cp) {
        for (int x = cp.l.x; x <= cp.r.x; ++x) {
            for (int y = cp.l.y; y <= cp.r.y; ++y) {
                if (!this.unused.contains(new Coordinate(x, y)))
                    return false;
            }
        }
        return true;
    }

    public boolean containAny(CoordPair cp) {
        for (int x = cp.l.x; x <= cp.r.x; ++x) {
            for (int y = cp.l.y; y <= cp.r.y; ++y) {
                if (this.unused.contains(new Coordinate(x, y)))
                    return true;
            }
        }
        return false;
    }

    public boolean containAny(Collection<Coordinate> coordinates) {
        for (Coordinate c : coordinates) {
            if (this.unused.contains(c))
                return true;
        }
        return false;
    }

    public boolean containAll(Collection<Coordinate> coordinates) {
        for (Coordinate c : coordinates) {
            if (!this.unused.contains(c))
                return false;
        }
        return true;
    }

    public void add(CoordPair cp) throws Exception {
        if (this.containAny(cp))
            throw new Exception("intersection found");
        this.unused.addAll(cp.toCoordinates());
    }

    public void add(Collection<Coordinate> coordinates) throws Exception {
        if (this.containAny(coordinates))
            throw new Exception("intersection found");
        this.unused.addAll(coordinates);
    }
}
