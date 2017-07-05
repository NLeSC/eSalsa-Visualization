package nl.esciencecenter.visualization.esalsa;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import nl.esciencecenter.visualization.esalsa.data.reworked.NCDFVariable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheFileManager {
    private final static Logger logger = LoggerFactory.getLogger(CacheFileManager.class);
    private final File cacheFile;

    public CacheFileManager(String path) {
        cacheFile = new File(path + File.separator + ".visualizationCache");
    }
    
    private float readFloat(String variableName, String toMatch) {
        float result = Float.NaN;

        // Check if we have made a cacheFileManager file earlier
        if (cacheFile.exists()) {
            BufferedReader in;
            String str;

            try {
                in = new BufferedReader(new FileReader(cacheFile));
                while ((str = in.readLine()) != null) {
                    String[] substrings = str.split(" ");
                    if (substrings[0].compareTo(variableName) == 0 && substrings[1].compareTo(toMatch) == 0) {
                        result = Float.parseFloat(substrings[2]);
                    }
                }
                in.close();
            } catch (IOException e) {
            	logger.debug("IOException caught in cache: "+e.getMessage());
            }
        }

        return result;    	
    }
    
    private void writeFloat(String variableName, String toMatch, float value) {
        if (!cacheFile.exists()) {
            try {
                cacheFile.createNewFile();
            } catch (IOException e) {
            	logger.debug("IOException caught in cache: "+e.getMessage());
            }
        }

        try {
        	BufferedReader in = new BufferedReader(new FileReader(cacheFile));
        	String newtextFileContent = "";
        	String str;
        	
        	boolean written = false;
            while ((str = in.readLine()) != null) {            	
                String[] substrings = str.split(" ");
                if (substrings[0].compareTo(variableName) == 0 && substrings[1].compareTo(toMatch) == 0) {
                	newtextFileContent += variableName + " " + toMatch + " " + value + "\n";
                	written = true;
                } else {
                	newtextFileContent += str + "\n";                	
                }
            }
            in.close();
            
            if (!written) {
            	newtextFileContent += variableName + " " + toMatch + " " + value + "\n";
            }
            
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(cacheFile, false)));            
            out.print(newtextFileContent);

            out.close();
        } catch (IOException e) {
        	logger.debug("IOException caught in cache: "+e.getMessage());
        }    	
    }

    public String readString(String variableName, String toMatch) {
        String result = "";

        // Check if we have made a cacheFileManager file earlier
        if (cacheFile.exists()) {
            BufferedReader in;
            String str;

            try {
                in = new BufferedReader(new FileReader(cacheFile));
                while ((str = in.readLine()) != null) {
                    String[] substrings = str.split(" ");
                    if (substrings[0].compareTo(variableName) == 0 && substrings[1].compareTo(toMatch) == 0) {
                        result = substrings[2];
                    }
                }
                in.close();
            } catch (IOException e) {
            	logger.debug("IOException caught in cache: "+e.getMessage());
            }
        } else {
        	System.out.println("Cache file nonexistent.");
        }

        return result;
    }
    
    private void writeString(String variableName, String toMatch, String value) {
        if (!cacheFile.exists()) {
            try {
                cacheFile.createNewFile();
            } catch (IOException e) {
            	logger.debug("IOException caught in cache: "+e.getMessage());
            }
        }

        try {
        	BufferedReader in = new BufferedReader(new FileReader(cacheFile));
        	String newtextFileContent = "";
        	String str;
        	
        	boolean written = false;
            while ((str = in.readLine()) != null) {            	
                String[] substrings = str.split(" ");
                if (substrings[0].compareTo(variableName) == 0 && substrings[1].compareTo(toMatch) == 0) {
                	newtextFileContent += variableName + " " + toMatch + " " + value + "\n";
                	written = true;
                } else {
                	newtextFileContent += str + "\n";                	
                }
            }
            in.close();
            
            if (!written) {
            	newtextFileContent += variableName + " " + toMatch + " " + value + "\n";
            }     
            
        	PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(cacheFile, false)));            
        	out.print(newtextFileContent);
        	out.close();
        } catch (IOException e) {
        	logger.debug("IOException caught in cache: "+e.getMessage());
        }    	
    }

    public float readMin(String variableName) {
        return readFloat(variableName, "min");
    }

    public float readLatMin(String variableName) {
        return readFloat(variableName, "latMin");
    }

    public float readMax(String variableName) {
        return readFloat(variableName, "max");
    }

    public float readLatMax(String variableName) {
        return readFloat(variableName, "latMax");
    }

    public void writeMin(String variableName, float value) {
    	writeFloat(variableName, "min", value);
    }

    public void writeLatMin(String variableName, float value) {
    	writeFloat(variableName, "latMin", value);
    }

    public void writeMax(String variableName, float value) {
    	writeFloat(variableName, "max", value);
    }

    public void writeLatMax(String variableName, float value) {
    	writeFloat(variableName, "latMax", value);
    }

    public String readColormap(String variableName) {        
        return readString(variableName, "colormap");
    }

    public void writeColormap(String variableName, String value) {
        writeString(variableName, "colormap", value);
    }
}
