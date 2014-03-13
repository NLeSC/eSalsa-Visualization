package nl.esciencecenter.visualization.esalsa.data;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.esciencecenter.neon.textures.ByteBufferTexture;
import nl.esciencecenter.neon.textures.Texture2D;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jogamp.common.nio.Buffers;

public class EfficientTextureStorage {
    private static final int LEGEND_TEXTURE_HEIGHT = 500;
    private static final int LEGEND_TEXTURE_WIDTH = 1;

    private final static Logger logger = LoggerFactory.getLogger(EfficientTextureStorage.class);

    private final SurfaceTextureDescription[] oldScreenA;
    private final SurfaceTextureDescription[] newScreenA;
    private HashMap<SurfaceTextureDescription, Texture2D> surfaceStorage;
    private HashMap<SurfaceTextureDescription, Texture2D> legendStorage;

    private final DatasetManager manager;

    private final ByteBufferTexture EMPTY_SURFACE_BUFFER;
    private final ByteBufferTexture EMPTY_LEGEND_BUFFER;

    private final int width;
    private final int height;
    private final int surfaceMultiTexUnit;
    private final int legendMultiTexUnit;

    public EfficientTextureStorage(DatasetManager manager, int screens, int width, int height, int surfaceMultiTexUnit,
            int legendMultiTexUnit) {
        this.width = width;
        this.height = height;
        this.surfaceMultiTexUnit = surfaceMultiTexUnit;
        this.legendMultiTexUnit = legendMultiTexUnit;

        oldScreenA = new SurfaceTextureDescription[screens];
        newScreenA = new SurfaceTextureDescription[screens];

        surfaceStorage = new HashMap<SurfaceTextureDescription, Texture2D>();
        legendStorage = new HashMap<SurfaceTextureDescription, Texture2D>();

        this.manager = manager;

        ByteBuffer surfaceBuffer = Buffers.newDirectByteBuffer(width * height * 4);
        ByteBuffer legendBuffer = Buffers.newDirectByteBuffer(LEGEND_TEXTURE_WIDTH * LEGEND_TEXTURE_HEIGHT * 4);

        EMPTY_SURFACE_BUFFER = new ByteBufferTexture(surfaceMultiTexUnit, surfaceBuffer, width, height);

        EMPTY_LEGEND_BUFFER = new ByteBufferTexture(legendMultiTexUnit, legendBuffer, LEGEND_TEXTURE_WIDTH,
                LEGEND_TEXTURE_HEIGHT);

        logger.debug("Texture storage initialization, size: " + width + "x" + height);
    }

    public synchronized Texture2D getSurfaceImage(int screenNumber) {
        if (screenNumber < 0 || screenNumber > oldScreenA.length - 1) {
            logger.error("Get request for screen number out of range: " + screenNumber);
        }

        Texture2D result = null;
        if (newScreenA[screenNumber] != null) {
            SurfaceTextureDescription newDesc = newScreenA[screenNumber];

            if (surfaceStorage.containsKey(newDesc)) {
                result = surfaceStorage.get(newDesc);
            } else {
                result = surfaceStorage.get(oldScreenA[screenNumber]);
            }
        }

        if (result != null) {
            return result;
        } else {
            return EMPTY_SURFACE_BUFFER;
        }

    }

    public synchronized Texture2D getLegendImage(int screenNumber) {
        if (screenNumber < 0 || screenNumber > oldScreenA.length - 1) {
            logger.error("Get request for legend number out of range: " + screenNumber);
        }

        Texture2D result = null;
        if (newScreenA[screenNumber] != null) {
            SurfaceTextureDescription newDesc = newScreenA[screenNumber];

            if (legendStorage.containsKey(newDesc)) {
                result = legendStorage.get(newDesc);
            } else {
                result = legendStorage.get(oldScreenA[screenNumber]);
            }
        }

        if (result != null) {
            return result;
        } else {
            return EMPTY_LEGEND_BUFFER;
        }
    }

    public synchronized List<Texture2D> requestNewConfiguration(int screenNumber, SurfaceTextureDescription newDesc) {
        // Make a list of all the now unused textures, so we can return them to
        // be removed from the GPU memory.
        List<Texture2D> oldTextures = new ArrayList<Texture2D>();

        if (screenNumber < 0 || screenNumber > oldScreenA.length - 1) {
            logger.error("Configuration request for screen number out of range: " + screenNumber);
        }

        SurfaceTextureDescription oldDesc = newScreenA[screenNumber];
        oldScreenA[screenNumber] = oldDesc;
        newScreenA[screenNumber] = newDesc;

        // Do some checking to see if the buffers are in sync
        if (surfaceStorage.containsValue(newDesc) && !legendStorage.containsValue(newDesc)) {
            Texture2D surface = surfaceStorage.remove(newDesc);
            oldTextures.add(surface);
        }
        if (legendStorage.containsValue(newDesc) && !surfaceStorage.containsValue(newDesc)) {
            Texture2D legend = legendStorage.remove(newDesc);
            oldTextures.add(legend);
        }

        // Check if there are textures in the storage that are unused, and only
        // add the new ones to the new lists.
        ArrayList<SurfaceTextureDescription> usedDescs = new ArrayList<SurfaceTextureDescription>();
        for (int i = 0; i < oldScreenA.length; i++) {
            usedDescs.add(oldScreenA[i]);
            usedDescs.add(newScreenA[i]);
        }

        HashMap<SurfaceTextureDescription, Texture2D> newSurfaceStore = new HashMap<SurfaceTextureDescription, Texture2D>();
        for (SurfaceTextureDescription storedSurfaceDesc : surfaceStorage.keySet()) {
            if (usedDescs.contains(storedSurfaceDesc)) {
                newSurfaceStore.put(storedSurfaceDesc, surfaceStorage.get(storedSurfaceDesc));
            }
        }

        HashMap<SurfaceTextureDescription, Texture2D> newLegendStore = new HashMap<SurfaceTextureDescription, Texture2D>();
        for (Map.Entry<SurfaceTextureDescription, Texture2D> entry : legendStorage.entrySet()) {
            SurfaceTextureDescription key = entry.getKey();
            Texture2D value = entry.getValue();
            if (usedDescs.contains(key)) {
                newLegendStore.put(key, value);
            }
        }

        // Now, add all of the unused ones to the to-be-removed list
        for (SurfaceTextureDescription key : surfaceStorage.keySet()) {
            Texture2D value = surfaceStorage.get(key);
            if (!newSurfaceStore.containsKey(key)) {
                oldTextures.add(value);
            }
        }
        for (SurfaceTextureDescription key : legendStorage.keySet()) {
            Texture2D value = legendStorage.get(key);
            if (!newLegendStore.containsKey(key)) {
                oldTextures.add(value);
            }
        }

        // And overwrite the old datastores with the new ones.
        surfaceStorage = newSurfaceStore;
        legendStorage = newLegendStore;

        if (!surfaceStorage.containsValue(newDesc) && !legendStorage.containsValue(newDesc)) {
            logger.debug("requesting: " + newDesc.getVarName());
            manager.buildImages(newDesc);
        }

        return oldTextures;
    }

    public synchronized void setSurfaceImage(SurfaceTextureDescription desc, ByteBuffer data) {
        boolean failure = true;

        // Only add this surface texture if it is still needed.
        for (int i = 0; i < newScreenA.length; i++) {
            if (newScreenA[i] == desc) {
                failure = false;
            }
        }

        if (!failure) {
            surfaceStorage.put(desc, new ByteBufferTexture(surfaceMultiTexUnit, data, width, height));
        }
    }

    public synchronized void setLegendImage(SurfaceTextureDescription desc, ByteBuffer data) {
        boolean failure = true;
        // Only add this legend texture if it is still needed.
        for (int i = 0; i < newScreenA.length; i++) {
            if (newScreenA[i] == desc) {
                failure = false;
            }
        }

        if (!failure) {
            legendStorage.put(desc, new ByteBufferTexture(legendMultiTexUnit, data, LEGEND_TEXTURE_WIDTH,
                    LEGEND_TEXTURE_HEIGHT));
        }
    }

    public boolean doneWithLastRequest() {
        boolean failure = false;

        for (SurfaceTextureDescription desc : newScreenA) {
            if (surfaceStorage.get(desc) == null || legendStorage.get(desc) == null) {
                failure = true;
            }
        }

        return !failure;
    }
}
