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

    private final List<NCDFDataSet> datasets;
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

            try {
                NCDFDataSet dataset = findDataset(varName);
                NCDFVariable ncdfVar = dataset.getVariable(varName);

                Dimensions colormapDims = new Dimensions(settings.getCurrentVarMin(varName),
                        settings.getCurrentVarMax(varName));

                float[] surfaceArray = getDataCached(desc, ncdfVar);

                int[] pixelArray = mapper.makeImage(desc.getColorMap(), colormapDims, surfaceArray,
                        ncdfVar.getFillValue(), desc.isLogScale(), ncdfVar.getLonDimensionSize(),
                        ncdfVar.getLatDimensionSize());

                ByteBuffer legendBuf = mapper.getColormapForLegendTexture(desc.getColorMap());

                for (TexturedataStorage tds : textureDatastorageList) {
                    if (tds.getWidth() == ncdfVar.getLonDimensionSize()
                            && tds.getHeight() == ncdfVar.getLatDimensionSize()) {
                        float topTexCoord = 0.0f + (ncdfVar.getMaxLatitude() / 90f);
                        float bottomTexCoord = 1.0f - (ncdfVar.getMinLatitude() / -90f);
                        // System.out.println("topTexCoord: " + topTexCoord);
                        // System.out.println("bottomTexCoord: " +
                        // bottomTexCoord);
                        tds.getTexStorage().setImageCombo(desc, pixelArray, legendBuf, topTexCoord, bottomTexCoord);
                    }
                }
            } catch (DatasetNotFoundException e) {
                e.printStackTrace();
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
        filesets.add(firstFileset);

        // start comparing from the second file onwards.
        for (int i = 1; i < files.length; i++) {
            boolean fileSetFound = false;
            for (List<File> currentFileset : filesets) {
                // extract all NON_NUMBER parts from the filename and compare
                // them to see if this is the same dataset
                String[] stringsRef = currentFileset.get(0).getName().split("(?=[0-9])([0-9]*)");
                String[] strings1 = files[i].getName().split("(?=[0-9])([0-9]*)");
                boolean sameExceptForNumbers = true;
                for (int j = 0; j < strings1.length; j++) {
                    if (strings1[j].compareTo(stringsRef[j]) != 0) {
                        sameExceptForNumbers = false;
                    }
                }

                if (sameExceptForNumbers) {
                    logger.debug("Adding " + files[i].getName() + " to fileset: " + currentFileset.get(0).getName());
                    currentFileset.add(files[i]);
                    fileSetFound = true;
                }
            }
            if (!fileSetFound) {
                logger.info("New fileset found: " + files[i].getName());
                List<File> secondaryFileset = new ArrayList<File>();
                secondaryFileset.add(files[i]);
                filesets.add(secondaryFileset);
            }
        }

        datasets = new ArrayList<NCDFDataSet>();

        for (List<File> currentFileset : filesets) {
            try {
                logger.debug("Now opening dataset");
                NCDFDataSet currentDataset = new NCDFDataSet(currentFileset);
                datasets.add(currentDataset);

                for (String varName : currentDataset.getVariableNames()) {
                    NCDFVariable ncdfVar = currentDataset.getVariable(varName);
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
        }

        mapper = new JOCLColormapper();
    }

    public synchronized void shutdown() {
        mapper.dispose();
        executor.shutdown();

        while (!executor.isTerminated()) {
        }
    }

    public synchronized void buildImages(SurfaceTextureDescription desc) {
        Runnable worker = new Worker(desc);
        executor.execute(worker);
    }

    public synchronized TextureStorage getTextureStorage(String varName) throws DatasetNotFoundException {
        NCDFDataSet dataset = findDataset(varName);

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

    public synchronized long getNextFrameNumber(long frameNumber) throws IndexOutOfBoundsException, IOException {
        if (masterTimeList.contains(frameNumber)) {
            int indexOfFrameNumber = masterTimeList.indexOf(frameNumber);
            return masterTimeList.get(indexOfFrameNumber + 1);

        } else {
            throw new IOException("Given frame number not valid: " + frameNumber);
        }
    }

    public synchronized int getNumFrames() {
        return masterTimeList.size();
    }

    public synchronized List<String> getVariables() {
        List<String> varNamesFromAllDatasets = new ArrayList<String>();
        for (NCDFDataSet ds : datasets) {
            varNamesFromAllDatasets.addAll(ds.getVariableNames());
        }
        return varNamesFromAllDatasets;
    }

    public synchronized String getVariableUnits(String varName) throws DatasetNotFoundException {
        NCDFDataSet dataset = findDataset(varName);
        NCDFVariable ncdfVar = dataset.getVariable(varName);
        return ncdfVar.getUnits();
    }

    public synchronized String getVariableTime(String varName,long frameNumber) throws DatasetNotFoundException {
        NCDFDataSet dataset = findDataset(varName);
        NCDFVariable ncdfVar = dataset.getVariable(varName);
        return ncdfVar.getTime(frameNumber);
    }

	public String getVariableDescription(String varName) throws DatasetNotFoundException {
        NCDFDataSet dataset = findDataset(varName);
        NCDFVariable ncdfVar = dataset.getVariable(varName);
        return ncdfVar.getDescription();
	}

    public synchronized float getMinValueContainedInDataset(String varName) throws DatasetNotFoundException {
        NCDFDataSet dataset = findDataset(varName);
        NCDFVariable ncdfVar = dataset.getVariable(varName);
        return ncdfVar.getMinimumValue();
    }

    public synchronized float getMaxValueContainedInDataset(String varName) throws DatasetNotFoundException {
        NCDFDataSet dataset = findDataset(varName);
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

    private NCDFDataSet findDataset(String varName) throws DatasetNotFoundException {
        NCDFDataSet datasetThatContainsTheVariable = null;
        for (NCDFDataSet ds : datasets) {
            if (ds.getVariableNames().contains(varName)) {
                datasetThatContainsTheVariable = ds;
            }
        }
        if (datasetThatContainsTheVariable == null) {
            throw new DatasetNotFoundException("What did you do? " + varName
                    + " doesn't exist? This should not have happened.");
        }
        return datasetThatContainsTheVariable;
    }
}
