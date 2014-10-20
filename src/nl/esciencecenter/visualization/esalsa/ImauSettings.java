package nl.esciencecenter.visualization.esalsa;

import java.util.ArrayList;
import java.util.HashMap;

import nl.esciencecenter.neon.util.TypedProperties;
import nl.esciencecenter.visualization.esalsa.data.SurfaceTextureDescription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImauSettings {
    private final Logger logger = LoggerFactory.getLogger(ImauSettings.class);

    private static class SingletonHolder {
        public final static ImauSettings instance = new ImauSettings();
    }

    public static ImauSettings getInstance() {
        return SingletonHolder.instance;
    }

    public enum GlobeMode {
        FIRST_DATASET, SECOND_DATASET, DIFF
    };

    public enum Months {
        Jan, Feb, Mar, Apr, May, Jun, Jul, Aug, Sep, Oct, Nov, Dec
    };

    private boolean STEREO_RENDERING = true;
    private boolean STEREO_SWITCHED = true;

    private float STEREO_OCULAR_DISTANCE_MIN = 0f;
    private float STEREO_OCULAR_DISTANCE_DEF = .2f;
    private float STEREO_OCULAR_DISTANCE_MAX = 1f;

    // Size settings for default startup and screenshots
    private int DEFAULT_SCREEN_WIDTH = 1024;
    private int DEFAULT_SCREEN_HEIGHT = 768;

    private int INTERFACE_HEIGHT = 88;
    private int INTERFACE_WIDTH = 210;

    private final int SCREENSHOT_SCREEN_WIDTH = 1280;
    private final int SCREENSHOT_SCREEN_HEIGHT = 720;

    // Settings for the initial view
    private int INITIAL_SIMULATION_FRAME = 0;
    private float INITIAL_ROTATION_X = 17f;
    private float INITIAL_ROTATION_Y = -25f;
    private float INITIAL_ZOOM = -390.0f;

    // Setting per movie frame
    private boolean MOVIE_ROTATE = true;
    private float MOVIE_ROTATION_SPEED_MIN = -1f;
    private float MOVIE_ROTATION_SPEED_MAX = 1f;
    private float MOVIE_ROTATION_SPEED_DEF = -0.25f;

    // Settings for the gas cloud octree
    private int MAX_OCTREE_DEPTH = 25;
    private float OCTREE_EDGES = 800f;

    // Settings that should never change, but are listed here to make sure they
    // can be found if necessary
    private int MAX_EXPECTED_MODELS = 1000;

    protected String SCREENSHOT_PATH = System.getProperty("user.dir") + System.getProperty("path.separator");

    private long WAITTIME_FOR_RETRY = 10000;
    private long WAITTIME_FOR_MOVIE = 1000;
    private int TIME_STEP_SIZE = 1;
    private float EPSILON = 1.0E-7f;

    private int FILE_EXTENSION_LENGTH = 2;
    private int FILE_NUMBER_LENGTH = 4;

    private final String[] ACCEPTABLE_POSTFIXES = { ".nc" };

    private String CURRENT_POSTFIX = "nc";

    private int PREPROCESSING_AMOUNT = 2;

    private final HashMap<String, Float> minValues;
    private final HashMap<String, Float> diffMinValues;
    private final HashMap<String, Float> maxValues;
    private final HashMap<String, Float> diffMaxValues;
    private final HashMap<String, Float> currentMinValues;
    private final HashMap<String, Float> currentDiffMinValues;
    private final HashMap<String, Float> currentMaxValues;
    private final HashMap<String, Float> currentDiffMaxValues;
    private final HashMap<String, String> currentColormap;
    private final HashMap<String, Boolean> logarithmicScale;

    private int DEPTH_MIN = 0;
    private int DEPTH_DEF = 0;
    private int DEPTH_MAX = 41;

    private int WINDOW_SELECTION = 0;

    private boolean IMAGE_STREAM_OUTPUT = false;
    private final int SAGE_FRAMES_PER_SECOND = 10;
    private boolean IMAGE_STREAM_GL_ONLY = true;

    private float HEIGHT_DISTORION = 0f;
    private final float HEIGHT_DISTORION_MIN = 0f;
    private final float HEIGHT_DISTORION_MAX = .01f;

    private String SAGE_DIRECTORY = "/home/maarten/sage-code/sage";

    private final boolean TOUCH_CONNECTED = false;

    private SurfaceTextureDescription[] screenDescriptions;

    private final String grid_width_dimension_substring = "lon";
    private final String grid_height_dimension_substring = "lat";

    private int number_of_screens_col = 2;
    private int number_of_screens_row = 2;

    private CacheFileManager cacheFileManagerAtDataLocation;
    private CacheFileManager cacheFileManagerAtProgramLocation;

    private int numberOfScreenshotsPerTimeStep = 6;

    private boolean requestedNewConfiguration;

    private ImauSettings() {
        super();

        minValues = new HashMap<String, Float>();
        maxValues = new HashMap<String, Float>();
        currentMinValues = new HashMap<String, Float>();
        currentMaxValues = new HashMap<String, Float>();
        diffMinValues = new HashMap<String, Float>();
        diffMaxValues = new HashMap<String, Float>();
        currentDiffMinValues = new HashMap<String, Float>();
        currentDiffMaxValues = new HashMap<String, Float>();
        currentColormap = new HashMap<String, String>();
        logarithmicScale = new HashMap<String, Boolean>();

        try {
            final TypedProperties props = new TypedProperties();
            props.loadFromClassPath("settings.properties");

            STEREO_RENDERING = props.getBooleanProperty("STEREO_RENDERING");
            STEREO_SWITCHED = props.getBooleanProperty("STEREO_SWITCHED");

            STEREO_OCULAR_DISTANCE_MIN = props.getFloatProperty("STEREO_OCULAR_DISTANCE_MIN");
            STEREO_OCULAR_DISTANCE_MAX = props.getFloatProperty("STEREO_OCULAR_DISTANCE_MAX");
            STEREO_OCULAR_DISTANCE_DEF = props.getFloatProperty("STEREO_OCULAR_DISTANCE_DEF");

            // Size settings for default startup and screenshots
            DEFAULT_SCREEN_WIDTH = props.getIntProperty("DEFAULT_SCREEN_WIDTH");
            DEFAULT_SCREEN_HEIGHT = props.getIntProperty("DEFAULT_SCREEN_HEIGHT");
            INTERFACE_WIDTH = props.getIntProperty("INTERFACE_WIDTH");
            INTERFACE_HEIGHT = props.getIntProperty("INTERFACE_HEIGHT");

            // Settings for the initial view
            INITIAL_SIMULATION_FRAME = props.getIntProperty("INITIAL_SIMULATION_FRAME");
            INITIAL_ROTATION_X = props.getFloatProperty("INITIAL_ROTATION_X");
            INITIAL_ROTATION_Y = props.getFloatProperty("INITIAL_ROTATION_Y");
            INITIAL_ZOOM = props.getFloatProperty("INITIAL_ZOOM");
            TIME_STEP_SIZE = props.getIntProperty("TIME_STEP_SIZE");

            // Setting per movie frame
            MOVIE_ROTATE = props.getBooleanProperty("MOVIE_ROTATE");
            MOVIE_ROTATION_SPEED_MIN = props.getFloatProperty("MOVIE_ROTATION_SPEED_MIN");
            MOVIE_ROTATION_SPEED_MAX = props.getFloatProperty("MOVIE_ROTATION_SPEED_MAX");
            MOVIE_ROTATION_SPEED_DEF = props.getFloatProperty("MOVIE_ROTATION_SPEED_DEF");

            // Settings for the gas cloud octree
            MAX_OCTREE_DEPTH = props.getIntProperty("MAX_OCTREE_DEPTH");
            OCTREE_EDGES = props.getFloatProperty("OCTREE_EDGES");

            // Settings that should never change, but are listed here to make
            // sure they can be found if necessary
            MAX_EXPECTED_MODELS = props.getIntProperty("MAX_EXPECTED_MODELS");

            SCREENSHOT_PATH = props.getProperty("SCREENSHOT_PATH");

            WAITTIME_FOR_RETRY = props.getLongProperty("WAITTIME_FOR_RETRY");
            WAITTIME_FOR_MOVIE = props.getLongProperty("WAITTIME_FOR_MOVIE");

            System.out.println(IMAGE_STREAM_OUTPUT ? "true" : "false");

            setIMAGE_STREAM_OUTPUT(props.getBooleanProperty("IMAGE_STREAM_OUTPUT"));

            System.out.println(IMAGE_STREAM_OUTPUT ? "true" : "false");

            // minValues.put("SSH", props.getFloatProperty("MIN_SSH"));
            // maxValues.put("SSH", props.getFloatProperty("MAX_SSH"));
            // currentMinValues.put("SSH",
            // props.getFloatProperty("SET_MIN_SSH"));
            // currentMaxValues.put("SSH",
            // props.getFloatProperty("SET_MAX_SSH"));
            // diffMinValues.put("SSH", props.getFloatProperty("DIFF_MIN_SSH"));
            // diffMaxValues.put("SSH", props.getFloatProperty("DIFF_MAX_SSH"));
            // currentDiffMinValues.put("SSH",
            // props.getFloatProperty("SET_DIFF_MIN_SSH"));
            // currentDiffMaxValues.put("SSH",
            // props.getFloatProperty("SET_DIFF_MAX_SSH"));
            //
            // minValues.put("SHF", props.getFloatProperty("MIN_SHF"));
            // maxValues.put("SHF", props.getFloatProperty("MAX_SHF"));
            // currentMinValues.put("SHF",
            // props.getFloatProperty("SET_MIN_SHF"));
            // currentMaxValues.put("SHF",
            // props.getFloatProperty("SET_MAX_SHF"));
            // diffMinValues.put("SHF", props.getFloatProperty("DIFF_MIN_SHF"));
            // diffMaxValues.put("SHF", props.getFloatProperty("DIFF_MAX_SHF"));
            // currentDiffMinValues.put("SHF",
            // props.getFloatProperty("SET_DIFF_MIN_SHF"));
            // currentDiffMaxValues.put("SHF",
            // props.getFloatProperty("SET_DIFF_MAX_SHF"));
            //
            // minValues.put("SFWF", props.getFloatProperty("MIN_SFWF"));
            // maxValues.put("SFWF", props.getFloatProperty("MAX_SFWF"));
            // currentMinValues.put("SFWF",
            // props.getFloatProperty("SET_MIN_SFWF"));
            // currentMaxValues.put("SFWF",
            // props.getFloatProperty("SET_MAX_SFWF"));
            // diffMinValues.put("SFWF",
            // props.getFloatProperty("DIFF_MIN_SFWF"));
            // diffMaxValues.put("SFWF",
            // props.getFloatProperty("DIFF_MAX_SFWF"));
            // currentDiffMinValues.put("SFWF",
            // props.getFloatProperty("SET_DIFF_MIN_SFWF"));
            // currentDiffMaxValues.put("SFWF",
            // props.getFloatProperty("SET_DIFF_MAX_SFWF"));
            //
            // minValues.put("HMXL", props.getFloatProperty("MIN_HMXL"));
            // maxValues.put("HMXL", props.getFloatProperty("MAX_HMXL"));
            // currentMinValues.put("HMXL",
            // props.getFloatProperty("SET_MIN_HMXL"));
            // currentMaxValues.put("HMXL",
            // props.getFloatProperty("SET_MAX_HMXL"));
            // diffMinValues.put("HMXL",
            // props.getFloatProperty("DIFF_MIN_HMXL"));
            // diffMaxValues.put("HMXL",
            // props.getFloatProperty("DIFF_MAX_HMXL"));
            // currentDiffMinValues.put("HMXL",
            // props.getFloatProperty("SET_DIFF_MIN_HMXL"));
            // currentDiffMaxValues.put("HMXL",
            // props.getFloatProperty("SET_DIFF_MAX_HMXL"));
            //
            // minValues.put("XMXL", props.getFloatProperty("MIN_XMXL"));
            // maxValues.put("XMXL", props.getFloatProperty("MAX_XMXL"));
            // currentMinValues.put("XMXL",
            // props.getFloatProperty("SET_MIN_XMXL"));
            // currentMaxValues.put("XMXL",
            // props.getFloatProperty("SET_MAX_XMXL"));
            // diffMinValues.put("XMXL",
            // props.getFloatProperty("DIFF_MIN_XMXL"));
            // diffMaxValues.put("XMXL",
            // props.getFloatProperty("DIFF_MAX_XMXL"));
            // currentDiffMinValues.put("XMXL",
            // props.getFloatProperty("SET_DIFF_MIN_XMXL"));
            // currentDiffMaxValues.put("XMXL",
            // props.getFloatProperty("SET_DIFF_MAX_XMXL"));
            //
            // minValues.put("TMXL", props.getFloatProperty("MIN_TMXL"));
            // maxValues.put("TMXL", props.getFloatProperty("MAX_TMXL"));
            // currentMinValues.put("TMXL",
            // props.getFloatProperty("SET_MIN_TMXL"));
            // currentMaxValues.put("TMXL",
            // props.getFloatProperty("SET_MAX_TMXL"));
            // diffMinValues.put("TMXL",
            // props.getFloatProperty("DIFF_MIN_TMXL"));
            // diffMaxValues.put("TMXL",
            // props.getFloatProperty("DIFF_MAX_TMXL"));
            // currentDiffMinValues.put("TMXL",
            // props.getFloatProperty("SET_DIFF_MIN_TMXL"));
            // currentDiffMaxValues.put("TMXL",
            // props.getFloatProperty("SET_DIFF_MAX_TMXL"));
            //
            // minValues.put("SALT", props.getFloatProperty("MIN_SALT"));
            // maxValues.put("SALT", props.getFloatProperty("MAX_SALT"));
            // currentMinValues.put("SALT",
            // props.getFloatProperty("SET_MIN_SALT"));
            // currentMaxValues.put("SALT",
            // props.getFloatProperty("SET_MAX_SALT"));
            // diffMinValues.put("SALT",
            // props.getFloatProperty("DIFF_MIN_SALT"));
            // diffMaxValues.put("SALT",
            // props.getFloatProperty("DIFF_MAX_SALT"));
            // currentDiffMinValues.put("SALT",
            // props.getFloatProperty("SET_DIFF_MIN_SALT"));
            // currentDiffMaxValues.put("SALT",
            // props.getFloatProperty("SET_DIFF_MAX_SALT"));
            //
            // minValues.put("TEMP", props.getFloatProperty("MIN_TEMP"));
            // maxValues.put("TEMP", props.getFloatProperty("MAX_TEMP"));
            // currentMinValues.put("TEMP",
            // props.getFloatProperty("SET_MIN_TEMP"));
            // currentMaxValues.put("TEMP",
            // props.getFloatProperty("SET_MAX_TEMP"));
            // diffMinValues.put("TEMP",
            // props.getFloatProperty("DIFF_MIN_TEMP"));
            // diffMaxValues.put("TEMP",
            // props.getFloatProperty("DIFF_MAX_TEMP"));
            // currentDiffMinValues.put("TEMP",
            // props.getFloatProperty("SET_DIFF_MIN_TEMP"));
            // currentDiffMaxValues.put("TEMP",
            // props.getFloatProperty("SET_DIFF_MAX_TEMP"));
            //
            // minValues.put("UVEL", props.getFloatProperty("MIN_UVEL"));
            // maxValues.put("UVEL", props.getFloatProperty("MAX_UVEL"));
            // currentMinValues.put("UVEL",
            // props.getFloatProperty("SET_MIN_UVEL"));
            // currentMaxValues.put("UVEL",
            // props.getFloatProperty("SET_MAX_UVEL"));
            // diffMinValues.put("UVEL",
            // props.getFloatProperty("DIFF_MIN_UVEL"));
            // diffMaxValues.put("UVEL",
            // props.getFloatProperty("DIFF_MAX_UVEL"));
            // currentDiffMinValues.put("UVEL",
            // props.getFloatProperty("SET_DIFF_MIN_UVEL"));
            // currentDiffMaxValues.put("UVEL",
            // props.getFloatProperty("SET_DIFF_MAX_UVEL"));
            //
            // minValues.put("VVEL", props.getFloatProperty("MIN_VVEL"));
            // maxValues.put("VVEL", props.getFloatProperty("MAX_VVEL"));
            // currentMinValues.put("VVEL",
            // props.getFloatProperty("SET_MIN_VVEL"));
            // currentMaxValues.put("VVEL",
            // props.getFloatProperty("SET_MAX_VVEL"));
            // diffMinValues.put("VVEL",
            // props.getFloatProperty("DIFF_MIN_VVEL"));
            // diffMaxValues.put("VVEL",
            // props.getFloatProperty("DIFF_MAX_VVEL"));
            // currentDiffMinValues.put("VVEL",
            // props.getFloatProperty("SET_DIFF_MIN_VVEL"));
            // currentDiffMaxValues.put("VVEL",
            // props.getFloatProperty("SET_DIFF_MAX_VVEL"));
            //
            // minValues.put("KE", props.getFloatProperty("MIN_KE"));
            // maxValues.put("KE", props.getFloatProperty("MAX_KE"));
            // currentMinValues.put("KE", props.getFloatProperty("SET_MIN_KE"));
            // currentMaxValues.put("KE", props.getFloatProperty("SET_MAX_KE"));
            // diffMinValues.put("KE", props.getFloatProperty("DIFF_MIN_KE"));
            // diffMaxValues.put("KE", props.getFloatProperty("DIFF_MAX_KE"));
            // currentDiffMinValues.put("KE",
            // props.getFloatProperty("SET_DIFF_MIN_KE"));
            // currentDiffMaxValues.put("KE",
            // props.getFloatProperty("SET_DIFF_MAX_KE"));
            //
            // minValues.put("PD", props.getFloatProperty("MIN_PD"));
            // maxValues.put("PD", props.getFloatProperty("MAX_PD"));
            // currentMinValues.put("PD", props.getFloatProperty("SET_MIN_PD"));
            // currentMaxValues.put("PD", props.getFloatProperty("SET_MAX_PD"));
            // diffMinValues.put("PD", props.getFloatProperty("DIFF_MIN_PD"));
            // diffMaxValues.put("PD", props.getFloatProperty("DIFF_MAX_PD"));
            // currentDiffMinValues.put("PD",
            // props.getFloatProperty("SET_DIFF_MIN_PD"));
            // currentDiffMaxValues.put("PD",
            // props.getFloatProperty("SET_DIFF_MAX_PD"));
            //
            // minValues.put("TAUX", props.getFloatProperty("MIN_TAUX"));
            // maxValues.put("TAUX", props.getFloatProperty("MAX_TAUX"));
            // currentMinValues.put("TAUX",
            // props.getFloatProperty("SET_MIN_TAUX"));
            // currentMaxValues.put("TAUX",
            // props.getFloatProperty("SET_MAX_TAUX"));
            // diffMinValues.put("TAUX",
            // props.getFloatProperty("DIFF_MIN_TAUX"));
            // diffMaxValues.put("TAUX",
            // props.getFloatProperty("DIFF_MAX_TAUX"));
            // currentDiffMinValues.put("TAUX",
            // props.getFloatProperty("SET_DIFF_MIN_TAUX"));
            // currentDiffMaxValues.put("TAUX",
            // props.getFloatProperty("SET_DIFF_MAX_TAUX"));
            //
            // minValues.put("TAUY", props.getFloatProperty("MIN_TAUY"));
            // maxValues.put("TAUY", props.getFloatProperty("MAX_TAUY"));
            // currentMinValues.put("TAUY",
            // props.getFloatProperty("SET_MIN_TAUY"));
            // currentMaxValues.put("TAUY",
            // props.getFloatProperty("SET_MAX_TAUY"));
            // diffMinValues.put("TAUY",
            // props.getFloatProperty("DIFF_MIN_TAUY"));
            // diffMaxValues.put("TAUY",
            // props.getFloatProperty("DIFF_MAX_TAUY"));
            // currentDiffMinValues.put("TAUY",
            // props.getFloatProperty("SET_DIFF_MIN_TAUY"));
            // currentDiffMaxValues.put("TAUY",
            // props.getFloatProperty("SET_DIFF_MAX_TAUY"));
            //
            // minValues.put("H2", props.getFloatProperty("MIN_H2"));
            // maxValues.put("H2", props.getFloatProperty("MAX_H2"));
            // currentMinValues.put("H2", props.getFloatProperty("SET_MIN_H2"));
            // currentMaxValues.put("H2", props.getFloatProperty("SET_MAX_H2"));
            // diffMinValues.put("H2", props.getFloatProperty("DIFF_MIN_H2"));
            // diffMaxValues.put("H2", props.getFloatProperty("DIFF_MAX_H2"));
            // currentDiffMinValues.put("H2",
            // props.getFloatProperty("SET_DIFF_MIN_H2"));
            // currentDiffMaxValues.put("H2",
            // props.getFloatProperty("SET_DIFF_MAX_H2"));

        } catch (NumberFormatException e) {
            logger.warn(e.getMessage());
        }

        initializeScreenDescriptions();
    }

    private synchronized void initializeScreenDescriptions() {
        screenDescriptions = new SurfaceTextureDescription[number_of_screens_col * number_of_screens_row];        
    }

    public synchronized void setWaittimeBeforeRetry(long value) {
        WAITTIME_FOR_RETRY = value;
    }

    public synchronized void setWaittimeMovie(long value) {
        WAITTIME_FOR_MOVIE = value;
    }

    public synchronized void setEpsilon(float value) {
        EPSILON = value;
    }

    public synchronized void setFileExtensionLength(int value) {
        FILE_EXTENSION_LENGTH = value;
    }

    public synchronized void setFileNumberLength(int value) {
        FILE_NUMBER_LENGTH = value;
    }

    public synchronized void setCurrentExtension(String value) {
        CURRENT_POSTFIX = value;
    }

    public synchronized long getWaittimeBeforeRetry() {
        return WAITTIME_FOR_RETRY;
    }

    public synchronized long getWaittimeMovie() {
        return WAITTIME_FOR_MOVIE;
    }

    public synchronized float getEpsilon() {
        return EPSILON;
    }

    public synchronized int getFileExtensionLength() {
        return FILE_EXTENSION_LENGTH;
    }

    public synchronized int getFileNumberLength() {
        return FILE_NUMBER_LENGTH;
    }

    public synchronized String[] getAcceptableExtensions() {
        return ACCEPTABLE_POSTFIXES;
    }

    public synchronized String getCurrentExtension() {
        return CURRENT_POSTFIX;
    }

    public synchronized int getPreprocessAmount() {
        return PREPROCESSING_AMOUNT;
    }

    public synchronized void setPreprocessAmount(int value) {
        PREPROCESSING_AMOUNT = value;
    }

    public synchronized float getVarDiffMax(String var) {
        return diffMaxValues.get(var);
    }

    public synchronized float getVarDiffMin(String var) {
        return diffMinValues.get(var);
    }

    public synchronized int getDepthMin() {
        return DEPTH_MIN;
    }

    public synchronized void setDepthMin(int value) {
        DEPTH_MIN = value;
    }

    public synchronized int getDepthDef() {
        return DEPTH_DEF;
    }

    public synchronized void setFrameNumber(int newFrameNumber) {
        for (int i = 0; i < number_of_screens_col * number_of_screens_row; i++) {
            SurfaceTextureDescription currentState = screenDescriptions[i];
            screenDescriptions[i] = new SurfaceTextureDescription(newFrameNumber, currentState.getDepth(),
                    currentState.getVarName(), currentState.getColorMap(), currentState.isDynamicDimensions(),
                    currentState.isDiff(), currentState.isSecondSet(), currentState.getLowerBound(),
                    currentState.getUpperBound(), currentState.isLogScale());
        }
        
        setRequestedNewConfiguration(true);
    }

    public synchronized void setDepth(int value) {
        for (int i = 0; i < number_of_screens_col * number_of_screens_row; i++) {
            SurfaceTextureDescription currentState = screenDescriptions[i];
            screenDescriptions[i] = new SurfaceTextureDescription(currentState.getFrameNumber(), value,
                    currentState.getVarName(), currentState.getColorMap(), currentState.isDynamicDimensions(),
                    currentState.isDiff(), currentState.isSecondSet(), currentState.getLowerBound(),
                    currentState.getUpperBound(), currentState.isLogScale());
        }

        DEPTH_DEF = value;
        
        setRequestedNewConfiguration(true);
    }

    public synchronized int getDepthMax() {
        return DEPTH_MAX;
    }

    public synchronized void setDepthMax(int value) {
        DEPTH_MAX = value;
    }

    public synchronized void setWindowSelection(int i) {
        WINDOW_SELECTION = i;

        setRequestedNewConfiguration(true);
    }

    public synchronized int getWindowSelection() {
    	if (number_of_screens_col * number_of_screens_row == 1) {
    		return 1;
    	}
        return WINDOW_SELECTION;
    }

    public synchronized String selectionToString(int windowSelection) {
        if (windowSelection == 1) {
            return "Left Top";
        } else if (windowSelection == 2) {
            return "Right Top";
        } else if (windowSelection == 3) {
            return "Left Bottom";
        } else if (windowSelection == 4) {
            return "Right Bottom";
        }

        return "All";
    }

    public synchronized void setDataMode(int screenNumber, boolean dynamic, boolean diff, boolean secondSet) {
        SurfaceTextureDescription state = screenDescriptions[screenNumber];

        SurfaceTextureDescription result;
        result = new SurfaceTextureDescription(state.getFrameNumber(), state.getDepth(), state.getVarName(),
                state.getColorMap(), dynamic, diff, secondSet, state.getLowerBound(), state.getUpperBound(),
                state.isLogScale());
        screenDescriptions[screenNumber] = result;
        
        setRequestedNewConfiguration(true);
    }

    public synchronized void setVariable(int screenNumber, String variable) {
        SurfaceTextureDescription state = screenDescriptions[screenNumber];
        SurfaceTextureDescription result = new SurfaceTextureDescription(state.getFrameNumber(), state.getDepth(),
                variable, getCurrentColormap(variable), state.isDynamicDimensions(), state.isDiff(),
                state.isSecondSet(), state.getLowerBound(), state.getUpperBound(), state.isLogScale());
        screenDescriptions[screenNumber] = result;
        
        setRequestedNewConfiguration(true);
    }

    public synchronized SurfaceTextureDescription getSurfaceDescription(int screenNumber) {
        return screenDescriptions[screenNumber];
    }

    public synchronized void setColorMap(int screenNumber, String selectedColorMap) {
        SurfaceTextureDescription state = screenDescriptions[screenNumber];
        SurfaceTextureDescription result = new SurfaceTextureDescription(state.getFrameNumber(), state.getDepth(),
                state.getVarName(), selectedColorMap, state.isDynamicDimensions(), state.isDiff(), state.isSecondSet(),
                state.getLowerBound(), state.getUpperBound(), state.isLogScale());
        screenDescriptions[screenNumber] = result;
        currentColormap.put(state.getVarName(), selectedColorMap);

//        if (cacheFileManagerAtDataLocation != null) {
//            cacheFileManagerAtDataLocation.writeColormap(state.getVarName(), selectedColorMap);
//        }
//        if (cacheFileManagerAtProgramLocation != null) {
//            cacheFileManagerAtProgramLocation.writeColormap(state.getVarName(), selectedColorMap);
//        }
        
        setRequestedNewConfiguration(true);
    }

    public synchronized boolean isIMAGE_STREAM_OUTPUT() {
        return IMAGE_STREAM_OUTPUT;
    }

    public synchronized void setIMAGE_STREAM_OUTPUT(boolean value) {
        IMAGE_STREAM_OUTPUT = value;
    }

    public synchronized String getSAGE_DIRECTORY() {
        return SAGE_DIRECTORY;
    }

    public synchronized void setSAGE_DIRECTORY(String sAGE_DIRECTORY) {
        SAGE_DIRECTORY = sAGE_DIRECTORY;
    }

    public synchronized boolean isIMAGE_STREAM_GL_ONLY() {
        return IMAGE_STREAM_GL_ONLY;
    }

    public synchronized void setIMAGE_STREAM_GL_ONLY(boolean iMAGE_STREAM_GL_ONLY) {
        IMAGE_STREAM_GL_ONLY = iMAGE_STREAM_GL_ONLY;
    }

    public synchronized float getHeightDistortion() {
        return HEIGHT_DISTORION;
    }

    public synchronized float getHeightDistortionMin() {
        return HEIGHT_DISTORION_MIN;
    }

    public synchronized float getHeightDistortionMax() {
        return HEIGHT_DISTORION_MAX;
    }

    public synchronized void setHeightDistortion(float value) {
        HEIGHT_DISTORION = value;
    }

    public synchronized boolean isTouchConnected() {
        return TOUCH_CONNECTED;
    }

    public synchronized String getFancyDate(long l) {
        String result = "";

        int year = (int) Math.floor(l / 100);

        long month = l - year * 100;

        if (month == 1) {
            result = "Jan";
        } else if (month == 2) {
            result = "Feb";
        } else if (month == 3) {
            result = "Mar";
        } else if (month == 4) {
            result = "Apr";
        } else if (month == 5) {
            result = "May";
        } else if (month == 6) {
            result = "Jun";
        } else if (month == 7) {
            result = "Jul";
        } else if (month == 8) {
            result = "Aug";
        } else if (month == 9) {
            result = "Sep";
        } else if (month == 10) {
            result = "Oct";
        } else if (month == 11) {
            result = "Nov";
        } else if (month == 12) {
            result = "Dec";
        } else {
            result = "error: " + month;
        }

        result += ", year " + year;

        return result;
    }

    public synchronized int getSageFramesPerSecond() {
        return SAGE_FRAMES_PER_SECOND;
    }

    public synchronized int getTimestep() {
        return TIME_STEP_SIZE;
    }

    public synchronized void setTimestep(int value) {
        System.out.println("Timestep set to: " + value);
        TIME_STEP_SIZE = value;
    }

    public synchronized boolean getStereo() {
        return STEREO_RENDERING;
    }

    public synchronized void setStereo(int stateChange) {
        if (stateChange == 1)
            STEREO_RENDERING = true;
        if (stateChange == 2)
            STEREO_RENDERING = false;
    }

    public synchronized boolean getStereoSwitched() {
        return STEREO_SWITCHED;
    }

    public synchronized void setStereoSwitched(int stateChange) {
        if (stateChange == 1)
            STEREO_SWITCHED = true;
        if (stateChange == 2)
            STEREO_SWITCHED = false;
    }

    public synchronized float getStereoOcularDistanceMin() {
        return STEREO_OCULAR_DISTANCE_MIN;
    }

    public synchronized float getStereoOcularDistanceMax() {
        return STEREO_OCULAR_DISTANCE_MAX;
    }

    public synchronized float getStereoOcularDistance() {
        return STEREO_OCULAR_DISTANCE_DEF;
    }

    public synchronized void setStereoOcularDistance(float value) {
        STEREO_OCULAR_DISTANCE_DEF = value;
    }

    public synchronized int getDefaultScreenWidth() {
        return DEFAULT_SCREEN_WIDTH;
    }

    public synchronized int getDefaultScreenHeight() {
        return DEFAULT_SCREEN_HEIGHT;
    }

    public synchronized int getScreenshotScreenWidth() {
        return SCREENSHOT_SCREEN_WIDTH;
    }

    public synchronized int getScreenshotScreenHeight() {
        return SCREENSHOT_SCREEN_HEIGHT;
    }

    public synchronized int getMaxOctreeDepth() {
        return MAX_OCTREE_DEPTH;
    }

    public synchronized float getOctreeEdges() {
        return OCTREE_EDGES;
    }

    public synchronized int getMaxExpectedModels() {
        return MAX_EXPECTED_MODELS;
    }

    public synchronized float getInitialRotationX() {
        return INITIAL_ROTATION_X;
    }

    public synchronized float getInitialRotationY() {
        return INITIAL_ROTATION_Y;
    }

    public synchronized float getInitialZoom() {
        return INITIAL_ZOOM;
    }

    public synchronized void setMovieRotate(int stateChange) {
        if (stateChange == 1)
            MOVIE_ROTATE = true;
        if (stateChange == 2)
            MOVIE_ROTATE = false;
    }

    public synchronized boolean getMovieRotate() {
        return MOVIE_ROTATE;
    }

    public synchronized void setMovieRotationSpeed(float value) {
        MOVIE_ROTATION_SPEED_DEF = value;
    }

    public synchronized float getMovieRotationSpeedMin() {
        return MOVIE_ROTATION_SPEED_MIN;
    }

    public synchronized float getMovieRotationSpeedMax() {
        return MOVIE_ROTATION_SPEED_MAX;
    }

    public synchronized float getMovieRotationSpeedDef() {
        return MOVIE_ROTATION_SPEED_DEF;
    }

    public synchronized int getInitialSimulationFrame() {
        return INITIAL_SIMULATION_FRAME;
    }

    public synchronized void setInitial_simulation_frame(int initialSimulationFrame) {
        INITIAL_SIMULATION_FRAME = initialSimulationFrame;
    }

    public synchronized void setInitial_rotation_x(float initialRotationX) {
        INITIAL_ROTATION_X = initialRotationX;
    }

    public synchronized void setInitial_rotation_y(float initialRotationY) {
        INITIAL_ROTATION_Y = initialRotationY;
    }

    public synchronized String getScreenshotPath() {
        return SCREENSHOT_PATH;
    }

    public synchronized void setScreenshotPath(String newPath) {
        SCREENSHOT_PATH = newPath;
    }

    public synchronized void setVariableRange(int screenNumber, String varName, int sliderLowerValue,
            int sliderUpperValue) {
        float diff = getVarMax(varName) - getVarMin(varName);

        float calcMin = (sliderLowerValue / 100f) * diff + getVarMin(varName);
        float calcMax = (sliderUpperValue / 100f) * diff + getVarMin(varName);

        // calcMin = Math.round(calcMin * 10f) / 10f;
        // calcMax = Math.round(calcMax * 10f) / 10f;

        currentMinValues.put(varName, calcMin);
        currentMaxValues.put(varName, calcMax);
        float minFloatValue = getCurrentVarMin(varName);
        float maxFloatValue = getCurrentVarMax(varName);

        SurfaceTextureDescription state = screenDescriptions[screenNumber];
        SurfaceTextureDescription result = new SurfaceTextureDescription(state.getFrameNumber(), state.getDepth(),
                state.getVarName(), state.getColorMap(), state.isDynamicDimensions(), state.isDiff(),
                state.isSecondSet(), minFloatValue, maxFloatValue, state.isLogScale());
        screenDescriptions[screenNumber] = result;
        
        setRequestedNewConfiguration(true);
    }

    public synchronized int getRangeSliderLowerValue(int screenNumber) {
        SurfaceTextureDescription state = screenDescriptions[screenNumber];

        float min = getVarMin(state.getVarName());
        float max = getVarMax(state.getVarName());
        float currentMin = getCurrentVarMin(state.getVarName());

        float diff = max - min;
        float result = (currentMin - min) / diff;

        return (int) (result * 100) - 1;
    }

    public synchronized int getRangeSliderUpperValue(int screenNumber) {
        SurfaceTextureDescription state = screenDescriptions[screenNumber];

        float min = getVarMin(state.getVarName());
        float max = getVarMax(state.getVarName());
        float currentMax = getCurrentVarMax(state.getVarName());

        float diff = max - min;
        float result = (currentMax - min) / diff;

        return (int) (result * 100) - 1;
    }

    public synchronized String getWidthSubstring() {
        return grid_width_dimension_substring;
    }

    public synchronized String getHeightSubstring() {
        return grid_height_dimension_substring;
    }

    public synchronized int getNumScreensRows() {
        return number_of_screens_row;
    }

    public synchronized int getNumScreensCols() {
        return number_of_screens_col;
    }

    public synchronized void setNumberOfScreens(int rows, int columns) {
        number_of_screens_row = rows;
        number_of_screens_col = columns;

        initializeScreenDescriptions();
    }

    public synchronized int getInterfaceWidth() {
        return INTERFACE_WIDTH;
    }

    public synchronized int getInterfaceHeight() {
        return INTERFACE_HEIGHT;
    }

    public synchronized String getCurrentColormap(String key) {
        String colormap = "";
        if (currentColormap.containsKey(key)) {
            colormap = currentColormap.get(key);
        } else if (cacheFileManagerAtDataLocation != null) {
            colormap = cacheFileManagerAtDataLocation.readColormap(key);
        } else if (cacheFileManagerAtProgramLocation != null) {
            colormap = cacheFileManagerAtProgramLocation.readColormap(key);
        }

        if (colormap.compareTo("") == 0) {
            colormap = JOCLColormapper.getColormapNames()[0];
        }

        return colormap;
    }

    public synchronized float getVarMin(String key) {
        float value;
        if (minValues.containsKey(key)) {
            value = minValues.get(key);
        } else {
            value = Float.NaN;
        }

        return value;
    }

    public synchronized float getVarMax(String key) {
        float value;
        if (maxValues.containsKey(key)) {
            value = maxValues.get(key);
        } else {
            value = Float.NaN;
        }

        return value;
    }

    public synchronized float getCurrentVarDiffMin(String key) {
        float value;
        if (currentDiffMinValues.containsKey(key)) {
            value = currentDiffMinValues.get(key);
        } else {
            value = minValues.get(key);
        }

        return value;
    }

    public synchronized float getCurrentVarDiffMax(String key) {
        float value;
        if (currentDiffMaxValues.containsKey(key)) {
            value = currentDiffMaxValues.get(key);
        } else {
            value = maxValues.get(key);
        }

        return value;
    }

    public synchronized float getCurrentVarMin(String key) {
        float value;
        if (currentMinValues.containsKey(key)) {
            value = currentMinValues.get(key);
        } else {
            value = minValues.get(key);
        }

        return value;
    }

    public synchronized float getCurrentVarMax(String key) {
        float value;
        if (currentMaxValues.containsKey(key)) {
            value = currentMaxValues.get(key);
        } else {
            value = maxValues.get(key);
        }

        return value;
    }

    public synchronized void setVarMin(String key, float currentMin) {
        minValues.put(key, currentMin);
        currentMinValues.put(key, currentMin);
        
        setRequestedNewConfiguration(true);
    }

    public synchronized void setVarMax(String key, float currentMax) {
        maxValues.put(key, currentMax);
        currentMaxValues.put(key, currentMax);
        
        setRequestedNewConfiguration(true);
    }

    public synchronized void initDefaultVariables(ArrayList<String> variables, int frameNumber) {
        screenDescriptions = new SurfaceTextureDescription[number_of_screens_col * number_of_screens_row];

        if (variables.size() != 0) {
            for (int j = 0; j < number_of_screens_col * number_of_screens_row; j++) {
            	if (cacheFileManagerAtDataLocation != null) {
	            	String presetVar = cacheFileManagerAtDataLocation.readString("onScreen", "scr#"+j);
	            	if (presetVar.compareTo("") != 0) {
	            		screenDescriptions[j] = new SurfaceTextureDescription(frameNumber, 0, presetVar, getCurrentColormap(presetVar), false, false,
		                        false, getCurrentVarMin(presetVar), getCurrentVarMax(presetVar), false);
	            	}
            	}
            	if (screenDescriptions[j] == null) {            	
	                String var;
	                if (j < variables.size()) {
	                    var = variables.get(j);
	                } else {
	                    var = variables.get(0);
	                }
	                screenDescriptions[j] = new SurfaceTextureDescription(frameNumber, 0, var, getCurrentColormap(var), false, false,
	                        false, getCurrentVarMin(var), getCurrentVarMax(var), false);
            	}
            	
                logger.debug(screenDescriptions[j].toString());                
            }
        }
            
        setRequestedNewConfiguration(true);
    }

    public synchronized void setLogScale(int screenNumber, boolean selected) {
        SurfaceTextureDescription state = screenDescriptions[screenNumber];
        SurfaceTextureDescription result = new SurfaceTextureDescription(state.getFrameNumber(), state.getDepth(),
                state.getVarName(), state.getColorMap(), state.isDynamicDimensions(), state.isDiff(),
                state.isSecondSet(), state.getLowerBound(), state.getUpperBound(), selected);
        screenDescriptions[screenNumber] = result;
        
        setRequestedNewConfiguration(true);
    }

    public synchronized boolean isRequestedNewConfiguration() {
        return requestedNewConfiguration;
    }

    public synchronized void setRequestedNewConfiguration(boolean requestedNewConfiguration) {
        this.requestedNewConfiguration = requestedNewConfiguration;
    }

    public synchronized void setCacheFileManagerAtDataLocation(CacheFileManager cacheAtDataLocation) {
        this.cacheFileManagerAtDataLocation = cacheAtDataLocation;
    }

    public synchronized void setCacheFileManagerAtProgramLocation(CacheFileManager cacheAtProgramLocation) {
        this.cacheFileManagerAtProgramLocation = cacheAtProgramLocation;
    }

    public synchronized CacheFileManager getCacheFileManagerAtDataLocation() {
        return cacheFileManagerAtDataLocation;
    }

    public synchronized CacheFileManager getCacheFileManagerAtProgramLocation() {
        return cacheFileManagerAtProgramLocation;
    }

    public synchronized int getNumberOfScreenshotsPerTimeStep() {
        return numberOfScreenshotsPerTimeStep;
    }

    public synchronized void setNumberOfScreenshotsPerTimeStep(int numberOfScreenshotsPerTimeStep) {
        this.numberOfScreenshotsPerTimeStep = numberOfScreenshotsPerTimeStep;
    }
}
