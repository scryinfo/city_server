package Account;

import java.util.concurrent.ConcurrentHashMap;

public class AccountSessionManager {
	private ConcurrentHashMap<String, AccountSession> data = new ConcurrentHashMap<String, AccountSession>();
	private static AccountSessionManager singleInstance = new AccountSessionManager();

	public AccountSessionManager() {

	}

	public static AccountSessionManager getInstance() {
		return singleInstance;
	}

	public AccountSession findOne(String accountName) {
		return data.search(1000L, (k, v) -> {
			if (k.equals(accountName))
				return v;
			return null;
		});
	}
	
	public AccountSession add(String accountName, AccountSession session){
		return data.putIfAbsent(accountName, session);
	}
	
	public AccountSession del(String accountName){
		return data.remove(accountName);
	}
	
	
}
