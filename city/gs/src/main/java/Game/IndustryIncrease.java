package Game;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity(name = "IndustryIncrease")
@Table(name = "IndustryIncrease")
public class IndustryIncrease {
	
    @Id
    @Column(name = "buildingType")
    private int buildingType; //建筑类型
    
    @Column(name = "industryMoney")
    private long industryMoney; //行业涨薪指数
    
	public IndustryIncrease() {
		super();
	}
	
	public IndustryIncrease(int buildingType, long industryMoney) {
		super();
		this.buildingType = buildingType;
		this.industryMoney = industryMoney;
	}

	public int getBuildingType() {
		return buildingType;
	}

	public void setBuildingType(int buildingType) {
		this.buildingType = buildingType;
	}

	public long getIndustryMoney() {
		return industryMoney;
	}

	public void setIndustryMoney(long industryMoney) {
		this.industryMoney = industryMoney;
	}
}
