package Shared;

import org.apache.log4j.Logger;

import java.io.File;

public class GlobalConfig {
	public static void init(String filePath) {

		iReader = new IniReader(new File(filePath));
		debug = Integer.parseInt(iReader.getPropertieValue("PARAM","debug")) != 0;
		product = Integer.parseInt(iReader.getPropertieValue("PARAM", "product")) != 0;
		serverId = Integer.parseInt(iReader.getPropertieValue("INFO","serverId"));
	}

	private static long _elapsedtime = 0 ;    //Last update time
	//public static final long _upDeltaNs = 3600*1000000000;    //Update interval, the unit is nanoseconds, 3600 is an hour
	public static final long _upDeltaNs = 10*1000000000;        //Update interval, the unit is nanoseconds, 3600 is an hour Ns
	public static final long _upDeltaMs = _upDeltaNs/1000000;   //Update interval, ms
	public static final long _upDeltaS = _upDeltaMs/1000;   	//Update interval, seconds

	private static int serverId;
	private static boolean debug;
	private static boolean product;
	private static IniReader iReader;
	public static String metaUri() {
		return iReader.getPropertieValue("DB", "metaUri");
	}
	public static String configUri(){ return iReader.getPropertieValue("DB", "configUri"); }
	public static int serverId() {
		return serverId;
	}
	public static boolean debug() {
		return debug;
	}
	public static boolean product() {
		return product;
	}
	public static final boolean DEBUGLOG = true;
	public static final boolean SPECTIALTICK = true;
	private static final Logger logger = Logger.getLogger(GlobalConfig.class);
	public static final void cityError(String msg){logger.fatal("[cityError] "+msg);}
}
