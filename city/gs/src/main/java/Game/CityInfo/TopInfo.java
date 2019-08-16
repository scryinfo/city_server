package Game.CityInfo;


import java.util.UUID;

// 行业排行
public class TopInfo {
    public UUID pid;
    public String faceId;
    public String name;  // 名称
    public long yesterdayIncome;  // 昨日收入
    public int workerNum;       // 行业总总人数
    public  long science; // 科技点数
    public  long promotion; // 推广点数
    public  int count;    // 昨日成交量(土地才会有)
    public TopInfo(UUID pid,String faceId,String name, long yesterdayIncome, int workerNum, long science, long promotion) {
        this.pid = pid;
        this.faceId = faceId;
        this.name = name;
        this.yesterdayIncome = yesterdayIncome;
        this.workerNum = workerNum;
        this.science = science;
        this.promotion = promotion;
    }

    public TopInfo(UUID pid,String faceId, String name,long total, int count) {
        this.pid = pid;
        this.faceId = faceId;
        this.name = name;
        this.yesterdayIncome = total;
        this.count = count;
    }
}
