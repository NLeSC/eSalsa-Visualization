package nl.esciencecenter.visualization.esalsa.data;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.swing.JFormattedTextField;
import javax.swing.JSlider;

import nl.esciencecenter.neon.exceptions.UninitializedException;
import nl.esciencecenter.neon.math.Float3Vector;
import nl.esciencecenter.neon.math.Float4Vector;
import nl.esciencecenter.neon.math.FloatVectorMath;
import nl.esciencecenter.neon.swing.CustomJSlider;
import nl.esciencecenter.visualization.esalsa.ImauInputHandler;
import nl.esciencecenter.visualization.esalsa.ImauPanel.KeyFrame;
import nl.esciencecenter.visualization.esalsa.ImauSettings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimedPlayer implements Runnable {
    private final static Logger logger = LoggerFactory.getLogger(TimedPlayer.class);

    public static enum states {
        UNOPENED, UNINITIALIZED, INITIALIZED, STOPPED, REDRAWING, SNAPSHOTTING, MOVIEMAKING, CLEANUP, WAITINGONFRAME, PLAYING, REVIEW
    }

    private class Orientation {
        private final Float3Vector rotation;
        private final float viewDist;

        public Orientation(Float3Vector curveSteps, float viewDist) {
            this.rotation = curveSteps;
            this.viewDist = viewDist;
        }

        public Float3Vector getRotation() {
            return rotation;
        }

        public float getViewDist() {
            return viewDist;
        }

        @Override
        public String toString() {
            return "#: " + rotation + " " + viewDist;
        }
    }
    
    private class MovieFrame implements Comparable<MovieFrame> {
    	private int frameNumber;
    	private int intermediateStep;
    	private Orientation orientation;
    	
    	public MovieFrame(int frameNumber, int intermediateStep) {
			this.frameNumber = frameNumber; 
			this.intermediateStep = intermediateStep;
    	}
    	
		public int getFrameNumber() {
			return frameNumber;
		}
		public int getIntermediateStep() {
			return intermediateStep;
		}
		
		public void setOrientation(Orientation orientation) {
			this.orientation = orientation;
		}

		public Orientation getOrientation() throws UninitializedException {
			if (orientation == null) throw new UninitializedException();
			return orientation;
		}

        @Override
        public int compareTo(MovieFrame other) {
            if (frameNumber < other.getFrameNumber()) {            	
                return -1;
            } else if (frameNumber == other.getFrameNumber()) {
            	if (intermediateStep < other.getIntermediateStep()) {
            		return -1;
            	} else if (intermediateStep == other.getIntermediateStep()) {
            		return 0;
            	} else {
            		return 1;
            	}
            } else {
            	return 1;
            }
        }
    }
    
    private ArrayList<KeyFrame> keyFrames;    
    private ArrayList<MovieFrame> allMovieFrames = new ArrayList<MovieFrame>();
    private int currentMovieFrame = 0;

    private final ImauSettings settings = ImauSettings.getInstance();

    private states currentState = states.UNOPENED;
    private int frameNumber;

    private final boolean running = true;
    private boolean initialized = false;

    private long startTime, stopTime;

    private final JSlider timeBar;
    private final JFormattedTextField frameCounter;

    private ImauInputHandler inputHandler;

    private DatasetManager dsManager;

    private boolean needsScreenshot = false;
    private String screenshotFilename = "";

    private final long waittime = settings.getWaittimeMovie();

    public TimedPlayer(CustomJSlider timeBar2, JFormattedTextField frameCounter) {
        this.timeBar = timeBar2;
        this.frameCounter = frameCounter;
        this.inputHandler = ImauInputHandler.getInstance();
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
        	int newFrameNumber = dsManager.getPreviousFrameNumber(frameNumber);
            updateFrame(newFrameNumber, false);
        } catch (IOException e) {
            logger.debug("One back failed.");
        }
    }

    public synchronized void oneForward() {
        stop();

        try {
        	int newFrameNumber = dsManager.getNextFrameNumber(frameNumber);
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
        int newFrameNumber = dsManager.getFirstFrameNumber();
        updateFrame(newFrameNumber, false);
    }

    public synchronized void setScreenshotNeeded(boolean value) {
        if (value) {
            final Float3Vector rotation = inputHandler.getRotation();
            final float viewDist = inputHandler.getViewDist();

            System.out.println("Simulation frame: " + frameNumber + ", Rotation x: " + rotation.getX() + " y: "
                    + rotation.getY() + " , viewDist: " + viewDist);

            screenshotFilename = settings.getScreenshotPath() + String.format("%05d", (currentMovieFrame)) + ".png";

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
        	//System.out.println(currentState);
        	try {        		
	        	if (currentState == states.MOVIEMAKING || currentState == states.REVIEW) {
	        		if (currentState == states.REVIEW) {
	        			if (currentMovieFrame < allMovieFrames.size()) {
		        			MovieFrame movieFrame = allMovieFrames.get(currentMovieFrame);
		            		int frameNumber = movieFrame.getFrameNumber();
		            		
		            		Orientation ori = movieFrame.getOrientation();
		            		inputHandler.setRotation(ori.getRotation());
		            		inputHandler.setViewDist(ori.getViewDist());
		            		
		            		//inputHandler.setRotation(new Float3Vector(inputHandler.getRotation().getX(), (inputHandler.getRotation().getY()+(360f/allMovieFrames.size())) %360f, inputHandler.getRotation().getZ()));
		            		//inputHandler.setViewDist(ori.getViewDist());
		            		updateFrame(frameNumber, true);
		            		currentMovieFrame++;
		            		
		            		try {
		                        Thread.sleep(10);
		                    } catch (final InterruptedException e) {
		                        System.err.println("Interrupted while stopped.");
		                    }	
	        			} else {
		            		stop();
		            	}
	        		} else {
		        		if (!isScreenshotNeeded()) {
			            	if (currentMovieFrame < allMovieFrames.size()) {
			            		MovieFrame movieFrame = allMovieFrames.get(currentMovieFrame);
			            		int frameNumber = movieFrame.getFrameNumber();
			            		
			            		Orientation ori = movieFrame.getOrientation();
			            		inputHandler.setRotation(ori.getRotation());
			            		inputHandler.setViewDist(ori.getViewDist());
			            		
			            		//inputHandler.setRotation(new Float3Vector(inputHandler.getRotation().getX(), (inputHandler.getRotation().getY()+(360f/allMovieFrames.size())) %360f, inputHandler.getRotation().getZ()));
			            		updateFrame(frameNumber, true);

			            		setScreenshotNeeded(true);
			                    
			                    currentMovieFrame++;
			            	} else {
			            		stop();
			            	} 
		            	} else {
		                    try {
		                        Thread.sleep(10);
		                    } catch (final InterruptedException e) {
		                        System.err.println("Interrupted while stopped.");
		                    }	            		
		            	}	
	        		}
	            }
        	} catch (UninitializedException e) {
                System.err.println("Interrupted while playing.");
            }
        	
        	
            if ((currentState == states.PLAYING) || (currentState == states.REDRAWING)) {
                try {
                    if (!isScreenshotNeeded()) {
                        startTime = System.currentTimeMillis();

                        if (currentState != states.REDRAWING) {
                        	int newFrameNumber;
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

    public synchronized void setFrame(int value, boolean overrideUpdate) {
        stop();

        updateFrame(value, overrideUpdate);
    }

    public synchronized void start() {
        currentState = states.PLAYING;
    }

    public synchronized void stop() {
        currentState = states.STOPPED;
    }

    private synchronized void updateFrame(int newFrameNumber, boolean overrideUpdate) {
        if (dsManager != null) {
            if (newFrameNumber != frameNumber || overrideUpdate) {
                if (!settings.isRequestedNewConfiguration()) {
                    frameNumber = newFrameNumber;
                    settings.setFrameNumber(newFrameNumber);
                    this.timeBar.setValue(newFrameNumber);
                    this.frameCounter.setValue(newFrameNumber);

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

    public synchronized String getVariableTime(String varName) throws DatasetNotFoundException {
        return dsManager.getVariableTime(varName, frameNumber);
    }

    public synchronized String getVariableDescription(String varName) throws DatasetNotFoundException {
        return dsManager.getVariableDescription(varName);
    }

    public synchronized float getMinValueContainedInDataset(String varName) throws DatasetNotFoundException {
        return dsManager.getMinValueContainedInDataset(varName);
    }

    public synchronized float getMaxValueContainedInDataset(String varName) throws DatasetNotFoundException {
        return dsManager.getMaxValueContainedInDataset(varName);
    }

    public int getInitialFrameNumber() {
        return dsManager.getFirstFrameNumber();
    }

    public synchronized void startSequence(boolean record) {
        // Only do interpolation step if we have a current AND a next keyFrame
        // available.        
        if (keyFrames.size() > 1) {        	
            for (int i = 0; i < keyFrames.size() - 1; i++) {
            	ArrayList<MovieFrame> newMovieFrames = new ArrayList<MovieFrame>();
            	
                KeyFrame currentKeyFrame = keyFrames.get(i);
                KeyFrame nextKeyFrame = keyFrames.get(i + 1);

                int startFrameNumber = currentKeyFrame.getFrameNumber();
                int stopFrameNumber = nextKeyFrame.getFrameNumber();

            	int currentFrameNumber = startFrameNumber;
                while (currentFrameNumber <= stopFrameNumber) {
                    for (int j = 0; j < settings.getNumberOfScreenshotsPerTimeStep(); j++) {
                    	newMovieFrames.add(new MovieFrame(currentFrameNumber, j));
                    }
                    try {
						currentFrameNumber = dsManager.getNextFrameNumber(currentFrameNumber);
					} catch (IndexOutOfBoundsException | IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
                }
                                
                Float3Vector startLocation = new Float3Vector(currentKeyFrame.getRotation().getX(), currentKeyFrame
                        .getRotation().getY(), 0f);
                Float3Vector endLocation = new Float3Vector(nextKeyFrame.getRotation().getX(), nextKeyFrame
                        .getRotation().getY(), 0f);

                Float3Vector still = new Float3Vector();

                //Float3Vector[] curveSteps = degreesBezierCurve(newMovieFrames.size(), startLocation, endLocation);
                
                Float3Vector[] curveSteps = cosineInterpolate(newMovieFrames.size(), startLocation, endLocation);

                // Patch for zoom
                Float4Vector startZoom = new Float4Vector(currentKeyFrame.getViewDist(), 0f, 0f, 1f);
                Float4Vector endZoom = new Float4Vector(nextKeyFrame.getViewDist(), 0f, 0f, 1f);

                Float4Vector[] zoomSteps = FloatVectorMath.bezierCurve(newMovieFrames.size(), startZoom, still,
                        still, endZoom);

                for (int j = 0; j < newMovieFrames.size(); j++) {                	
                    Orientation newOrientation = new Orientation(curveSteps[j], zoomSteps[j].getX());
                    newMovieFrames.get(j).setOrientation(newOrientation);
                }
                
                allMovieFrames.addAll(newMovieFrames);
            }
        }

        stop();
        
        if (allMovieFrames.size() > 0) {
        	Collections.sort(allMovieFrames);
        	
	        updateFrame(allMovieFrames.get(0).getFrameNumber(), true);
	        currentMovieFrame = 0;
	
	        if (record) {
	            movieMode();
	        } else {
	            reviewMode();
	        }
        }
    }

    /**
     * Bezier curve interpolation for _rotation_ between two points with control
     * vectors (this could be particle speed at the points). Outputs a number of
     * degrees for rotations.
     * 
     * @param steps
     *            The number of steps on the bezier curve to calculate.
     * @param startLocation
     *            The starting point for this bezier curve.
     * @param startControl
     *            The starting point's control vector.
     * @param endControl
     *            The end point for this bezier curve.
     * @param endLocation
     *            The end point's control vector.
     * @return The array of points on the new bezier curve.
     */
    public static Float3Vector[] degreesBezierCurve(int steps, Float3Vector startLocation, Float3Vector endLocation) {
        Float3Vector[] newBezierPoints = new Float3Vector[steps];
        for (int i = 0; i < steps; i++) {
            newBezierPoints[i] = new Float3Vector();
        }

        float t = 1f / steps;
        float temp = t * t;
        
//        float sx = startLocation.getX();
//        float sy = startLocation.getY();
//        float sz = startLocation.getZ();
//        float ex = endLocation.getX();
//        float ey = endLocation.getY();
//        float ez = endLocation.getZ();
//        if (sx < 360f) startLocation.setX(sx+360f);
//        if (sy < 360f) startLocation.setY(sy+360f);
//        if (sz < 360f) startLocation.setZ(sz+360f);
//        if (ex < 360f) endLocation.setX(ex+360f);
//        if (ey < 360f) endLocation.setY(ey+360f);
//        if (ez < 360f) endLocation.setZ(ez+360f);        

        for (int coord = 0; coord < 3; coord++) {
            float p[] = new float[4];
            if (coord == 0) {
        		p[0] = startLocation.getX();
        		p[1] = startLocation.getX();
        		p[2] = endLocation.getX();
        		p[3] = endLocation.getX();
            } else if (coord == 1) {            	
                p[0] = startLocation.getY();
                p[1] = startLocation.getY();
                p[2] = endLocation.getY();
                p[3] = endLocation.getY();            	
            } else if (coord == 2) {
                p[0] = startLocation.getZ();
                p[1] = startLocation.getZ();
                p[2] = endLocation.getZ();
                p[3] = endLocation.getZ();
            }
 
            // The algorithm itself begins here ==
            float f, fd, fdd, fddd, fdd_per_2, fddd_per_2, fddd_per_6; // NOSONAR

            // I've tried to optimize the amount of
            // multiplications here, but these are exactly
            // the same formulas that were derived earlier
            // for f(0), f'(0)*t etc.
            f = p[0];
            fd = 3f * (p[1] - p[0]) * t;
            fdd_per_2 = 3f * (p[0] - 2f * p[1] + p[2]) * temp;
            fddd_per_2 = 3f * (3f * (p[1] - p[2]) + p[3] - p[0]) * temp * t;

            fddd = fddd_per_2 + fddd_per_2;
            fdd = fdd_per_2 + fdd_per_2;
            fddd_per_6 = fddd_per_2 * (1f / 3f);

            for (int loop = 0; loop < steps; loop++) {
                if (coord == 0) {
                    newBezierPoints[loop].setX(f % 360f);
                } else if (coord == 1) {
                    newBezierPoints[loop].setY(f % 360f);
                } else if (coord == 2) {
                    newBezierPoints[loop].setZ(f % 360f);
                }

                f = f + fd + fdd_per_2 + fddd_per_6;
                fd = fd + fdd + fddd_per_2;
                fdd = fdd + fddd;
                fdd_per_2 = fdd_per_2 + fddd_per_2;
            }
        }

        return newBezierPoints;
    }
    
    public static Float3Vector[] cosineInterpolate(int steps, Float3Vector startLocation, Float3Vector endLocation) {
		Float3Vector[] result = new Float3Vector[steps];
		
		double sx = startLocation.getX();
		double sy = startLocation.getY();
		double ex = endLocation.getX();
		double ey = endLocation.getY();
		
		for (int i = 0; i < steps; i++) {
			double mu = (double) i / (double)steps;
			double mu2 = (1.0-Math.cos(mu*Math.PI))/2.0;
			
			double travelDestination;
			if (mymodulo(ex - sx, 360.0) < mymodulo(sx - ex, 360.0)) {
				travelDestination = mymodulo(ex - sx, 360.0);
			} else {
				travelDestination = -mymodulo(sx - ex, 360.0);
			}
					
			double rx = mymodulo((sx*(1-mu2)+(sx+travelDestination)*mu2), 360.0);

			if (mymodulo(ey - sy, 360.0) < mymodulo(sy - ey, 360.0)) {
				travelDestination = mymodulo(ey - sy, 360.0);
			} else {
				travelDestination = -mymodulo(sy - ey, 360.0);
			}
			
			double ry = mymodulo((sy*(1-mu2)+(sy+travelDestination)*mu2), 360.0);
								
			result[i] = new Float3Vector((float)rx, (float)ry, 0f);
		}
				
    	return result;
    }
    
    private static double mymodulo(double in, double mod) {
    	double result = in;
    	
    	if (in > 0) {
    		double temp = result;
	    	while (temp > 0) {
	    		temp = temp - mod;
	    		if (temp > 0) {
	    			result = temp;
	    		}
	    	}
    	} else if (in < 0) {
    		double temp = result;    	
	    	while (temp < 0) {
	    		temp = temp + mod;
	    		if (temp > 0) { 
	    			result = temp;
	    		}
	    	}
    	}
    	
    	return result;
    }

    public synchronized void reviewMode() {
        currentState = states.REVIEW;
    }

	public void setKeyFrames(ArrayList<KeyFrame> keyFrames) {
		this.keyFrames = keyFrames;
		allMovieFrames.clear();
	}
}
