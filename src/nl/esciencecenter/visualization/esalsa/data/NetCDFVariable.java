package nl.esciencecenter.visualization.esalsa.data;

import java.util.ArrayList;
import java.util.List;

import ucar.nc2.Variable;

public class NetCDFVariable {
    private final String                    name;
    private final Variable                  netcdfVar;
    private int                             times = 0, depths = 0;
    private final int                       latitudes, longitudes;
    private float                           min   = Float.NaN, max = Float.NaN, fillvalue = Float.NaN;

    private final List<TimeFileAssociation> timeFileAssociations;

    public NetCDFVariable(String name, Variable var, int lats, int lons) {
        this.name = name;
        this.netcdfVar = var;
        this.latitudes = lats;
        this.longitudes = lons;

        timeFileAssociations = new ArrayList<NetCDFVariable.TimeFileAssociation>();
    }

    public String getName() {
        return name;
    }

    public Variable getNetcdfVar() {
        return netcdfVar;
    }

    public int getTimes() {
        return times;
    }

    public int getDepths() {
        return depths;
    }

    public int getLatitudes() {
        return latitudes;
    }

    public int getLongitudes() {
        return longitudes;
    }

    public float getMin() {
        return min;
    }

    public float getMax() {
        return max;
    }

    public float getFillvalue() {
        return fillvalue;
    }

    public NetCDFReader getTimeFile(int time) throws TimestepNotFoundException {
        for (TimeFileAssociation t : timeFileAssociations) {
            if (t.getTimestep() == time) {
                return t.getReader();
            }
        }

        throw new TimestepNotFoundException();
    }

    public void setTimes(int times) {
        this.times = times;
    }

    public void setDepths(int depths) {
        this.depths = depths;
    }

    public void setMin(float min) {
        this.min = min;
    }

    public void setMax(float max) {
        this.max = max;
    }

    public void setFillvalue(float fillvalue) {
        this.fillvalue = fillvalue;
    }

    public void addTimeFileAssociations(List<TimeFileAssociation> timeFileAssociations) {
        this.timeFileAssociations.addAll(timeFileAssociations);
    }
}