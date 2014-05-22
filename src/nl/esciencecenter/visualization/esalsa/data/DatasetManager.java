package nl.esciencecenter.visualization.esalsa.data;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.media.opengl.GL3;

import nl.esciencecenter.neon.swing.ColormapInterpreter.Dimensions;
import nl.esciencecenter.visualization.esalsa.ImauSettings;
import nl.esciencecenter.visualization.esalsa.JOCLColormapper;
import nl.esciencecenter.visualization.esalsa.data.reworked.NCDFDataSet;
import nl.esciencecenter.visualization.esalsa.data.reworked.NCDFVariable;
import nl.esciencecenter.visualization.esalsa.data.reworked.NoSuchSequenceNumberException;
import nl.esciencecenter.visualization.esalsa.data.reworked.VariableNotCompatibleException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ucar.ma2.InvalidRangeException;

public class DatasetManager {
    private final static Logger logger = LoggerFactory.getLogger(DatasetManager.class);
    private final ImauSettings settings = ImauSettings.getInstance();

    private NCDFDataSet dataset;
    private final ExecutorService executor;
    private final List<Long> masterTimeList;

    private final LinkedList<CachedData> cachedData;

    private class CachedData {
        private final String varname;
        private final long frameNumber;
        private final int depth;

        private float[] data;

        public CachedData(SurfaceTextureDescription desc, float[] data) {
            varname = desc.varName;
            frameNumber = desc.frameNumber;
            depth = desc.depth;

            data = data;
        }

        public synchronized String getVarname() {
            return varname;
        }

        public synchronized long getFrameNumber() {
            return frameNumber;
        }

        public synchronized int getDepth() {
            return depth;
        }

        public synchronized float[] getData() {
            return data;
        }

        @Override
        public int hashCode() {
            int variablePrime = (varname.hashCode() + 67) * 859;
            int frameNumberPrime = (int) ((frameNumber + 131) * 1543);
            int depthPrime = (depth + 251) * 2957;

            int hashCode = frameNumberPrime + depthPrime + variablePrime;

            return hashCode;
        }

        @Override
        public boolean equals(Object thatObject) {
            if (this == thatObject)
                return true;
            if (!(thatObject instanceof CachedData))
                return false;

            // cast to native object is now safe
            CachedData that = (CachedData) thatObject;

            // now a proper field-by-field evaluation can be made
            return (varname.compareTo(that.varname) == 0 && frameNumber == that.frameNumber && depth == that.depth);
        }
    }

    private class TexturedataStorage {
        private final int width;
        private final int height;
        private final TextureStorage texStorage;

        public TexturedataStorage(DatasetManager manager, int width, int height) {
            this.width = width;
            this.height = height;
            texStorage = new TextureStorage(manager, settings.getNumScreensRows() * settings.getNumScreensCols(),
                    width, height, GL3.GL_TEXTURE4, GL3.GL_TEXTURE5);
        }

        public synchronized int getWidth() {
            return width;
        }

        public synchronized int getHeight() {
            return height;
        }

        public synchronized TextureStorage getTexStorage() {
            return texStorage;
        }
    }

    private final JOCLColormapper mapper;
    private final List<TexturedataStorage> textureDatastorageList;

    private class Worker implements Runnable {
        private final SurfaceTextureDescription desc;

        public Worker(SurfaceTextureDescription desc) {
            this.desc = desc;
        }

        @Override
        public void run() {
            String varName = desc.getVarName();
            NCDFVariable ncdfVar = dataset.getVariable(varName);

            Dimensions colormapDims = new Dimensions(settings.getCurrentVarMin(varName),
                    settings.getCurrentVarMax(varName));

            float[] surfaceArray = getDataCached(desc, ncdfVar);

            int[] pixelArray = mapper.makeImage(desc.getColorMap(), colormapDims, surfaceArray, ncdfVar.getFillValue(),
                    desc.isLogScale(), ncdfVar.getLonDimensionSize(), ncdfVar.getLatDimensionSize());

            ByteBuffer legendBuf = mapper.getColormapForLegendTexture(desc.getColorMap());

            for (TexturedataStorage tds : textureDatastorageList) {
                if (tds.getWidth() == ncdfVar.getLonDimensionSize() && tds.getHeight() == ncdfVar.getLatDimensionSize()) {
                    tds.getTexStorage().setImageCombo(desc, pixelArray, legendBuf);
                }
            }
        }

        private float[] getDataCached(SurfaceTextureDescription desc, NCDFVariable ncdfVar) {
            float[] result = null;
            if (cachedData.contains(desc)) {
                return cachedData.get(cachedData.indexOf(desc)).getData();
            } else {
                long frameNumber = desc.getFrameNumber();
                int requestedDepth = desc.getDepth();
                try {
                    result = ncdfVar.getData(frameNumber, requestedDepth);
                    cachedData.addLast(new CachedData(desc, result));
                    while (cachedData.size() > (settings.getNumScreensCols() * settings.getNumScreensRows())) {
                        cachedData.removeFirst();
                    }
                    return result;
                } catch (NoSuchSequenceNumberException | InvalidRangeException | IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

            return result;
        }

    }

    public DatasetManager(File[] files) {
        cachedData = new LinkedList<CachedData>();
        executor = Executors.newFixedThreadPool(4);

        masterTimeList = new ArrayList<Long>();
        textureDatastorageList = new ArrayList<TexturedataStorage>();

        List<List<File>> filesets = new ArrayList<List<File>>();
        List<File> firstFileset = new ArrayList<File>();
        firstFileset.add(files[0]);

        for (int i = 0; i < files.length; i++) {
            for (List<File> currentFileset : filesets) {
                // extract all NON_NUMBER parts from the filename and compare
                // them
                // to see if this is the same dataset
                String[] stringsRef = currentFileset.get(0).getName().split("(?=[0-9])([0-9]*)");
                String[] strings1 = files[i].getName().split("(?=[0-9])([0-9]*)");
                boolean same = true;
                for (int j = 0; j < strings1.length; j++) {
                    if (strings1[j].compareTo(stringsRef[j]) == 0) {
                        same = false;
                    }
                }

                if (same) {
                    currentFileset.add(files[i]);
                }
            }

        }
        // String[] strings2 = files[i].getName().split("(?=[^0-9])([^0-9]*)");
        // for (int j = 0; j < strings2.length; j++) {
        // System.out.println(strings2[j]);
        // }
        //
        // for (int j = 0; j < strings1.length; j++) {
        // System.out.print(strings2[j] + strings1[j]);
        // }

        try {
            logger.debug("Now opening dataset");
            dataset = new NCDFDataSet(firstFileset);

            for (String varName : dataset.getVariableNames()) {
                NCDFVariable ncdfVar = dataset.getVariable(varName);
                int varWidth = ncdfVar.getLonDimensionSize();
                int varHeight = ncdfVar.getLatDimensionSize();

                boolean tdsFound = false;
                for (TexturedataStorage tds : textureDatastorageList) {
                    if (tds.getWidth() == varWidth && tds.getHeight() == varHeight) {
                        tdsFound = true;
                    }
                }
                if (!tdsFound) {
                    textureDatastorageList.add(new TexturedataStorage(this, varWidth, varHeight));
                }

                List<Long> times = ncdfVar.getSequenceNumbers();
                for (long time : times) {
                    if (!masterTimeList.contains(time)) {
                        masterTimeList.add(time);
                    }
                }
                Collections.sort(masterTimeList);
            }
        } catch (IOException | VariableNotCompatibleException e) {
            e.printStackTrace();
        }

        mapper = new JOCLColormapper();
    }

    public synchronized void shutdown() {
        executor.shutdown();

        while (!executor.isTerminated()) {
        }
    }

    public synchronized void buildImages(SurfaceTextureDescription desc) {
        Runnable worker = new Worker(desc);
        executor.execute(worker);
    }

    public synchronized TextureStorage getTextureStorage(String varName) {
        NCDFVariable ncdfVar = dataset.getVariable(varName);
        for (TexturedataStorage tds : textureDatastorageList) {
            if (tds.getWidth() == ncdfVar.getLonDimensionSize() && tds.getHeight() == ncdfVar.getLatDimensionSize()) {
                return tds.getTexStorage();
            }
        }

        return null;
    }

    public synchronized long getPreviousFrameNumber(long frameNumber) throws IOException {
        if (masterTimeList.contains(frameNumber)) {
            int indexOfFrameNumber = masterTimeList.indexOf(frameNumber);

            try {
                return masterTimeList.get(indexOfFrameNumber - 1);
            } catch (ArrayIndexOutOfBoundsException e) {
                return frameNumber;
            }
        } else {
            throw new IOException("Given frame number not valid: " + frameNumber);
        }
    }

    public synchronized long getNextFrameNumber(long frameNumber) throws IOException {
        if (masterTimeList.contains(frameNumber)) {
            int indexOfFrameNumber = masterTimeList.indexOf(frameNumber);

            try {
                return masterTimeList.get(indexOfFrameNumber + 1);
            } catch (ArrayIndexOutOfBoundsException e) {
                return frameNumber;
            }
        } else {
            throw new IOException("Given frame number not valid: " + frameNumber);
        }
    }

    public synchronized int getNumFrames() {
        return masterTimeList.size();
    }

    public synchronized List<String> getVariables() {
        return dataset.getVariableNames();
    }

    public synchronized String getVariableUnits(String varName) {
        NCDFVariable ncdfVar = dataset.getVariable(varName);
        return ncdfVar.getUnits();
    }

    public synchronized float getMinValueContainedInDataset(String varName) {
        NCDFVariable ncdfVar = dataset.getVariable(varName);
        return ncdfVar.getMinimumValue();
    }

    public synchronized float getMaxValueContainedInDataset(String varName) {
        NCDFVariable ncdfVar = dataset.getVariable(varName);
        return ncdfVar.getMaximumValue();
    }

    public long getFirstFrameNumber() {
        return masterTimeList.get(0);
    }

    public int getIndexOfFrameNumber(long frameNumber) {
        if (masterTimeList.contains(frameNumber)) {
            return masterTimeList.indexOf(frameNumber);
        }
        return 0;
    }
}
