package nl.esciencecenter.visualization.esalsa.data;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import javax.media.opengl.GL3;

import nl.esciencecenter.neon.swing.ColormapInterpreter;
import nl.esciencecenter.neon.swing.ColormapInterpreter.Color;
import nl.esciencecenter.neon.swing.ColormapInterpreter.Dimensions;
import nl.esciencecenter.visualization.esalsa.ImauSettings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatasetManager {
    private final static Logger logger = LoggerFactory.getLogger(DatasetManager.class);
    private final ImauSettings settings = ImauSettings.getInstance();

    private ArrayList<String> variables;
    private ArrayList<NetCDFReader> readers;
    private ArrayList<Integer> availableFrameSequenceNumbers;
    private EfficientTextureStorage effTexStorage;

    private int latArraySize;
    private int lonArraySize;

    public DatasetManager(File[] files) {
        init(files);
    }

    private void init(File[] files) {
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

            if (frames == 0) {
                frames = ncr.getAvailableFrames();
            }

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

            if (frames != ncr.getAvailableFrames()) {
                logger.debug("NUMBER OF FRAMES NOT EQUAL");
                accept = false;
            }

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

        effTexStorage = new EfficientTextureStorage(this, settings.getNumScreensRows() * settings.getNumScreensCols(),
                lonArraySize, latArraySize, GL3.GL_TEXTURE4, GL3.GL_TEXTURE5);

    }

    public void buildImages(SurfaceTextureDescription desc) {
        int frameNumber = desc.getFrameNumber();
        String varName = desc.getVarName();

        int frameIndex = getIndexOfFrameNumber(frameNumber);

        NetCDFReader currentReader = readers.get(frameIndex);

        ByteBuffer surfaceBuffer = currentReader.getImage(desc.getColorMap(), varName, desc.getDepth(),
                desc.isLogScale());
        effTexStorage.setSurfaceImage(desc, surfaceBuffer);

        Dimensions colormapDims = new Dimensions(settings.getCurrentVarMin(varName), settings.getCurrentVarMax(varName));

        int height = 500;
        int width = 1;
        ByteBuffer outBuf = ByteBuffer.allocate(height * width * 4);

        for (int row = height - 1; row >= 0; row--) {
            float index = row / (float) height;
            float var = (index * colormapDims.getDiff()) + colormapDims.getMin();

            Color c = ColormapInterpreter.getColor(desc.getColorMap(), colormapDims, var);

            for (int col = 0; col < width; col++) {
                outBuf.put((byte) (255 * c.getRed()));
                outBuf.put((byte) (255 * c.getGreen()));
                outBuf.put((byte) (255 * c.getBlue()));
                outBuf.put((byte) 0);
            }
        }

        outBuf.flip();

        effTexStorage.setLegendImage(desc, outBuf);
    }

    public EfficientTextureStorage getEfficientTextureStorage() {
        return effTexStorage;
    }

    public int getFrameNumberOfIndex(int index) {
        return availableFrameSequenceNumbers.get(index);
    }

    public int getIndexOfFrameNumber(int frameNumber) {
        return availableFrameSequenceNumbers.indexOf(frameNumber);
    }

    public int getPreviousFrameNumber(int frameNumber) throws IOException {
        int nextNumber = getIndexOfFrameNumber(frameNumber) - 1;

        if (nextNumber >= 0 && nextNumber < availableFrameSequenceNumbers.size()) {
            return getFrameNumberOfIndex(nextNumber);
        } else {
            throw new IOException("Frame number not available: " + nextNumber);
        }
    }

    public int getNextFrameNumber(int frameNumber) throws IOException {
        int nextNumber = getIndexOfFrameNumber(frameNumber) + 1;

        if (nextNumber >= 0 && nextNumber < availableFrameSequenceNumbers.size()) {
            return getFrameNumberOfIndex(nextNumber);
        } else {
            throw new IOException("Frame number not available: " + nextNumber);
        }
    }

    public int getNumFrames() {
        return availableFrameSequenceNumbers.size();
    }

    public ArrayList<String> getVariables() {
        return variables;
    }

    public String getVariableUnits(String varName) {
        return readers.get(0).getUnits(varName);
    }

    public int getImageWidth() {
        return lonArraySize;
    }

    public int getImageHeight() {
        return latArraySize;
    }

    public float getMinValueContainedInDataset(String varName) {
        float min = Float.MAX_VALUE;
        for (NetCDFReader reader : readers) {
            float readerMin = reader.getMinValue(varName);
            if (readerMin < min) {
                min = readerMin;
            }
        }
        return min;
    }

    public float getMaxValueContainedInDataset(String varName) {
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
