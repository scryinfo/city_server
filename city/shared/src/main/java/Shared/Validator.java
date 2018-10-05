package Shared;

import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

public class Validator {
	static class Login {
        String code; // validate code
		int successTimes; // validate success times
		int waitingMs; // ms before first successful validate
		boolean reserveFlag; // mark reserve flag
		int reserveMs; // mark ms

        public Login(String code, int successTimes, int waitingMs, boolean reserveFlag, int reserveMs) {
            this.code = code;
            this.successTimes = successTimes;
            this.waitingMs = waitingMs;
            this.reserveFlag = reserveFlag;
            this.reserveMs = reserveMs;
        }
    }

	private static final Logger logger = Logger.getLogger(Validator.class);
	private HashMap<String, Login> data = new HashMap<String, Login>();
	private static Validator instance = new Validator();
	private Validator() {}

	public static Validator getInstance() {
		return instance;
	}

	public synchronized void regist(final String accountName, final String code) {
		data.put(accountName, new Login(code, 0, 0, false, 0));
	}

	public synchronized int validate(final String accountName, final String code) {
        Validator.Login v = data.get(accountName);
        if(v == null) {
            logger.debug("haven't found the account name");
            return 0;
        }
        if (!v.code.equals(code)) {
            logger.debug("code doesn't match");
            return 0;
        }
        v.reserveFlag = false;
        v.reserveMs = 0;
        return ++(v.successTimes);
	}

	public synchronized void unRegist(final String accountName, final String code) {
	    Validator.Login v = data.get(accountName);
		if (v == null) {
			logger.debug("haven't found the account");
		}
		if (v.code.equals(code)) {
			data.remove(accountName);
		} else {
			logger.debug("the code has been overwirtten");
		}

	}

	public synchronized void markReserve(final String accountName, final String code) {
		data.computeIfPresent(accountName, (k, v) -> {
			if (v.code.equals(code)) {
				v.reserveFlag = true;
			}
			return v;
		});
	}

	public synchronized void update(int diff) {
        Iterator<Entry<String, Login>> iter = data.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<String, Login> entry = iter.next();
            Login v = entry.getValue();
            if (v.reserveFlag) {
                v.reserveMs += diff;
                if (v.reserveMs >= 1200000) { // 20min
                    // log erase an mark
                    iter.remove();
                }
            } else {
                v.waitingMs += diff;
                if (v.waitingMs >= 3600000) { // 1 hour
                    // log erase an mark
                    iter.remove();
                }
            }
        }
	}
}
