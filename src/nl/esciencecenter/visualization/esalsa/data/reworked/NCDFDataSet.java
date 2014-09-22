package nl.esciencecenter.visualization.esalsa.data.reworked;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

public class NCDFDataSet {
    private final static Logger logger = LoggerFactory.getLogger(NCDFDataSet.class);
    private final List<NCDFVariable> variables;

    public NCDFDataSet(List<File> files) throws IOException, VariableNotCompatibleException {
        variables = new ArrayList<NCDFVariable>();
        Collections.sort(files);

        for (File file : files) {
            logger.debug("Opening " + file.getName());
            NetcdfFile ncfile = NetcdfFile.open(file.getAbsolutePath());

            List<Variable> fileVariables = ncfile.getVariables();
            for (Variable v : fileVariables) {
                if (v.getShape().length > 1) {
                	boolean hasTime = false;
                    boolean hasLat = false;
                    boolean hasLon = false;                    
                    for (Dimension d : v.getDimensions()) {
                        if (d.getFullName().contains("time")) {
                            hasTime = true;
                        }
                        if (d.getFullName().contains("lat") || d.getFullName().contains("nj")) {
                            hasLat = true;
                        }

                        if (d.getFullName().contains("lon") || d.getFullName().contains("ni")) {
                            hasLon = true;
                        }
                    }

                    if (hasTime && hasLon && hasLat) {
                        logger.debug("Variable " + v.getFullName());

                        boolean alreadyAnalysed = false;
                        for (NCDFVariable dataSetVariable : variables) {
                            if (dataSetVariable.getName().compareTo(v.getFullName()) == 0) {
                                alreadyAnalysed = true;
                            }
                        }
                        if (!alreadyAnalysed) {
                            NCDFVariable newVariable = new NCDFVariable(v, files);
                            variables.add(newVariable);
                        }
                    }
                }
            }
        }
    }

    public synchronized List<String> getVariableNames() {
        List<String> result = new ArrayList<String>();
        for (NCDFVariable v : variables) {
            result.add(v.getName());
        }
        return result;
    }

    public synchronized NCDFVariable getVariable(String name) {
        NCDFVariable result = null;
        for (NCDFVariable v : variables) {
            if (v.getName().compareTo(name) == 0) {
                result = v;
            }
        }
        return result;
    }

}
