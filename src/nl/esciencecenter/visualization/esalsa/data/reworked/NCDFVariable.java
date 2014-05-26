package nl.esciencecenter.visualization.esalsa.data.reworked;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import nl.esciencecenter.visualization.esalsa.CacheFileManager;
import nl.esciencecenter.visualization.esalsa.ImauSettings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ucar.ma2.Array;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

public class NCDFVariable {
    private final static Logger logger = LoggerFactory.getLogger(NCDFVariable.class);
    private final static int MAX_TIMESTEPS_IN_SINGLE_FILE = 10000;
    private final ImauSettings settings = ImauSettings.getInstance();

    private class TimeStep implements Comparable<TimeStep> {
        File file;
        int fileSequenceNumber;
        int timeStepWithinFile;
        boolean hasTimesteps;

        public TimeStep(File file, int fileSequenceNumber, int timeStepWithinFile, boolean hasTimesteps) {
            this.file = file;
            this.fileSequenceNumber = fileSequenceNumber;
            this.timeStepWithinFile = timeStepWithinFile;
            this.hasTimesteps = hasTimesteps;
        }

        public File getFile() {
            return file;
        }

        public int getFileSequenceNumber() {
            return fileSequenceNumber;
        }

        public int getTimeStepWithinFile() {
            return timeStepWithinFile;
        }

        @Override
        public int compareTo(TimeStep other) {
            if (fileSequenceNumber < other.getFileSequenceNumber()) {
                return -1;
            } else if (fileSequenceNumber > other.getFileSequenceNumber()) {
                return 1;
            } else {
                if (timeStepWithinFile < other.getTimeStepWithinFile()) {
                    return -1;
                } else if (timeStepWithinFile < other.getTimeStepWithinFile()) {
                    return 1;
                }
            }

            return 0;
        }

        public boolean hasTimesteps() {
            return hasTimesteps;
        }
    }

    private final Variable variable;
    private final List<TimeStep> timeSteps;

    private int heightDimensionSize = 0;
    private int latDimensionSize = 0;
    private int lonDimensionSize = 0;

    private float[] realLatitudeValues, realLongitudeValues;

    private float minimumValue, maximumValue, fillValue, minimumLatitude, maximumLatitude;

    private final CacheFileManager cacheAtDataLocation;
    private final CacheFileManager cacheAtProgramLocation;

    public NCDFVariable(Variable variable, List<File> filesToBeAnalysed) throws VariableNotCompatibleException,
            IOException {
        cacheAtDataLocation = settings.getCacheFileManagerAtDataLocation();
        cacheAtProgramLocation = settings.getCacheFileManagerAtProgramLocation();

        timeSteps = new ArrayList<TimeStep>();
        this.variable = variable;

        // Loop over the files to see if the variable is in there
        int sequenceNumber = 0;
        for (File file : filesToBeAnalysed) {
            NetcdfFile ncfile = NetcdfFile.open(file.getAbsolutePath());

            Variable variableInThisFile = ncfile.findVariable(variable.getFullName());
            if (variableInThisFile != null) {
                // seems to be in there, lets analyse the dimensions and see if
                // they match our previously found dimensions
                for (Dimension d : variableInThisFile.getDimensions()) {
                    if (d.getFullName().contains("depth") || d.getFullName().contains("z_t")
                            || d.getFullName().contains("lev")) {
                        int currentHeightDimensionSize = d.getLength();
                        if (heightDimensionSize != currentHeightDimensionSize) {
                            if (heightDimensionSize == 0) {
                                heightDimensionSize = currentHeightDimensionSize;
                            } else {
                                throw new VariableNotCompatibleException("Variable " + variable.getFullName()
                                        + " was found with mismatching dimensions");
                            }
                        }
                    }

                    if (d.getFullName().contains("lat") || d.getFullName().contains("nj")) {
                        int currentlatDimensionSize = d.getLength();
                        if (latDimensionSize != currentlatDimensionSize) {
                            if (latDimensionSize == 0) {
                                latDimensionSize = currentlatDimensionSize;
                            } else {
                                throw new VariableNotCompatibleException("Variable " + variable.getFullName()
                                        + " was found with mismatching dimensions");
                            }
                        }
                    }

                    if (d.getFullName().contains("lon") || d.getFullName().contains("ni")) {
                        int currentlonDimensionSize = d.getLength();
                        if (lonDimensionSize != currentlonDimensionSize) {
                            if (lonDimensionSize == 0) {
                                lonDimensionSize = currentlonDimensionSize;
                                // Variable realLongitudes =
                                // ncfile.findVariable(d.getFullName());
                                // if (realLongitudes != null) {
                                // Array netCDFArray = realLongitudes.read();
                                // realLongitudeValues = (float[])
                                // netCDFArray.get1DJavaArray(float.class);
                                //
                                // float min = Float.POSITIVE_INFINITY;
                                // float max = Float.NEGATIVE_INFINITY;
                                // for (int i = 0; i <
                                // realLongitudeValues.length; i++) {
                                // float value = realLongitudeValues[i];
                                // value = (value + 360.000f) % 360.000f;
                                // realLongitudeValues[i] = value;
                                //
                                // if (value < min) {
                                // min = value;
                                // }
                                // if (value > max) {
                                // max = value;
                                // }
                                // }
                                //
                                // logger.debug("longitudes for " +
                                // variable.getFullName() + " exist between " +
                                // min
                                // + " and " + max);
                                // }
                            } else {
                                throw new VariableNotCompatibleException("Variable " + variable.getFullName()
                                        + " was found with mismatching dimensions");
                            }
                        }
                    }
                }

                if (latDimensionSize > 0 && lonDimensionSize > 0) {
                    // Since we didnt get an exception yet, it seems it's
                    // mappable, so we need to do our thing

                    determineLatBounds(ncfile, variableInThisFile);

                    // Loop over the dimensions to see if there's a time
                    // dimension
                    boolean timeStepsInFile = false;
                    for (Dimension d : variableInThisFile.getDimensions()) {
                        if (d.getFullName().contains("time")) {
                            for (int t = 0; t < d.getLength(); t++) {
                                TimeStep newTimeStep = new TimeStep(file, sequenceNumber, t, true);
                                timeSteps.add(newTimeStep);
                                timeStepsInFile = true;
                            }
                        }
                    }
                    if (!timeStepsInFile) {
                        TimeStep newTimeStep = new TimeStep(file, sequenceNumber, 0, false);
                        timeSteps.add(newTimeStep);
                    }
                }
            }
            ncfile.close();
            sequenceNumber++;
        }

        Collections.sort(timeSteps);

        try {
            analyseBounds();
        } catch (NoSuchSequenceNumberException | InvalidRangeException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void determineLatBounds(NetcdfFile ncfile, Variable variableInFile) throws IOException {
        float latMin = cacheAtDataLocation.readLatMin(variable.getFullName());
        float latMax = cacheAtDataLocation.readLatMax(variable.getFullName());
        if (Float.isNaN(latMin) || Float.isNaN(latMax)) {
            latMin = cacheAtProgramLocation.readLatMin(variable.getFullName());
            latMax = cacheAtProgramLocation.readLatMax(variable.getFullName());
        }
        if (Float.isNaN(latMin) || Float.isNaN(latMax)) {
            List<Attribute> attributes = variableInFile.getAttributes();
            Variable latitudes = null;
            for (Attribute a : attributes) {
                if (a.getFullName().compareTo("coordinates") == 0) {
                    String[] coords = a.getStringValue().split("[ ]");
                    for (String c : coords) {
                        if (c.contains("lat") || c.contains("LAT") || c.contains("ni")) {
                            Variable potentialLatitudes = ncfile.findVariable(c);
                            if (potentialLatitudes != null) {
                                latitudes = potentialLatitudes;
                            }
                        }
                    }
                }
            }

            if (latitudes == null) {
                for (Dimension d : variableInFile.getDimensions()) {
                    if (d.getFullName().contains("lat") || d.getFullName().contains("LAT")
                            || d.getFullName().contains("nj")) {
                        Variable potentialLatitudes = ncfile.findVariable(d.getFullName());
                        if (potentialLatitudes != null) {
                            latitudes = potentialLatitudes;
                        }
                    }
                }
            }

            if (latitudes != null) {
                float fillValue = Float.NEGATIVE_INFINITY;
                for (Attribute a : latitudes.getAttributes()) {
                    if (a.getFullName().compareTo("_FillValue") == 0) {
                        fillValue = a.getNumericValue().floatValue();
                    }
                }

                Array netCDFArray = latitudes.read();
                realLatitudeValues = (float[]) netCDFArray.get1DJavaArray(float.class);

                float min = Float.POSITIVE_INFINITY;
                float max = Float.NEGATIVE_INFINITY;
                for (float value : realLatitudeValues) {
                    if (value != fillValue) {
                        if (value < min) {
                            min = value;
                        }
                        if (value > max) {
                            max = value;
                        }
                    }
                }
                minimumLatitude = min;
                maximumLatitude = max;

                cacheAtDataLocation.writeLatMin(variableInFile.getFullName(), min);
                cacheAtProgramLocation.writeLatMin(variableInFile.getFullName(), min);
                cacheAtDataLocation.writeLatMax(variableInFile.getFullName(), max);
                cacheAtProgramLocation.writeLatMax(variableInFile.getFullName(), max);
            } else {
                minimumLatitude = -90f;
                maximumLatitude = 90f;
            }
        }

        logger.debug("latitudes for " + variable.getFullName() + " exist between " + minimumLatitude + " and "
                + maximumLatitude);
    }

    private void analyseBounds() throws NoSuchSequenceNumberException, InvalidRangeException, IOException {
        // First, determine the fillValue, we dont want that skewing our
        // results...
        fillValue = Float.NEGATIVE_INFINITY;
        for (Attribute a : variable.getAttributes()) {
            if (a.getFullName().compareTo("_FillValue") == 0) {
                fillValue = a.getNumericValue().floatValue();
            }
        }

        float resultMin = Float.NaN, resultMax = Float.NaN;

        // Check the settings first to see if this value was predefined.
        float settingsMin = settings.getVarMin(variable.getFullName());
        float settingsMax = settings.getVarMax(variable.getFullName());
        if (!Float.isNaN(settingsMin)) {
            resultMin = settingsMin;
            logger.debug("Settings hit for min " + variable.getFullName() + " : " + resultMin);
        }
        if (!Float.isNaN(settingsMax)) {
            resultMax = settingsMax;
            logger.debug("Settings hit for max " + variable.getFullName() + " : " + resultMax);
        }

        // Then Check if we have made a cacheFileManager file earlier and the
        // value is in there
        if (Float.isNaN(resultMin)) {
            float cacheMin = cacheAtDataLocation.readMin(variable.getFullName());
            if (!Float.isNaN(cacheMin)) {
                resultMin = cacheMin;
                logger.debug("Cache hit for min " + variable.getFullName() + " : " + resultMin);
            }
        }

        if (Float.isNaN(resultMax)) {
            float cacheMax = cacheAtDataLocation.readMax(variable.getFullName());
            if (!Float.isNaN(cacheMax)) {
                resultMax = cacheMax;
                logger.debug("Cache hit for max " + variable.getFullName() + " : " + resultMax);
            }
        }

        if (Float.isNaN(resultMin)) {
            float cacheMin = cacheAtProgramLocation.readMin(variable.getFullName());
            if (!Float.isNaN(cacheMin)) {
                resultMin = cacheMin;
                logger.debug("Cache hit for min " + variable.getFullName() + " : " + resultMin);
            }
        }

        if (Float.isNaN(resultMax)) {
            float cacheMax = cacheAtProgramLocation.readMax(variable.getFullName());
            if (!Float.isNaN(cacheMax)) {
                resultMax = cacheMax;
                logger.debug("Cache hit for max " + variable.getFullName() + " : " + resultMax);
            }
        }

        // If we have both covered by now, we're done and don't need to read the
        // file.
        if (!Float.isNaN(resultMin) && !Float.isNaN(resultMax)) {
            minimumValue = resultMin;
            maximumValue = resultMax;
        } else {
            // One of these is not in settings, not in cache, so we need to
            // determine the bounds by hand.
            float tempMin = Float.POSITIVE_INFINITY, tempMax = Float.NEGATIVE_INFINITY;
            for (long t : getSequenceNumbers()) {
                if (heightDimensionSize == 0) {
                    float[] dataSlice = getData(t, 0);
                    for (int i = 0; i < dataSlice.length; i++) {
                        float value = dataSlice[i];
                        if (value != fillValue && value < tempMin) {
                            tempMin = value;
                        }
                        if (value != fillValue && value > tempMax) {
                            tempMax = value;
                        }
                    }
                } else if (heightDimensionSize == 1) {
                    float[] dataSlice = getData(t, 0);
                    for (int i = 0; i < dataSlice.length; i++) {
                        float value = dataSlice[i];
                        if (value != fillValue && value < tempMin) {
                            tempMin = value;
                        }
                        if (value != fillValue && value > tempMax) {
                            tempMax = value;
                        }
                    }
                    if (tempMin == Float.POSITIVE_INFINITY && tempMax == Float.NEGATIVE_INFINITY) {
                        dataSlice = getData(t, 1);
                        for (int i = 0; i < dataSlice.length; i++) {
                            float value = dataSlice[i];
                            if (value != fillValue && value < tempMin) {
                                tempMin = value;
                            }
                            if (value != fillValue && value > tempMax) {
                                tempMax = value;
                            }
                        }
                    }
                } else {
                    for (int d = 0; d < heightDimensionSize; d++) {
                        float[] dataSlice = getData(t, d);
                        for (int i = 0; i < dataSlice.length; i++) {
                            float value = dataSlice[i];
                            if (value != fillValue && value < tempMin) {
                                tempMin = value;
                            }
                            if (value != fillValue && value > tempMax) {
                                tempMax = value;
                            }
                        }
                    }
                }
            }
            if (!Float.isNaN(resultMin)) {
                minimumValue = resultMin;
            } else {
                // Round to 10 percent below the diff
                // double diff = tempMax - tempMin;
                // double result = Math.round((tempMin - (diff * 0.1)) * 100) /
                // 100;
                minimumValue = tempMin;
                logger.debug("Calculated min " + variable.getFullName() + " : " + tempMin);
            }
            if (!Float.isNaN(resultMax)) {
                maximumValue = resultMax;
            } else {
                // Round to 10 percent above the diff
                // double diff = tempMax - tempMin;
                // double result = Math.round(diff * 1.1 * 100) / 100;
                maximumValue = tempMax;
                logger.debug("Calculated max " + variable.getFullName() + " : " + tempMax);
            }
        }

        cacheAtDataLocation.writeMin(variable.getFullName(), minimumValue);
        cacheAtDataLocation.writeMax(variable.getFullName(), maximumValue);
        cacheAtProgramLocation.writeMin(variable.getFullName(), minimumValue);
        cacheAtProgramLocation.writeMax(variable.getFullName(), maximumValue);

        settings.setVarMin(variable.getFullName(), minimumValue);
        settings.setVarMax(variable.getFullName(), maximumValue);
    }

    public synchronized float[] getData(long sequenceNumber, int requestedDepth) throws NoSuchSequenceNumberException,
            InvalidRangeException, IOException {
        File wantedFile = null;
        TimeStep wantedTimestep = null;
        for (TimeStep t : timeSteps) {
            if ((long) t.getFileSequenceNumber() * (long) MAX_TIMESTEPS_IN_SINGLE_FILE + t.getTimeStepWithinFile() == sequenceNumber) {
                wantedFile = t.getFile();
                wantedTimestep = t;
            }
        }
        if (wantedFile == null || wantedTimestep == null) {
            throw new NoSuchSequenceNumberException("Sequence number " + sequenceNumber
                    + " requested but not available.");
        }

        NetcdfFile netcdfFile = NetcdfFile.open(wantedFile.getAbsolutePath());
        Variable fileVariable = netcdfFile.findVariable(variable.getFullName());

        float[] data = null;

        Array netCDFArray = null;
        if (wantedTimestep.hasTimesteps()) {
            if (heightDimensionSize > 0) {
                netCDFArray = fileVariable.slice(0, wantedTimestep.getTimeStepWithinFile()).slice(0, requestedDepth)
                        .read();
            } else {
                netCDFArray = fileVariable.slice(0, wantedTimestep.getTimeStepWithinFile()).read();
            }
        } else {
            if (heightDimensionSize > 0) {
                netCDFArray = fileVariable.slice(0, requestedDepth).read();
            } else {
                netCDFArray = fileVariable.read();
            }
        }

        data = (float[]) netCDFArray.get1DJavaArray(float.class);

        netcdfFile.close();

        return data;
    }

    public synchronized int getHeightDimensionSize() {
        return heightDimensionSize;
    }

    public synchronized int getLatDimensionSize() {
        return latDimensionSize;
    }

    public synchronized int getLonDimensionSize() {
        return lonDimensionSize;
    }

    public synchronized List<Long> getSequenceNumbers() {
        List<Long> result = new ArrayList<Long>();
        for (TimeStep t : timeSteps) {
            result.add((long) t.getFileSequenceNumber() * (long) MAX_TIMESTEPS_IN_SINGLE_FILE
                    + t.getTimeStepWithinFile());
        }
        return result;
    }

    public synchronized float getMinimumValue() {
        return minimumValue;
    }

    public synchronized float getMaximumValue() {
        return maximumValue;
    }

    public synchronized float getFillValue() {
        return fillValue;
    }

    public synchronized String getName() {
        return variable.getFullName();
    }

    public synchronized String getUnits() {
        return variable.getUnitsString();
    }

    public float getMinLatitude() {
        return minimumLatitude;
    }

    public float getMaxLatitude() {
        return maximumLatitude;
    }

}
