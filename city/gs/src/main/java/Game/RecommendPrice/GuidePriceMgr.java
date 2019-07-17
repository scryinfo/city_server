package Game.RecommendPrice;

public class GuidePriceMgr {
    private static GuidePriceMgr instance = new GuidePriceMgr();
    private GuidePriceMgr() {
    }
    public static GuidePriceMgr instance() {
        return instance;
    }
    //params1.住宅评分,2.繁荣度
    public  double getApartmentGuidePrice(double currScore,double prosperous) {
//        推荐定价 = (全城均住宅成交价 * (玩家住宅总评分 /400 * 7 + 1) * (1 + 玩家住宅繁荣度)) / ((全城均住宅总评分 /400 * 7 + 1) * (1 + 全城均住宅繁荣度))
        return 0;
    }
}
