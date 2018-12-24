package Game;

public class RentPara {
    public RentPara(int rentPreDay, int paymentCycleDays, int deposit, int rentDaysMin, int rentDaysMax, int rentDays) {
        this.rentPreDay = rentPreDay;
        this.paymentCycleDays = paymentCycleDays;
        this.deposit = deposit;
        this.rentDays = rentDays;
        this.rentDaysMin = rentDaysMin;
        this.rentDaysMax = rentDaysMax;
        this.rentOut = false;
    }
    public RentPara(int rentPreDay, int deposit, int rentDaysMin, int rentDaysMax) {
        this.rentPreDay = rentPreDay;
        this.paymentCycleDays = 0;
        this.deposit = deposit;
        this.rentDays = 0;
        this.rentDaysMin = rentDaysMin;
        this.rentDaysMax = rentDaysMax;
        this.rentOut = true;
    }
    boolean rentOut;
    int rentPreDay;
    int paymentCycleDays;
    int deposit;
    int rentDays;
    int rentDaysMin;
    int rentDaysMax;
    public int requiredCost() {
        return rentPreDay * paymentCycleDays + deposit;
    }
    public int requiredPay() {
        return rentPreDay * paymentCycleDays;
    }
    public boolean valid() {
        boolean ok = rentPreDay > 0 && rentDaysMin > 0 && rentDaysMax > 0 && rentDaysMin <= rentDaysMax && deposit > 0;
        if(ok) {
            if(!rentOut)
                ok = rentDays >= rentDaysMin && rentDays <= rentDaysMax;
            else
                ok = paymentCycleDays <= rentDays && (rentDays > paymentCycleDays && rentDays%paymentCycleDays == 0);
        }
        return ok;
    }
}
