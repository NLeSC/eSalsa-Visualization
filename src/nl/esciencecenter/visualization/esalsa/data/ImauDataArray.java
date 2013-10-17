package nl.esciencecenter.visualization.esalsa.data;

import java.io.IOException;
import java.util.List;

import nl.esciencecenter.neon.exceptions.UninitializedException;
import ucar.ma2.Array;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

public class ImauDataArray implements Runnable {
    protected SurfaceTextureDescription description;

    private final NetcdfFile ncFile1, ncFile2;
    private boolean initialized = false;
    private float[] data;
    private int width;
    private int height;

    public ImauDataArray(NetcdfFile frameFile, SurfaceTextureDescription description) throws IOException {
        this.description = description;

        this.ncFile1 = frameFile;
        this.ncFile2 = null;
    }

    public ImauDataArray(NetcdfFile frameFile1, NetcdfFile frameFile2, SurfaceTextureDescription description)
            throws IOException {
        this.description = description;

        this.ncFile1 = frameFile1;
        this.ncFile2 = frameFile2;
    }

    @Override
    public void run() {
        if (!initialized) {
            Variable ncdfVar1 = ncFile1.findVariable(description.getVarName());
            List<Dimension> dims = ncdfVar1.getDimensions();
            for (Dimension d : dims) {
                if (d.getName().compareTo("t_lat") == 0 || d.getName().compareTo("u_lat") == 0) {
                    height = d.getLength();
                } else if (d.getName().compareTo("t_lon") == 0 || d.getName().compareTo("u_lon") == 0) {
                    width = d.getLength();
                }
            }

            int depthDim = 0;
            if (dims.size() > 3) {
                if (dims.get(0).getLength() == 1 || dims.size() > 4) {
                    depthDim = 1;
                } else if (dims.size() > 2) {
                    depthDim = 0;
                }
            }

            if (ncFile2 == null) {
                try {
                    Array ncdfArray2D;
                    if (dims.size() > 2) {
                        ncdfArray2D = ncdfVar1.slice(depthDim, description.getDepth()).read();
                    } else {
                        ncdfArray2D = ncdfVar1.read();
                    }

                    float[] result = (float[]) ncdfArray2D.get1DJavaArray(float.class);

                    data = result;

                    NetCDFUtil.close(ncFile1);

                    initialized = true;
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InvalidRangeException e) {
                    e.printStackTrace();
                }

            } else {
                try {
                    Variable ncdfVar2 = ncFile2.findVariable(description.getVarName());

                    Array ncdfArray2D1, ncdfArray2D2;
                    if (dims.size() > 2) {
                        ncdfArray2D1 = ncdfVar1.slice(depthDim, description.getDepth()).read();
                        ncdfArray2D2 = ncdfVar2.slice(depthDim, description.getDepth()).read();
                    } else {
                        ncdfArray2D1 = ncdfVar1.read();
                        ncdfArray2D2 = ncdfVar2.read();
                    }

                    float[] result1 = (float[]) ncdfArray2D1.get1DJavaArray(float.class);
                    float[] result2 = (float[]) ncdfArray2D2.get1DJavaArray(float.class);

                    float[] result = new float[result1.length];
                    for (int i = 0; i < result.length; i++) {
                        if (result1[i] < -1E33 || result2[i] < -1E33) {
                            result[i] = result1[i];
                        } else {
                            result[i] = result2[i] - result1[i];
                        }
                    }

                    data = result;

                    NetCDFUtil.close(ncFile1);
                    NetCDFUtil.close(ncFile2);

                    initialized = true;
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InvalidRangeException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public float[] getData() throws UninitializedException {
        if (initialized) {
            return data;
        }

        throw new UninitializedException();
    }

    public int getWidth() throws UninitializedException {
        if (initialized) {
            return width;
        }

        throw new UninitializedException();
    }

    public int getHeight() throws UninitializedException {
        if (initialized) {
            return height;
        }

        throw new UninitializedException();
    }

    @Override
    public int hashCode() {
        return description.hashCode();
    }

    @Override
    public boolean equals(Object thatObject) {
        if (this == thatObject)
            return true;
        if (!(thatObject instanceof ImauDataArray))
            return false;

        // cast to native object is now safe
        ImauDataArray that = (ImauDataArray) thatObject;

        // now a proper field-by-field evaluation can be made
        return (description.equals(that.description));
    }

    public SurfaceTextureDescription getDescription() {
        return description;
    }
}