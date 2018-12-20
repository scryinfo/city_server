package Game;

public enum MailType {
    EXCHANGE_GOODS_SOLD(1),
    STORE_FULL(2),
    PRODUC_LINE_COMPLETION(3),
    EMPLOYEE_SATISFACTION(4),
    LOCKOUT(5),
    AD_SPACE_RENT_OUT(6),
    SCIENTIFIC_PAYOFFS(7),
    INVENTIONS(8),
    LAND_AUCTION(9),
    LAND_LEASE(10),
    LAND_AUCTION_HIGHERPRICE(11),
    FINANCE(12),
    FRIENDS_NOTICE(13),
    LAND_TRANSACTION(14),
    AD_SPACE_EXPIRE(15),
    RETAIL_STORE_MERCHANDISE(16),
    PARK_TICKET_REVENUE(17);

    private int mailType;

    MailType(int mailType) {
        this.mailType = mailType;
    }

    public int getMailType(int t) {
        return t;
    }
}
