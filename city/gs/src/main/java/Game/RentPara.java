package Game;

public class RentPara {
    public RentPara(int rentPreDay,int rentDaysMin, int rentDaysMax, int rentDays) {
        this.rentPreDay = rentPreDay;
        this.rentDays = rentDays;
        this.rentDaysMin = rentDaysMin;
        this.rentDaysMax = rentDaysMax;
        this.rentOut = false;
    }
    public RentPara(int rentPreDay,int rentDaysMin, int rentDaysMax) {
        this.rentPreDay = rentPreDay;
        this.rentDays = 0;
        this.rentDaysMin = rentDaysMin;
        this.rentDaysMax = rentDaysMax;
        this.rentOut = true;
    }
    boolean rentOut;
    int rentPreDay;
    int rentDays;
    int rentDaysMin;
    int rentDaysMax;
    public int requiredCost() {
        return rentPreDay * rentDays;
    }
    public int requiredPay() {
        return rentPreDay * rentDays;
    }
    public boolean valid() {
        boolean ok = rentPreDay > 0 && rentDaysMin > 0 && rentDaysMax > 0 && rentDaysMin <= rentDaysMax;
        if(ok) {
            if(!rentOut)
                ok = rentDays >= rentDaysMin && rentDays <= rentDaysMax;
        }
        return ok;
    }
}
