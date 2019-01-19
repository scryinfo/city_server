package Game.Meta;

import org.bson.Document;

import java.util.Arrays;
import java.util.List;

public class MetaCity {
	public MetaCity(Document d) throws Exception {
		this.x = d.getInteger("x");
		this.y = d.getInteger("y");
		this.gridX = d.getInteger("gridX");
		this.gridY = d.getInteger("gridY");
		this.name = d.getString("name");
		this.timeZone = d.getInteger("timeZone");
        this.timeSection = ((List<Integer>)d.get("timeSection")).stream().mapToInt(Integer::valueOf).toArray();
        Arrays.sort(timeSection);
        if(timeSection.length == 0 || timeSection[0] != 0 || timeSection[timeSection.length-1] > 23)
            throw new Exception("city time section config is incorrect!");
        this.minHour = minTimeSectionHour();
	}
	public int x;
	public int y;
	public int gridX;
	public int gridY;
	public String name;
	public int timeZone;
	public int[] timeSection;
    public int minHour;
    public int indexOfHour(int nowHour) {
        int idx = Arrays.binarySearch(this.timeSection, nowHour);
        if(idx < 0)
            idx = -(idx+2); // fuck java
        return idx;
    }

    private int minTimeSectionHour() {
        int mini = Integer.MAX_VALUE;
        for(int i = 1; i < this.timeSection.length; ++i) {
            int d = this.timeSection[i] - this.timeSection[i-1];
            if(d < mini)
                mini = d;
        }
        return mini;
    }

    public int timeSectionDuration(int index) {
        if(index != this.timeSection.length-1)
            return this.timeSection[index+1] - this.timeSection[index];
        else
            return 24-this.timeSection[index];
    }

    public int nextTimeSectionDuration(int index) {
       return timeSectionDuration(nextIndex(index));
    }
    public int nextIndex(int index) {
        if(index >= this.timeSection.length)
            throw new IllegalArgumentException();
        if(index == this.timeSection.length-1)
            return 0;
        return index+1;
    }
    public int nextTimeSectionHour(int index) {
        return this.timeSection[nextIndex(index)];
    }
}
