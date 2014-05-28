package nl.esciencecenter.visualization.esalsa.data;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFormattedTextField;
import javax.swing.JSlider;

import nl.esciencecenter.neon.input.InputHandler;
import nl.esciencecenter.neon.math.Float3Vector;
import nl.esciencecenter.neon.math.FloatVectorMath;
import nl.esciencecenter.neon.swing.CustomJSlider;
import nl.esciencecenter.visualization.esalsa.ImauSettings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimedPlayer implements Runnable {
    private final static Logger logger = LoggerFactory.getLogger(TimedPlayer.class);

    public static enum states {
        UNOPENED, UNINITIALIZED, INITIALIZED, STOPPED, REDRAWING, SNAPSHOTTING, MOVIEMAKING, CLEANUP, WAITINGONFRAME, PLAYING
    }

    private final ImauSettings settings = ImauSettings.getInstance();

    private states currentState = states.UNOPENED;
    private long frameNumber;

    private final boolean running = true;
    private boolean initialized = false;

    private long startTime, stopTime;

    private final JSlider timeBar;
    private final JFormattedTextField frameCounter;

    private final InputHandler inputHandler;

    private DatasetManager dsManager;

    private boolean needsScreenshot = false;
    private String screenshotFilename = "";

    private final long waittime = settings.getWaittimeMovie();

    private final ArrayList<Float3Vector> bezierPoints, fixedPoints;
    private final ArrayList<Integer> bezierSteps;

    public TimedPlayer(CustomJSlider timeBar2, JFormattedTextField frameCounter) {
        this.timeBar = timeBar2;
        this.frameCounter = frameCounter;
        this.inputHandler = InputHandler.getInstance();

        bezierPoints = new ArrayList<Float3Vector>();

        fixedPoints = new ArrayList<Float3Vector>();
        bezierSteps = new ArrayList<Integer>();

        fixedPoints.add(new Float3Vector(394, 624, -80)); // Europa
        bezierSteps.add(60);
        fixedPoints.add(new Float3Vector(379, 702, -140)); // Gulf Stream
        bezierSteps.add(60);
        fixedPoints.add(new Float3Vector(359, 651, -140)); // Equator
        bezierSteps.add(60);
        fixedPoints.add(new Float3Vector(320, 599, -90)); // South Africa
        bezierSteps.add(60);
        fixedPoints.add(new Float3Vector(339, 540, -140)); // India
        bezierSteps.add(60);
        fixedPoints.add(new Float3Vector(382, 487, -80)); // Japan
        bezierSteps.add(60);
        fixedPoints.add(new Float3Vector(353, 360, -110)); // Panama
        bezierSteps.add(60);
        fixedPoints.add(new Float3Vector(311, 326, -110)); // Argentina
        bezierSteps.add(60);
        fixedPoints.add(new Float3Vector(412, 302, -140)); // Greenland
        bezierSteps.add(60);
        fixedPoints.add(new Float3Vector(394, 264, -80)); // Europa

        Float3Vector lastPoint = fixedPoints.get(0);
        Float3Vector still = new Float3Vector(0, 0, 0);
        for (int i = 1; i < fixedPoints.size(); i++) {
            Float3Vector newPoint = fixedPoints.get(i);

            Float3Vector[] bezierPointsTemp = FloatVectorMath.degreesBezierCurve(bezierSteps.get(i - 1), lastPoint,
                    still, still, newPoint);

            for (int j = 1; j < bezierPointsTemp.length; j++) {
                bezierPoints.add(bezierPointsTemp[j]);
            }

            lastPoint = newPoint;
        }
    }

    public synchronized void close() {
        initialized = false;
        frameNumber = 0;
        timeBar.setValue(0);
        frameCounter.setValue(0);
        timeBar.setMaximum(0);

        if (dsManager != null) {
            dsManager.shutdown();
        }
    }

    public synchronized void init(File[] files) {
        this.dsManager = new DatasetManager(files);

        frameNumber = dsManager.getFirstFrameNumber();
        final int initialMaxBar = dsManager.getNumFrames() - 1;

        timeBar.setMaximum(initialMaxBar);
        timeBar.setMinimum(0);

        initialized = true;
    }

    public synchronized boolean isInitialized() {
        return initialized;
    }

    public synchronized boolean isPlaying() {
        if ((currentState == states.PLAYING) || (currentState == states.MOVIEMAKING)) {
            return true;
        }

        return false;
    }

    public synchronized void movieMode() {
        currentState = states.MOVIEMAKING;
    }

    public synchronized void oneBack() {
        stop();

        try {
            long newFrameNumber = dsManager.getPreviousFrameNumber(frameNumber);
            updateFrame(newFrameNumber, false);
        } catch (IOException e) {
            logger.debug("One back failed.");
        }
    }

    public synchronized void oneForward() {
        stop();

        try {
            long newFrameNumber = dsManager.getNextFrameNumber(frameNumber);
            updateFrame(newFrameNumber, false);
        } catch (IOException e) {
            logger.debug("One forward failed.");
        }
    }

    public synchronized void redraw() {
        if (initialized) {
            updateFrame(frameNumber, true);
            currentState = states.REDRAWING;
        }
    }

    public synchronized void rewind() {
        stop();
        long newFrameNumber = dsManager.getFirstFrameNumber();
        updateFrame(newFrameNumber, false);
    }

    public synchronized void setScreenshotNeeded(boolean value) {
        if (value) {
            final Float3Vector rotation = inputHandler.getRotation();
            final float viewDist = inputHandler.getViewDist();

            System.out.println("Simulation frame: " + frameNumber + ", Rotation x: " + rotation.getX() + " y: "
                    + rotation.getY() + " , viewDist: " + viewDist);

            screenshotFilename = settings.getScreenshotPath() + String.format("%05d", (frameNumber)) + ".png";

            System.out.println("Screenshot filename: " + screenshotFilename);
        }
        needsScreenshot = value;
    }

    public synchronized boolean isScreenshotNeeded() {
        return needsScreenshot;
    }

    public synchronized String getScreenshotFileName() {
        return screenshotFilename;
    }

    @Override
    public void run() {
        if (!initialized) {
            System.err.println("HDFTimer started while not initialized.");
            System.exit(1);
        }

        inputHandler.setRotation(new Float3Vector(settings.getInitialRotationX(), settings.getInitialRotationY(), 0f));
        inputHandler.setViewDist(settings.getInitialZoom());

        stop();

        while (running) {
            if ((currentState == states.PLAYING) || (currentState == states.REDRAWING)
                    || (currentState == states.MOVIEMAKING)) {
                try {
                    if (!isScreenshotNeeded()) {
                        startTime = System.currentTimeMillis();

                        if (currentState == states.MOVIEMAKING) {
                            final Float3Vector rotation = inputHandler.getRotation();
                            if (settings.getMovieRotate()) {
                                inputHandler.setRotation(new Float3Vector(bezierPoints.get(
                                        dsManager.getIndexOfFrameNumber(frameNumber)).getX(), bezierPoints.get(
                                        dsManager.getIndexOfFrameNumber(frameNumber)).getY(), 0f));
                                inputHandler.setViewDist(bezierPoints.get(dsManager.getIndexOfFrameNumber(frameNumber))
                                        .getZ());
                                setScreenshotNeeded(true);
                            } else {
                                setScreenshotNeeded(true);
                            }
                        }

                        // Forward frame
                        if (currentState != states.REDRAWING) {
                            long newFrameNumber;
                            try {
                                newFrameNumber = dsManager.getNextFrameNumber(frameNumber);
                                updateFrame(newFrameNumber, false);
                            } catch (IndexOutOfBoundsException e) {
                                logger.debug("Waiting on frame after " + frameNumber);
                                currentState = states.WAITINGONFRAME;
                            } catch (IOException e) {
                                logger.debug("IOException " + e);
                            }
                        }

                        // Wait for the _rest_ of the timeframe
                        stopTime = System.currentTimeMillis();
                        long spentTime = stopTime - startTime;

                        if (spentTime < waittime) {
                            Thread.sleep(waittime - spentTime);
                        }
                    }
                } catch (final InterruptedException e) {
                    System.err.println("Interrupted while playing.");
                }
            } else if (currentState == states.STOPPED) {
                try {
                    Thread.sleep(100);
                } catch (final InterruptedException e) {
                    System.err.println("Interrupted while stopped.");
                }
            } else if (currentState == states.REDRAWING) {
                currentState = states.STOPPED;
            } else if (currentState == states.WAITINGONFRAME) {
                rewind();
                start();
            }
        }
    }

    public synchronized void setFrameByIndex(int value, boolean overrideUpdate) {
        stop();

        long frameNumberIndex;
        try {
            frameNumberIndex = dsManager.getFirstFrameNumber();
            for (int i = 0; i < value; i++) {
                frameNumberIndex = dsManager.getNextFrameNumber(frameNumberIndex);
            }
        } catch (IOException e) {
            logger.error("Frame index of " + value + " not found.");
            frameNumberIndex = dsManager.getFirstFrameNumber();
        }

        updateFrame(frameNumberIndex, overrideUpdate);
    }

    public synchronized void start() {
        currentState = states.PLAYING;
    }

    public synchronized void stop() {
        currentState = states.STOPPED;
    }

    private synchronized void updateFrame(long newFrameNumber, boolean overrideUpdate) {
        if (dsManager != null) {
            if (newFrameNumber != frameNumber || overrideUpdate) {
                if (!settings.isRequestedNewConfiguration()) {
                    frameNumber = newFrameNumber;
                    settings.setFrameNumber(newFrameNumber);
                    this.timeBar.setValue(dsManager.getIndexOfFrameNumber(newFrameNumber));
                    this.frameCounter.setValue(dsManager.getIndexOfFrameNumber(newFrameNumber));

                    settings.setRequestedNewConfiguration(true);
                }
            }
        }
    }

    public synchronized TextureStorage getTextureStorage(String varName) throws DatasetNotFoundException {
        return dsManager.getTextureStorage(varName);
    }

    public synchronized List<String> getVariables() {
        return dsManager.getVariables();
    }

    public synchronized String getVariableUnits(String varName) throws DatasetNotFoundException {
        return dsManager.getVariableUnits(varName);
    }

    public synchronized float getMinValueContainedInDataset(String varName) throws DatasetNotFoundException {
        return dsManager.getMinValueContainedInDataset(varName);
    }

    public synchronized float getMaxValueContainedInDataset(String varName) throws DatasetNotFoundException {
        return dsManager.getMaxValueContainedInDataset(varName);
    }

    public long getInitialFrameNumber() {
        return dsManager.getFirstFrameNumber();
    }
}
