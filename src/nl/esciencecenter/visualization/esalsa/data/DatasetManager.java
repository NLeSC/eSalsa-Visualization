package nl.esciencecenter.visualization.esalsa.data;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.media.opengl.GL3;

import nl.esciencecenter.neon.swing.ColormapInterpreter.Dimensions;
import nl.esciencecenter.visualization.esalsa.ImauSettings;
import nl.esciencecenter.visualization.esalsa.JOCLColormapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatasetManager {
    private final static Logger logger = LoggerFactory.getLogger(DatasetManager.class);
    private final ImauSettings settings = ImauSettings.getInstance();

    private ArrayList<String> variables;
    private ArrayList<NetCDFReader> readers;
    private ArrayList<Integer> availableFrameSequenceNumbers;
    private TextureStorage texStorage;

    private int latArraySize;
    private int lonArraySize;

    private final ExecutorService executor;
    private final JOCLColormapper mapper;

    private class Worker implements Runnable {
        private final SurfaceTextureDescription desc;

        public Worker(SurfaceTextureDescription desc) {
            this.desc = desc;
        }

        @Override
        public void run() {
            int frameNumber = desc.getFrameNumber();
            String varName = desc.getVarName();
            int requestedDepth = desc.getDepth();

            int frameIndex = getIndexOfFrameNumber(frameNumber);
            NetCDFReader currentReader = readers.get(frameIndex);

            Dimensions colormapDims = new Dimensions(settings.getCurrentVarMin(varName),
                    settings.getCurrentVarMax(varName));
            float[] surfaceArray = null;
            while (surfaceArray == null) {
                surfaceArray = currentReader.getData(varName, requestedDepth);
            }

            int[] pixelArray = mapper.makeImage(desc.getColorMap(), colormapDims, surfaceArray,
                    currentReader.getFillValue(varName), desc.isLogScale());

            ByteBuffer legendBuf = mapper.getColormapForLegendTexture(desc.getColorMap());

            texStorage.setImageCombo(desc, pixelArray, legendBuf);
        }

    }

    public DatasetManager(File[] files) {
        executor = Executors.newFixedThreadPool(4);

        init(files);

        mapper = new JOCLColormapper(getImageWidth(), getImageHeight());
    }

    public synchronized void shutdown() {
        executor.shutdown();

        while (!executor.isTerminated()) {
        }
    }

    private synchronized void init(File[] files) {
        variables = new ArrayList<String>();
        readers = new ArrayList<NetCDFReader>();
        availableFrameSequenceNumbers = new ArrayList<Integer>();

        latArraySize = 0;
        lonArraySize = 0;
        int frames = 0;

        for (File file : files) {
            boolean accept = true;
            NetCDFReader ncr = new NetCDFReader(file);

            // If this is the first file, use it to set the standard
            if (latArraySize == 0) {
                latArraySize = ncr.getLatSize();
            }

            if (lonArraySize == 0) {
                lonArraySize = ncr.getLonSize();
            }

            // if (frames == 0) {
            // frames = ncr.getAvailableFrames();
            // }

            if (variables.size() == 0) {
                variables = ncr.getVariableNames();
            }

            // If it is a subsequent file, check if it adheres to the standards
            // set by the first file.
            if (latArraySize != ncr.getLatSize()) {
                logger.debug("LAT ARRAY SIZES NOT EQUAL");
                accept = false;
            }

            if (lonArraySize != ncr.getLonSize()) {
                logger.debug("LON ARRAY SIZES NOT EQUAL");
                accept = false;
            }

            boolean stillGood = true;
            ArrayList<String> varNames = ncr.getVariableNames();
            for (String varName : varNames) {
                if (!variables.contains(varName)) {
                    stillGood = false;
                }
            }
            for (String varName : variables) {
                if (!varNames.contains(varName)) {
                    stillGood = false;
                }
            }
            if (!stillGood) {
                logger.debug("VARIABLES NOT EQUAL");
                accept = false;
            }

            // if (frames != ncr.getAvailableFrames()) {
            // logger.debug("NUMBER OF FRAMES NOT EQUAL");
            // accept = false;
            // }

            // If it does adhere to the standard, add the variables to the
            // datastore and associate them with the netcdf readers they came
            // from.
            if (accept) {
                availableFrameSequenceNumbers.add(ncr.getThisParticularFrameNumber());
                readers.add(ncr);
                for (String varName : varNames) {
                    // And determine the bounds
                    ncr.determineMinMax(varName);
                }
            }
        }

        texStorage = new TextureStorage(this, settings.getNumScreensRows() * settings.getNumScreensCols(),
                lonArraySize, latArraySize, GL3.GL_TEXTURE4, GL3.GL_TEXTURE5);

    }

    public synchronized void buildImages(SurfaceTextureDescription desc) {
        Runnable worker = new Worker(desc);
        executor.execute(worker);
    }

    public synchronized TextureStorage getEfficientTextureStorage() {
        return texStorage;
    }

    public synchronized int getFrameNumberOfIndex(int index) {
        return availableFrameSequenceNumbers.get(index);
    }

    public synchronized int getIndexOfFrameNumber(int frameNumber) {
        return availableFrameSequenceNumbers.indexOf(frameNumber);
    }

    public synchronized int getPreviousFrameNumber(int frameNumber) throws IOException {
        int nextNumber = getIndexOfFrameNumber(frameNumber) - 1;

        if (nextNumber >= 0 && nextNumber < availableFrameSequenceNumbers.size()) {
            return getFrameNumberOfIndex(nextNumber);
        } else {
            throw new IOException("Frame number not available: " + nextNumber);
        }
    }

    public synchronized int getNextFrameNumber(int frameNumber) throws IOException {
        int nextNumber = getIndexOfFrameNumber(frameNumber) + 1;

        if (nextNumber >= 0 && nextNumber < availableFrameSequenceNumbers.size()) {
            return getFrameNumberOfIndex(nextNumber);
        } else {
            throw new IOException("Frame number not available: " + nextNumber);
        }
    }

    public synchronized int getNumFrames() {
        return availableFrameSequenceNumbers.size();
    }

    public synchronized ArrayList<String> getVariables() {
        return variables;
    }

    public synchronized String getVariableUnits(String varName) {
        return readers.get(0).getUnits(varName);
    }

    public synchronized int getImageWidth() {
        return lonArraySize;
    }

    public synchronized int getImageHeight() {
        return latArraySize;
    }

    public synchronized float getMinValueContainedInDataset(String varName) {
        float min = Float.MAX_VALUE;
        for (NetCDFReader reader : readers) {
            float readerMin = reader.getMinValue(varName);
            if (readerMin < min) {
                min = readerMin;
            }
        }
        return min;
    }

    public synchronized float getMaxValueContainedInDataset(String varName) {
        float max = Float.MIN_VALUE;
        for (NetCDFReader reader : readers) {
            float readerMax = reader.getMaxValue(varName);
            if (readerMax > max) {
                max = readerMax;
            }
        }
        return max;
    }
}
