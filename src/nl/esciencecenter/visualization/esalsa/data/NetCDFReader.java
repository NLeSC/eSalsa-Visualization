package nl.esciencecenter.visualization.esalsa.data;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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

public class NetCDFReader {
    private final static Logger logger = LoggerFactory.getLogger(NetCDFReader.class);
    private final ImauSettings settings = ImauSettings.getInstance();

    private final File file;
    private final NetcdfFile ncfile;
    private final HashMap<String, Variable> variables;

    private final HashMap<String, Float> mins;
    private final HashMap<String, Float> maxes;

    private final CacheFileManager cache;

    private final class Bounds {
        private final float max, min;

        public Bounds(float min, float max) {
            this.min = min;
            this.max = max;
        }

        public float getMax() {
            return max;
        }

        public float getMin() {
            return min;
        }

    }

    public NetCDFReader(File file) {
        this.file = file;
        this.ncfile = open(file);
        cache = new CacheFileManager(file.getParent());

        variables = new HashMap<String, Variable>();

        mins = new HashMap<String, Float>();
        maxes = new HashMap<String, Float>();

        List<Variable> vars = ncfile.getVariables();

        for (Variable v : vars) {
            String name = v.getFullName();

            boolean latDimensionFound = false;
            boolean lonDimensionFound = false;

            for (Dimension d : v.getDimensions()) {
                if (d.getFullName().contains("lat")) {
                    latDimensionFound = true;
                }
                if (d.getFullName().contains("lon")) {
                    lonDimensionFound = true;
                }
            }

            if (latDimensionFound && lonDimensionFound) {
                variables.put(name, v);
                System.out.println("Variable: " + name + " found and available for use.");
            }
        }
    }

    public synchronized int getThisParticularFrameNumber() {
        // Assume we've given the correct file but it has no time variable,
        // therefore it 'must be' a single frame.
        return Integer.parseInt(getSequenceNumber(file));

    }

    public synchronized int getLatSize() {
        int value = -1;
        for (Dimension d : ncfile.getDimensions()) {
            if (d.getFullName().contains("lat")) {
                value = d.getLength();
            }
        }

        return value;
    }

    public synchronized int getLonSize() {
        int value = -1;
        for (Dimension d : ncfile.getDimensions()) {
            if (d.getFullName().contains("lon")) {
                value = d.getLength();
            }
        }

        return value;
    }

    public synchronized ArrayList<String> getVariableNames() {
        ArrayList<String> result = new ArrayList<String>();
        for (String s : variables.keySet()) {
            result.add(s);
            // System.out.println(s);
        }
        return result;
    }

    public synchronized String getUnits(String varName) {
        return variables.get(varName).getUnitsString();
    }

    // public synchronized ByteBuffer getImage(String colorMapname, String
    // variableName, int requestedDepth,
    // boolean logScale) {
    // Variable variable = variables.get(variableName);
    //
    // int times = 0, depths = 0, lats = 0, lons = 0;
    // for (int i = 0; i < shapes.get(variableName).size(); i++) {
    // if (dimensions.get(variableName).get(i).getFullName().contains("time")) {
    // times = shapes.get(variableName).get(i);
    // }
    // if (dimensions.get(variableName).get(i).getFullName().contains("depth")
    // || dimensions.get(variableName).get(i).getFullName().contains("z_t")) {
    // depths = shapes.get(variableName).get(i);
    // }
    // if (dimensions.get(variableName).get(i).getFullName().contains("lat")) {
    // lats = shapes.get(variableName).get(i);
    // }
    // if (dimensions.get(variableName).get(i).getFullName().contains("lon")) {
    // lons = shapes.get(variableName).get(i);
    // }
    // }
    //
    // ByteBuffer result = Buffers.newDirectByteBuffer(lats * lons * 4);
    // result.rewind();
    //
    // try {
    // Array netCDFArray;
    // if (shapes.get(variableName).size() > 2) {
    // netCDFArray = variable.slice(0, requestedDepth).read();
    // } else {
    // netCDFArray = variable.read();
    // }
    //
    // float[] data = (float[]) netCDFArray.get1DJavaArray(float.class);
    //
    // Dimensions colormapDims;
    // if (logScale) {
    // colormapDims = new Dimensions((float)
    // Math.log(settings.getCurrentVarMin(variableName) + 1f),
    // (float) Math.log(settings.getCurrentVarMax(variableName) + 1f));
    // for (int lat = lats - 1; lat > 0; lat--) {
    // for (int lon = lons - 1; lon >= 0; lon--) {
    // Color color = ColormapInterpreter.getColor(colorMapname, colormapDims,
    // (float) Math.log(data[lat * lons + lon] + 1f),
    // (float) Math.log(fillValues.get(variableName)));
    // result.put((byte) (color.getRed() * 255));
    // result.put((byte) (color.getGreen() * 255));
    // result.put((byte) (color.getBlue() * 255));
    // result.put((byte) 0);
    // }
    // }
    // } else {
    // colormapDims = new Dimensions(settings.getCurrentVarMin(variableName),
    // settings.getCurrentVarMax(variableName));
    // for (int lat = lats - 1; lat > 0; lat--) {
    // for (int lon = lons - 1; lon >= 0; lon--) {
    // Color color = ColormapInterpreter.getColor(colorMapname, colormapDims,
    // data[lat * lons + lon],
    // fillValues.get(variableName));
    // result.put((byte) (color.getRed() * 255));
    // result.put((byte) (color.getGreen() * 255));
    // result.put((byte) (color.getBlue() * 255));
    // result.put((byte) 0);
    // }
    // }
    // }
    //
    // } catch (IOException e) {
    // e.printStackTrace();
    // } catch (InvalidRangeException e) {
    // e.printStackTrace();
    // }
    //
    // result.rewind();
    //
    // return result;
    // }

    public synchronized float[] getData(String variableName, int requestedDepth) {
        Variable variable = variables.get(variableName);

        float[] data = null;

        Array netCDFArray = null;
        try {
            Variable v = variables.get(variableName);
            if (v.getDimension(0).getFullName().contains("depth") || v.getDimension(0).getFullName().contains("z_t")) {
                netCDFArray = variable.slice(0, requestedDepth).read();
            } else if (v.getDimension(0).getFullName().contains("time") && v.getDimension(0).getLength() == 1) {
                if (v.getDimension(1).getFullName().contains("depth")
                        || v.getDimension(1).getFullName().contains("z_t")) {
                    netCDFArray = variable.slice(0, 0).slice(1, requestedDepth).read();
                } else {
                    netCDFArray = variable.slice(0, 0).read();
                    // logger.error(v.getFullName() +
                    // "-> v.getDimension(1).getFullName() : "
                    // + v.getDimension(0).getFullName() +
                    // " did not contain either 'time' or 'z_t'");
                }
            } else {
                netCDFArray = variable.read();
                // logger.error(v.getFullName()+"-> v.getDimension(0).getFullName() : "+v.getDimension(0).getFullName()
                // + " did not contain either 'time' or 'z_t'");
            }

            data = (float[]) netCDFArray.get1DJavaArray(float.class);
        } catch (IOException | InvalidRangeException e) {
            e.printStackTrace();
        }

        return data;
    }

    public synchronized void determineMinMax(String variableName) {
        // Check the settings first to see if this value was predefined.
        float settingsMin = settings.getVarMin(variableName);
        float settingsMax = settings.getVarMax(variableName);
        if (!Float.isNaN(settingsMin)) {
            mins.put(variableName, settingsMin);
        }
        if (!Float.isNaN(settingsMax)) {
            maxes.put(variableName, settingsMax);
        }

        // Then Check if we have made a cacheFileManager file earlier
        float cacheMin = cache.readMin(variableName);
        if (!Float.isNaN(cacheMin)) {
            mins.put(variableName, cacheMin);
            settings.setVarMin(variableName, cacheMin);

        }
        float cacheMax = cache.readMax(variableName);
        if (!Float.isNaN(cacheMax)) {
            maxes.put(variableName, cacheMax);
            settings.setVarMax(variableName, cacheMax);

        }

        Variable variable = variables.get(variableName);

        float fillValue = Float.NEGATIVE_INFINITY;
        for (Attribute a : variable.getAttributes()) {
            if (a.getFullName().compareTo("_FillValue") == 0) {
                fillValue = a.getNumericValue().floatValue();
            }
        }

        if (!(mins.containsKey(variableName) && maxes.containsKey(variableName))) {
            Variable v = variables.get(variableName);
            int times = 0, depths = 0, lats = 0, lons = 0;

            List<Dimension> dimensions = v.getDimensions();
            int[] shapes = v.getShape();
            for (int i = 0; i < dimensions.size(); i++) {
                if (dimensions.get(i).getFullName().contains("time")) {
                    times = shapes[i];
                }
                if (dimensions.get(i).getFullName().contains("depth")
                        || dimensions.get(i).getFullName().contains("z_t")) {
                    depths = shapes[i];
                }
                if (dimensions.get(i).getFullName().contains("lat")) {
                    lats = shapes[i];
                }
                if (dimensions.get(i).getFullName().contains("lon")) {
                    lons = shapes[i];
                }
            }
            System.out.println("Determining minimum and maximum values for " + variableName + ", please wait");

            try {
                if (lats == 0 || lons == 0) {
                    System.err
                            .println("ERROR: LATITUDES or LONGITUDES not found in the dimensions of this data file, exiting.");
                    System.exit(1);
                }

                Variable resultingSlice;
                Bounds bounds = new Bounds(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);
                if (times > 0 && depths > 0) {
                    if (dimensions.get(0).getFullName().contains("time")) {
                        for (int time = 0; time < times; time++) {
                            Variable singleTimeSlicedVariable = variable.slice(0, time);
                            for (int depth = 0; depth < depths; depth++) {
                                resultingSlice = singleTimeSlicedVariable.slice(0, depth);
                                bounds = getBounds(resultingSlice, lats, lons, fillValue, bounds);
                            }
                        }
                    } else if (dimensions.get(0).getFullName().contains("depth")
                            || dimensions.get(0).getFullName().contains("z_t")) {
                        for (int depth = 0; depth < depths; depth++) {
                            resultingSlice = variable.slice(0, depth);
                            bounds = getBounds(resultingSlice, lats, lons, fillValue, bounds);
                        }
                    } else {
                        bounds = getBounds(variable, lats, lons, fillValue, bounds);
                    }
                }

                cache.writeMin(variableName, bounds.getMin());
                mins.put(variableName, bounds.getMin());
                settings.setVarMin(variableName, bounds.getMin());

                cache.writeMax(variableName, bounds.getMax());
                maxes.put(variableName, bounds.getMax());
                settings.setVarMax(variableName, bounds.getMax());

            } catch (IOException e) {
                e.printStackTrace();
            } catch (InvalidRangeException e) {
                e.printStackTrace();
            }
        }
    }

    private synchronized Bounds getBounds(Variable var, int lats, int lons, float fillValue, Bounds previousBounds)
            throws IOException {
        float currentMax = previousBounds.getMax();
        float currentMin = previousBounds.getMin();

        Array netCDFArray = var.read();
        float[] data = (float[]) netCDFArray.get1DJavaArray(float.class);

        for (int lat = 0; lat < lats; lat++) {
            for (int lon = lons - 1; lon >= 0; lon--) {
                float pieceOfData = data[lat * lons + lon];
                if (pieceOfData != fillValue) {
                    if (pieceOfData < currentMin) {
                        currentMin = pieceOfData;
                    }
                    if (pieceOfData > currentMax) {
                        currentMax = pieceOfData;
                    }
                }
            }
        }

        return new Bounds(currentMin, currentMax);
    }

    public synchronized float getMinValue(String variableName) {
        if (mins.containsKey(variableName)) {
            return mins.get(variableName);
        } else {
            determineMinMax(variableName);
            return mins.get(variableName);
        }
    }

    public synchronized float getMaxValue(String variableName) {
        if (mins.containsKey(variableName)) {
            return mins.get(variableName);
        } else {
            determineMinMax(variableName);
            return mins.get(variableName);
        }
    }

    @Override
    public synchronized String toString() {
        String result = "";
        for (String name : variables.keySet()) {
            result += "Variable: " + name + "\n";

            Variable v = variables.get(name);

            int i = 0;
            for (Dimension d : v.getDimensions()) {
                result += "Dims: " + d.getFullName() + "[" + v.getShape()[i] + "]\n";
                i++;
            }
        }
        return result;
    }

    private synchronized NetcdfFile open(File file) {
        NetcdfFile ncfile = null;
        try {
            ncfile = NetcdfFile.open(file.getAbsolutePath());
        } catch (IOException ioe) {
            logger.error("trying to open " + file.getAbsolutePath(), ioe);
        }
        return ncfile;
    }

    public synchronized void close() {
        try {
            this.ncfile.close();
        } catch (IOException ioe) {
            logger.error("trying to close " + ncfile.toString(), ioe);
        }
    }

    /**
     * Static function to get a sequence number from a filename in a collection
     * of netcdf files. This currently assumes a file name format with a
     * sequence number of no less than 4 characters wide, and only one such
     * number per file name. example filename:
     * t.t0.1_42l_nccs01.007502.interp900x602.nc, where 007502 is the sequence
     * number.
     * 
     * @param file
     *            A file in the sequence of which the sequence number is
     *            requested.
     * @return The sequence number of the file.
     */
    private synchronized static String getSequenceNumber(File file) {
        final String path = file.getParent();
        final String name = file.getName();
        final String fullPath = path + name;

        String[] split = fullPath.split("[^0-9]");

        boolean foundOne = false;
        String sequenceNumberString = "";
        for (int i = 0; i < split.length; i++) {
            String s = split[i];
            try {
                Integer.parseInt(s);
                if (s.length() >= 5) {
                    sequenceNumberString = s;
                    if (!foundOne) {
                        foundOne = true;
                    } else {
                        System.err.println("ERROR: Filename includes two possible sequence numbers.");
                    }
                }
            } catch (NumberFormatException e) {
                // IGNORE
            }
        }

        return sequenceNumberString;
    }

    public synchronized float getFillValue(String variableName) {
        float fillValue = Float.NEGATIVE_INFINITY;
        for (Attribute a : variables.get(variableName).getAttributes()) {
            if (a.getFullName().compareTo("_FillValue") == 0) {
                fillValue = a.getNumericValue().floatValue();
            }
        }
        return fillValue;
    }
}
