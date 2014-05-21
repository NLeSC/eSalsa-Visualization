package nl.esciencecenter.visualization.esalsa.data.reworked;

import java.io.File;
import java.io.IOException;
import java.util.List;

import nl.esciencecenter.visualization.esalsa.CacheFileManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

public class NCDFVariable {
    private final static Logger logger = LoggerFactory.getLogger(NCDFVariable.class);

    private class TimeStep {
        File file;
        int  timeStepWithinFile;

        public TimeStep(File file, int timeStepWithinFile) {
            this.file = file;
            this.timeStepWithinFile = timeStepWithinFile;
        }

        public File getFile() {
            return file;
        }

        public int getTimeStepWithinFile() {
            return timeStepWithinFile;
        }
    }

    private final CacheFileManager cache;

    private Variable               variable;
    private List<TimeStep>         timeSteps;

    int                            heightDimensionSize = 0;
    int                            latDimensionSize    = 0;
    int                            lonDimensionSize    = 0;

    public NCDFVariable(Variable variable, NCDFDataSet dataSet) throws VariableNotCompatibleException {
        // Loop over the files to see if the variable is in there
        List<File> files = dataSet.getFiles();
        for (File file : files) {
            NetcdfFile ncfile = open(file);

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

                    if (d.getFullName().contains("lat")) {
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

                    if (d.getFullName().contains("lon")) {
                        int currentlonDimensionSize = d.getLength();
                        if (lonDimensionSize != currentlonDimensionSize) {
                            if (lonDimensionSize == 0) {
                                lonDimensionSize = currentlonDimensionSize;
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

                    // Loop over the dimensions to see if there's a time
                    // dimension
                    int timeStepsInThisFile = 1;
                    for (Dimension d : variableInThisFile.getDimensions()) {
                        if (d.getFullName().contains("time")) {

                        }
                    }
                }
            }
        }
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
}
