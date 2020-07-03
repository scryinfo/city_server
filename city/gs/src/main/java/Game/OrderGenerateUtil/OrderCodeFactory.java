package Game.OrderGenerateUtil;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

public class OrderCodeFactory {
    /** Order category header */
    private static final String ORDER_CODE = "1";
    /** Return category header */
    private static final String RETURN_ORDER = "2";
    /** Refund category header */
    private static final String REFUND_ORDER = "3";
    /** Unpaid re-payment header */
    private static final String AGAIN_ORDER = "4";
    /** Random coding */
    private static final int[] r = new int[]{7, 9, 6, 2, 8 , 1, 3, 0, 5, 4};
    /** Total length of user id and random number */
    private static final int maxLength = 14;

    /**
     * Encrypt according to id + add random number to form fixed length code
     */
    private static String toCode(Long id) {
        String idStr = id.toString();
        StringBuilder idsbs = new StringBuilder();
        for (int i = idStr.length() - 1 ; i >= 0; i--) {
            idsbs.append(r[idStr.charAt(i)-'0']);
        }
        return idsbs.append(getRandom(maxLength - idStr.length())).toString();
    }
     
    /**
     * Generate timestamp
     */
    private static String getDateTime(){
        DateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        return sdf.format(new Date());
    }
    
    /**
     * Generate a fixed-length random code
     * @param n    length
     */
    private static long getRandom(long n) {
        long min = 1,max = 9;
        for (int i = 1; i < n; i++) {
            min *= 10;
            max *= 10;
        }
        long rangeLong = (((long) (new Random().nextDouble() * (max - min)))) + min ;
        return rangeLong;
    }
    
    /**
     * Generate codes without category headers
     * @param userId
     */
    private static synchronized String getCode(Long userId){
        userId = userId == null ? 10000 : userId;
        return getDateTime() + toCode(userId);
    }
    
    /**
     * Generate order number code
     * @param userId
     */
    public static String getOrderCode(Long userId){
        return ORDER_CODE + getCode(userId);
    }
    
    /**
     * Generate return order number code
     * @param userId
     */
    public static String getReturnCode(Long userId){
        return RETURN_ORDER + getCode(userId);
    }
    
    /**
     * Generate refund order number code
     * @param userId
     */
    public static String getRefundCode(Long userId){
        return REFUND_ORDER + getCode(userId);
    }
    
    /**
     * Repay without payment
     * @param userId
     */
    public static String getAgainCode(Long userId){
        return AGAIN_ORDER + getCode(userId);
    }

    public static Long getOrderId(long metaId){
        String orderCode = getOrderCode(metaId);
        orderCode = orderCode.substring(15, orderCode.length());
        return Long.parseLong(orderCode);
    }
}