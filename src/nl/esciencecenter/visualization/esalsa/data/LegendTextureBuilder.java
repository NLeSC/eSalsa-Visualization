package nl.esciencecenter.visualization.esalsa.data;

import java.nio.ByteBuffer;

import nl.esciencecenter.neon.swing.ColormapInterpreter;
import nl.esciencecenter.neon.swing.ColormapInterpreter.Color;
import nl.esciencecenter.neon.swing.ColormapInterpreter.Dimensions;
import nl.esciencecenter.visualization.esalsa.ImauSettings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LegendTextureBuilder implements Runnable {
    private final static Logger logger = LoggerFactory.getLogger(LegendTextureBuilder.class);
    private final ImauSettings settings = ImauSettings.getInstance();

    protected SurfaceTextureDescription description;

    private final ImauDataArray inputArray;
    private final TextureStorage texStore;
    private boolean initialized;

    public LegendTextureBuilder(TextureStorage texStore, ImauDataArray inputArray) {
        this.texStore = texStore;
        this.inputArray = inputArray;
        this.description = inputArray.getDescription();
    }

    @Override
    public void run() {
        if (!initialized) {
            Dimensions dims = getDimensions(inputArray.getDescription());

            int height = 500;
            int width = 1;
            ByteBuffer outBuf = ByteBuffer.allocate(height * width * 4);

            for (int row = height - 1; row >= 0; row--) {
                float index = row / (float) height;
                float var = (index * dims.getDiff()) + dims.getMin();

                Color c = ColormapInterpreter.getColor(description.getColorMap(), dims, var);

                for (int col = 0; col < width; col++) {
                    outBuf.put((byte) (255 * c.getRed()));
                    outBuf.put((byte) (255 * c.getGreen()));
                    outBuf.put((byte) (255 * c.getBlue()));
                    outBuf.put((byte) 1);
                }
            }

            outBuf.flip();

            texStore.setLegendImage(description, outBuf);
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
        if (!(thatObject instanceof LegendTextureBuilder))
            return false;

        // cast to native object is now safe
        LegendTextureBuilder that = (LegendTextureBuilder) thatObject;

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
