package Game;

public class RentPara {
    public RentPara(int rentPreDay, int paymentCycleDays, int deposit, int rentDays) {
        this.rentPreDay = rentPreDay;
        this.paymentCycleDays = paymentCycleDays;
        this.deposit = deposit;
        this.rentDays = rentDays;
    }

    int rentPreDay;
    int paymentCycleDays;
    int deposit;
    int rentDays;
    public int requiredCost() {
        return rentPreDay * paymentCycleDays + deposit;
    }
    public int requiredPay() {
        return rentPreDay * paymentCycleDays;
    }
    public boolean valid() {
        return rentPreDay > 0 && paymentCycleDays > 0 && deposit > 0 && (rentDays > paymentCycleDays && rentDays % paymentCycleDays == 0);
    }
}
