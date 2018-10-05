package Account;

import org.bson.Document;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public final class AccountInfo {
	public String name;
	public Instant createTime;
	public Instant freezeTime;
	
	AccountInfo(Document bson){
		name = bson.getString("_id");
		createTime = bson.getDate("ct").toInstant();
		freezeTime = bson.getDate("ft").toInstant();
	}
	
	AccountInfo(String accountName){
		name = accountName;
		createTime = Instant.now();
		freezeTime = Instant.EPOCH;
	}
	
	Document toDocument(){
		Document doc = new Document()
				.append("_id", name)
				.append("ct", Date.from(createTime))
				.append("ft", Date.from(freezeTime));
		return doc;
	}
	
	Instant getFreezeEndTime(int freezeDays){
		Instant t = Instant.now().plus(freezeDays, ChronoUnit.DAYS);
		return t;
	}
}
