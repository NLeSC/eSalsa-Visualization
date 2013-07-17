package nl.esciencecenter.visualization.esalsa.data;

public class SurfaceTextureDescription {
    protected final int     frameNumber;
    protected final int     depth;
    protected final String  varName;
    protected final String  colorMap;
    protected final boolean dynamicDimensions;
    protected final boolean diff;
    protected final boolean secondSet;
    protected final float   lowerBound;
    protected final float   upperBound;

    public SurfaceTextureDescription(int frameNumber, int depth,
            String varName, String colorMap, boolean dynamicDimensions,
            boolean diff, boolean secondSet, float lowerBound, float upperBound) {
        this.frameNumber = frameNumber;
        this.depth = depth;
        this.varName = varName;
        this.colorMap = colorMap;
        this.dynamicDimensions = dynamicDimensions;
        this.diff = diff;
        this.secondSet = secondSet;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public int getFrameNumber() {
        return frameNumber;
    }

    public int getDepth() {
        return depth;
    }

    public String getVarName() {
        return varName;
    }

    public String getColorMap() {
        return colorMap;
    }

    public boolean isDynamicDimensions() {
        return dynamicDimensions;
    }

    public boolean isDiff() {
        return diff;
    }

    public boolean isSecondSet() {
        return secondSet;
    }

    public float getLowerBound() {
        return lowerBound;
    }

    public float getUpperBound() {
        return upperBound;
    }

    @Override
    public int hashCode() {
        int dataModePrime = (frameNumber + 3) * 23;
        int dynamicPrime = ((dynamicDimensions ? 1 : 3) + 41) * 313;
        int diffPrime = ((diff ? 3 : 5) + 43) * 313;
        int secondPrime = ((diff ? 5 : 7) + 53) * 313;
        int variablePrime = (varName.hashCode() + 67) * 859;
        int frameNumberPrime = (frameNumber + 131) * 1543;
        int depthPrime = (depth + 251) * 2957;
        int colorMapPrime = (colorMap.hashCode() + 919) * 7883;
        int lowerBoundPrime = (int) ((lowerBound + 41) * 1543);
        int upperBoundPrime = (int) ((upperBound + 67) * 2957);

        int hashCode = frameNumberPrime + dynamicPrime + diffPrime
                + secondPrime + depthPrime + dataModePrime + variablePrime
                + colorMapPrime + lowerBoundPrime + upperBoundPrime;

        return hashCode;
    }

    @Override
    public boolean equals(Object thatObject) {
        if (this == thatObject)
            return true;
        if (!(thatObject instanceof SurfaceTextureDescription))
            return false;

        // cast to native object is now safe
        SurfaceTextureDescription that = (SurfaceTextureDescription) thatObject;

        // now a proper field-by-field evaluation can be made
        return (dynamicDimensions == that.dynamicDimensions
                && diff == that.diff && secondSet == that.secondSet
                && varName.compareTo(that.varName) == 0
                && frameNumber == that.frameNumber
                && lowerBound == that.lowerBound
                && upperBound == that.upperBound && depth == that.depth && colorMap
                    .compareTo(that.colorMap) == 0);
    }

    public int getDataModeIndex() {
        if (!diff) {
            if (secondSet) {
                return 1;
            } else {
                return 0;
            }
        }
        return 2;
    }

    public String verbalizeDataMode() {
        if (!diff) {
            if (secondSet) {
                return "0.5 Sv";
            } else {
                return "Control";
            }
        }

        return "Difference";
    }

    public static String[] getDataModes() {
        return new String[] { "Control", "0.5 Sv", "Difference" };
    }
}
