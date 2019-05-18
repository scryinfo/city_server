package Game;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import Game.Meta.MetaData;

@Entity(name = "IndustryIncrease")
@Table(name = "IndustryIncrease")
public class IndustryIncrease {
	
    @Id
    @Column(name = "buildingType")
    private int buildingType; //建筑类型
    
    @Column(name = "industryMoney")
    private long industryMoney; //行业涨薪指数
    
    @Column(name = "industrySalary")
    private double industrySalary; //行业工资
    
	public IndustryIncrease() {
		super();
	}
	
	public IndustryIncrease(int buildingType, long industryMoney) {
		super();
		this.buildingType = buildingType;
		this.industryMoney = industryMoney;
		this.industrySalary = MetaData.getSalaryByBuildingType(buildingType);
	}
  
	public IndustryIncrease(int buildingType, long industryMoney, double industrySalary) {
		super();
		this.buildingType = buildingType;
		this.industryMoney = industryMoney;
		this.industrySalary = MetaData.getSalaryByBuildingType(buildingType);
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

	public double getIndustrySalary() {
		return industrySalary;
	}

	public void setIndustrySalary(double industrySalary) {
		this.industrySalary = industrySalary;
	}
}
