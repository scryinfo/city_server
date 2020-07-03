package Game.CityInfo;


import java.util.UUID;

// Industry ranking
public class TopInfo {
    public UUID pid;
    public String faceId;
    public String name;  // name
    public long yesterdayIncome;  // Yesterday's income
    public long workerNum;       // Total number of people in the industry
    public  long science; // Technology Points
    public  long promotion; // Promotion points
    public  int count;    // Yesterday's trading volume (only for land)
    public TopInfo(UUID pid,String faceId,String name, long yesterdayIncome, long workerNum, long science, long promotion) {
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
