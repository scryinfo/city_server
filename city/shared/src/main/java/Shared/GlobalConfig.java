package Shared;

import java.io.File;

public class GlobalConfig {
	public static void init(String filePath) {

		iReader = new IniReader(new File(filePath));
		debug = Integer.parseInt(iReader.getPropertieValue("PARAM","debug")) != 0;
		product = Integer.parseInt(iReader.getPropertieValue("PARAM", "product")) != 0;
		serverId = Integer.parseInt(iReader.getPropertieValue("INFO","serverId"));
	}
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
	public static final boolean DEBUGLOG = false;
}
