package Account;

import org.bson.Document;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public final class AccountInfo {
	public String name;
	private String md5Pwd;
	public Instant createTime;
	public Instant freezeTime;
	
	AccountInfo(Document bson){
		name = bson.getString("_id");
		createTime = bson.getDate("ct").toInstant();
		freezeTime = bson.getDate("ft").toInstant();
		this.md5Pwd = bson.getString("pwd");
	}

	public String getMd5Pwd()
	{
		return md5Pwd;
	}

	AccountInfo(String accountName, String md5Pwd){
		name = accountName;
		createTime = Instant.now();
		freezeTime = Instant.EPOCH;
		this.md5Pwd = md5Pwd;
	}
	
	Document toDocument(){
		Document doc = new Document()
				.append("_id", name)
				.append("pwd",md5Pwd)
				.append("ct", Date.from(createTime))
				.append("ft", Date.from(freezeTime));
		return doc;
	}
	
	Instant getFreezeEndTime(int freezeDays){
		Instant t = Instant.now().plus(freezeDays, ChronoUnit.DAYS);
		return t;
	}
}
