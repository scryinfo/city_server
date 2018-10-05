package Shared;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class IniReader {  
    private Map<String, Properties> sectionsMap = new HashMap<String, Properties>();  
    private transient String currentSection;  
    private transient Properties curSectionPeropertie;  
    public IniReader(File file) {
    	try {
	        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));  
	        String line = bufferedReader.readLine();  
	        while (line != null) {  
	            if(line.startsWith("[")&&line.endsWith("]")){
	            	currentSection = line.substring(1,line.length()-1);
	                curSectionPeropertie = new Properties();  
	                sectionsMap.put(currentSection, curSectionPeropertie);  
	            } else if (line.indexOf("=")>0) {  
	                if (curSectionPeropertie != null) {  
	                    String[] array = line.split("=");  
	                    curSectionPeropertie.put(array[0], array[1]); 
	                }  
	            }  
	            line = bufferedReader.readLine(); 
	        }  
	        bufferedReader.close();
		} catch (IOException e) {
			e.printStackTrace();
		} 
    }  
    public String getPropertieValue(String section, String propertie) {  
        Properties p = (Properties) sectionsMap.get(section);  
        if (p != null) {  
            return p.getProperty(propertie);  
        } else {  
            return "";  
        }  
    }  
}  