package Game.Util;

public class NpcUtil {
    /** 
     * Randomly specify N unique numbers in the range
     * The simplest and most basic method
     * @param min Minimum value of specified range 
     * @param max Maximum specified range
     * @param n Random number 
     */  
    public static int[] getDifferentIndex(int min, int max, int n){  
        if (n > (max - min + 1) || max < min) {  
               return null;  
           }  
        int[] result = new int[n];  
        int count = 0;  
        while(count < n) {  
            int num = (int) (Math.random() * (max - min)) + min;  
            boolean flag = true;  
            for (int j = 0; j < n; j++) {  
                if(num == result[j]){  
                    flag = false;  
                    break;  
                }  
            }  
            if(flag){  
                result[count] = num;  
                count++;  
            }  
        }  
        return result;  
    }  
}
