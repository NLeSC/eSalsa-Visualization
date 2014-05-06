package nl.esciencecenter.visualization.esalsa;

import java.nio.IntBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jogamp.common.nio.Buffers;

public class IntArrayTexture extends Texture2D {
    private final static Logger logger = LoggerFactory.getLogger(IntArrayTexture.class);

    public IntArrayTexture(int glMultitexUnit, int[] pixels, int width, int height) {
        super(glMultitexUnit);
        this.pixelBuffer = Buffers.copyIntBufferAsByteBuffer(IntBuffer.wrap(pixels));
        this.width = width;
        this.height = height;
    }

}
