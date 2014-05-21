package nl.esciencecenter.visualization.esalsa.data;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
    private final static Logger        logger   = LoggerFactory.getLogger(NetCDFReader.class);
    private final ImauSettings         settings = ImauSettings.getInstance();

    private final File                 file;
    private final NetcdfFile           ncfile;
    private final List<NetCDFVariable> variables;

    private final CacheFileManager     cache;

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

        variables = new ArrayList<NetCDFVariable>();

        List<Variable> vars = ncfile.getVariables();

        for (Variable v : vars) {
            String name = v.getFullName();

            int latDimensionSize = 0;
            int lonDimensionSize = 0;

            for (Dimension d : v.getDimensions()) {
                if (d.getFullName().contains("lat")) {
                    latDimensionSize = d.getLength();
                }
                if (d.getFullName().contains("lon")) {
                    lonDimensionSize = d.getLength();
                }
            }

            if (latDimensionSize > 0 && lonDimensionSize > 0) {
                NetCDFVariable newVar = new NetCDFVariable(name, v, latDimensionSize, lonDimensionSize);

                variables.add(newVar);
                System.out.println("Variable: " + name + " found and available for use.");
            }
        }
    }

    public synchronized int getThisParticularFrameNumber() {
        // Assume we've given the correct file but it has no time variable,
        // therefore it 'must be' a single frame.
        return Integer.parseInt(getSequenceNumber(file));
    }

    public synchronized ArrayList<String> getVariableNames() {
        ArrayList<String> result = new ArrayList<String>();
        for (NetCDFVariable var : variables) {
            result.add(var.getName());
        }

        return result;
    }

    public synchronized float[] getData(NetCDFVariable netcdfVar, int requestedDepth) throws VariableNotFoundException {
        Variable variable = netcdfVar.getNetcdfVar();

        float[] data = null;

        Array netCDFArray = null;
        try {
            if (variable.getDimension(0).getFullName().contains("depth")
                    || variable.getDimension(0).getFullName().contains("z_t")
                    || variable.getDimension(0).getFullName().contains("lev")) {
                netCDFArray = variable.slice(0, requestedDepth).read();

            } else if (variable.getDimension(0).getFullName().contains("time")
                    && variable.getDimension(0).getLength() == 1) {
                if (variable.getDimension(1).getFullName().contains("depth")
                        || variable.getDimension(1).getFullName().contains("z_t")
                        || variable.getDimension(1).getFullName().contains("lev")) {
                    netCDFArray = variable.slice(0, 0).slice(1, requestedDepth).read();
                }
            } else {
                netCDFArray = variable.read();
            }

            data = (float[]) netCDFArray.get1DJavaArray(float.class);
        } catch (IOException | InvalidRangeException e) {
            e.printStackTrace();
        }

        return data;
    }

    public synchronized List<TimeFileAssociation> analyse(NetCDFVariable netcdfVariable) {
        List<TimeFileAssociation> result = new ArrayList<TimeFileAssociation>();

        // Check the settings first to see if this value was predefined.
        float settingsMin = settings.getVarMin(netcdfVariable.getName());
        float settingsMax = settings.getVarMax(netcdfVariable.getName());
        if (!Float.isNaN(settingsMin)) {
            netcdfVariable.setMin(settingsMin);
        }
        if (!Float.isNaN(settingsMax)) {
            netcdfVariable.setMin(settingsMax);
        }

        // Then Check if we have made a cacheFileManager file earlier
        float cacheMin = cache.readMin(netcdfVariable.getName());
        if (!Float.isNaN(netcdfVariable.getMin()) && !Float.isNaN(cacheMin)) {
            netcdfVariable.setMin(cacheMin);
            settings.setVarMin(netcdfVariable.getName(), cacheMin);
        }

        float cacheMax = cache.readMax(netcdfVariable.getName());
        if (!Float.isNaN(netcdfVariable.getMax()) && !Float.isNaN(cacheMax)) {
            netcdfVariable.setMax(cacheMax);
            settings.setVarMax(netcdfVariable.getName(), cacheMax);
        }

        Variable variable = netcdfVariable.getNetcdfVar();

        float fillValue = Float.NEGATIVE_INFINITY;
        for (Attribute a : variable.getAttributes()) {
            if (a.getFullName().compareTo("_FillValue") == 0) {
                netcdfVariable.setFillvalue(a.getNumericValue().floatValue());
            }
        }

        if (!Float.isNaN(netcdfVariable.getMin()) && !Float.isNaN(netcdfVariable.getMax())) {
            Variable v = netcdfVariable.getNetcdfVar();

            List<Dimension> dimensions = v.getDimensions();
            int[] shapes = v.getShape();
            for (int i = 0; i < dimensions.size(); i++) {
                if (dimensions.get(i).getFullName().contains("time")) {
                    netcdfVariable.setTimes(shapes[i]);

                    for (int t = 0; t < shapes[i]; t++) {
                        TimeFileAssociation tfA = new TimeFileAssociation(getThisParticularFrameNumber() + t, this);
                        result.add(tfA);
                    }
                } else {
                    TimeFileAssociation tfA = new TimeFileAssociation(getThisParticularFrameNumber(), this);
                    result.add(tfA);
                }
                if (dimensions.get(i).getFullName().contains("depth")
                        || dimensions.get(i).getFullName().contains("z_t")
                        || dimensions.get(i).getFullName().contains("lev")) {
                    netcdfVariable.setDepths(shapes[i]);
                }
            }

            try {
                Variable resultingSlice;
                Bounds bounds = new Bounds(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);
                if (netcdfVariable.getDepths() > 0 && netcdfVariable.getTimes() > 0) {
                    if (dimensions.get(0).getFullName().contains("time")) {
                        for (int time = 0; time < netcdfVariable.getTimes(); time++) {
                            Variable singleTimeSlicedVariable = variable.slice(0, time);
                            if (dimensions.get(1).getFullName().contains("depth")
                                    || dimensions.get(1).getFullName().contains("z_t")
                                    || dimensions.get(1).getFullName().contains("lev")) {
                                for (int depth = 0; depth < netcdfVariable.getDepths(); depth++) {
                                    resultingSlice = singleTimeSlicedVariable.slice(0, depth);
                                    bounds = getBounds(resultingSlice, netcdfVariable.getLatitudes(),
                                            netcdfVariable.getLongitudes(), fillValue, bounds);
                                }
                            } else {
                                bounds = getBounds(singleTimeSlicedVariable, netcdfVariable.getLatitudes(),
                                        netcdfVariable.getLongitudes(), fillValue, bounds);
                            }
                        }
                    } else if (dimensions.get(0).getFullName().contains("depth")
                            || dimensions.get(0).getFullName().contains("z_t")
                            || dimensions.get(0).getFullName().contains("lev")) {
                        for (int depth = 0; depth < netcdfVariable.getDepths(); depth++) {
                            resultingSlice = variable.slice(0, depth);
                            bounds = getBounds(resultingSlice, netcdfVariable.getLatitudes(),
                                    netcdfVariable.getLongitudes(), fillValue, bounds);
                        }
                    } else {
                        bounds = getBounds(variable, netcdfVariable.getLatitudes(), netcdfVariable.getLongitudes(),
                                fillValue, bounds);
                    }
                }

                netcdfVariable.setMin(bounds.getMin());
                cache.writeMin(netcdfVariable.getName(), bounds.getMin());
                settings.setVarMin(netcdfVariable.getName(), bounds.getMin());

                netcdfVariable.setMax(bounds.getMax());
                cache.writeMax(netcdfVariable.getName(), bounds.getMax());
                settings.setVarMax(netcdfVariable.getName(), bounds.getMax());

            } catch (IOException e) {
                e.printStackTrace();
            } catch (InvalidRangeException e) {
                e.printStackTrace();
            }
        }
        return result;
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
            String s2 = "";
            if (i < split.length - 1) {
                try {
                    Integer.parseInt(split[i + 1]);
                } catch (NumberFormatException e) {
                    // IGNORE
                }
                s2 = split[i + 1];
            }
            String s3 = "";
            if (i < split.length - 2) {
                try {
                    Integer.parseInt(split[i + 2]);
                } catch (NumberFormatException e) {
                    // IGNORE
                }
                s3 = split[i + 2];
            }
            s = s + s2 + s3;
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
}
