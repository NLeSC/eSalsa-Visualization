package nl.esciencecenter.visualization.esalsa.data;

import java.nio.ByteBuffer;

import nl.esciencecenter.esight.exceptions.UninitializedException;
import nl.esciencecenter.esight.swing.ColormapInterpreter;
import nl.esciencecenter.esight.swing.ColormapInterpreter.Color;
import nl.esciencecenter.esight.swing.ColormapInterpreter.Dimensions;
import nl.esciencecenter.visualization.esalsa.ImauSettings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SurfaceTextureBuilder implements Runnable {
    private final static Logger logger = LoggerFactory
            .getLogger(SurfaceTextureBuilder.class);
    private final ImauSettings settings = ImauSettings.getInstance();

    protected SurfaceTextureDescription description;

    private final ImauDataArray inputArray;
    private final TextureStorage texStore;
    private boolean initialized;
    private final int imageHeight;
    private final int blankRows;

    public SurfaceTextureBuilder(TextureStorage texStore,
            ImauDataArray inputArray, int imageHeight, int blankRows) {
        this.texStore = texStore;
        this.inputArray = inputArray;
        this.description = inputArray.getDescription();

        this.imageHeight = imageHeight;
        this.blankRows = blankRows;
    }

    @Override
    public void run() {
        if (!initialized) {
            try {
                int dsWidth = inputArray.getWidth();
                int dsHeight = inputArray.getHeight();

                int pixels = imageHeight * dsWidth;

                ByteBuffer outBuf = ByteBuffer.allocate(pixels * 4);
                outBuf.clear();
                outBuf.rewind();

                for (int i = 0; i < blankRows; i++) {
                    for (int w = 0; w < dsWidth; w++) {
                        outBuf.put((byte) 0);
                        outBuf.put((byte) 0);
                        outBuf.put((byte) 0);
                        outBuf.put((byte) 0);
                    }
                }

                Dimensions dims = getDimensions(inputArray.getDescription());

                String mapName = description.getColorMap();
                float[] data = inputArray.getData();

                for (int row = dsHeight - 1; row >= 0; row--) {
                    for (int col = 0; col < dsWidth; col++) {
                        int i = (row * dsWidth + col);
                        Color c = ColormapInterpreter.getColor(mapName, dims,
                                data[i]);

                        outBuf.put((byte) (255 * c.red));
                        outBuf.put((byte) (255 * c.green));
                        outBuf.put((byte) (255 * c.blue));
                        outBuf.put((byte) 0);
                    }
                }

                while (outBuf.hasRemaining()) {
                    outBuf.put((byte) 0);
                }

                outBuf.flip();

                texStore.setSurfaceImage(description, outBuf);

            } catch (UninitializedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public int hashCode() {
        return description.hashCode();
    }

    @Override
    public boolean equals(Object thatObject) {
        if (this == thatObject)
            return true;
        if (!(thatObject instanceof SurfaceTextureBuilder))
            return false;

        // cast to native object is now safe
        SurfaceTextureBuilder that = (SurfaceTextureBuilder) thatObject;

        // now a proper field-by-field evaluation can be made
        return (description.equals(that.description));
    }

    public Dimensions getDimensions(SurfaceTextureDescription desc) {
        float max = 0;
        float min = 0;

        if (desc.isDiff()) {
            max = settings.getCurrentVarDiffMax(desc.getVarName());
            min = settings.getCurrentVarDiffMin(desc.getVarName());

        } else {
            max = settings.getCurrentVarMax(desc.getVarName());
            min = settings.getCurrentVarMin(desc.getVarName());
        }

        return new Dimensions(min, max);
    }
}
