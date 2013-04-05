package nl.esciencecenter.visualization.esalsa.data;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jogamp.common.nio.Buffers;

public class TextureStorage {
    private final static Logger                            logger = LoggerFactory
                                                                          .getLogger(TextureStorage.class);

    private final SurfaceTextureDescription[]              oldScreenA;
    private final SurfaceTextureDescription[]              newScreenA;
    private HashMap<SurfaceTextureDescription, ByteBuffer> surfaceStorage;
    private HashMap<SurfaceTextureDescription, ByteBuffer> legendStorage;

    private final ImauDatasetManager                       manager;

    private final ByteBuffer                               EMPTY_SURFACE_BUFFER;
    private final ByteBuffer                               EMPTY_LEGEND_BUFFER;

    public TextureStorage(ImauDatasetManager manager, int screens, int width,
            int height) {
        oldScreenA = new SurfaceTextureDescription[screens];
        newScreenA = new SurfaceTextureDescription[screens];

        surfaceStorage = new HashMap<SurfaceTextureDescription, ByteBuffer>();
        legendStorage = new HashMap<SurfaceTextureDescription, ByteBuffer>();

        this.manager = manager;

        EMPTY_SURFACE_BUFFER = Buffers.newDirectByteBuffer(width * height * 4);
        EMPTY_LEGEND_BUFFER = Buffers.newDirectByteBuffer(1 * 500 * 4);
    }

    public synchronized ByteBuffer getSurfaceImage(int screenNumber) {
        if (screenNumber < 0 || screenNumber > oldScreenA.length - 1) {
            logger.error("Get request for screen number out of range: "
                    + screenNumber);
        }

        ByteBuffer result = null;
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

    public synchronized ByteBuffer getLegendImage(int screenNumber) {
        if (screenNumber < 0 || screenNumber > oldScreenA.length - 1) {
            logger.error("Get request for legend number out of range: "
                    + screenNumber);
        }

        ByteBuffer result = null;
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

    public synchronized void requestNewConfiguration(int screenNumber,
            SurfaceTextureDescription newDesc) {
        if (screenNumber < 0 || screenNumber > oldScreenA.length - 1) {
            logger.error("Configuration request for screen number out of range: "
                    + screenNumber);
        }

        SurfaceTextureDescription oldDesc = newScreenA[screenNumber];
        oldScreenA[screenNumber] = oldDesc;

        newScreenA[screenNumber] = newDesc;

        // Do some checking to see if the buffers are in sync
        if (surfaceStorage.containsValue(newDesc)
                && !legendStorage.containsValue(newDesc)) {
            surfaceStorage.remove(newDesc);
        }
        if (legendStorage.containsValue(newDesc)
                && !surfaceStorage.containsValue(newDesc)) {
            legendStorage.remove(newDesc);
        }

        // Check if there are textures in the storage that are unused, and
        // remove them if so
        ArrayList<SurfaceTextureDescription> usedDescs = new ArrayList<SurfaceTextureDescription>();
        for (int i = 0; i < oldScreenA.length; i++) {
            usedDescs.add(oldScreenA[i]);
            usedDescs.add(newScreenA[i]);
        }

        HashMap<SurfaceTextureDescription, ByteBuffer> newSurfaceStore = new HashMap<SurfaceTextureDescription, ByteBuffer>();
        for (SurfaceTextureDescription storedSurfaceDesc : surfaceStorage
                .keySet()) {
            if (usedDescs.contains(storedSurfaceDesc)) {
                newSurfaceStore.put(storedSurfaceDesc,
                        surfaceStorage.get(storedSurfaceDesc));
            }
        }
        surfaceStorage = newSurfaceStore;

        HashMap<SurfaceTextureDescription, ByteBuffer> newLegendStore = new HashMap<SurfaceTextureDescription, ByteBuffer>();
        for (Map.Entry<SurfaceTextureDescription, ByteBuffer> entry : legendStorage
                .entrySet()) {
            SurfaceTextureDescription key = entry.getKey();
            ByteBuffer value = entry.getValue();
            if (usedDescs.contains(key)) {
                newLegendStore.put(key, value);
            }
        }
        legendStorage = newLegendStore;

        if (!surfaceStorage.containsValue(newDesc)
                && !legendStorage.containsValue(newDesc)) {
            manager.buildImages(newDesc);
        }
    }

    public synchronized void setSurfaceImage(SurfaceTextureDescription desc,
            ByteBuffer data) {
        boolean failure = true;

        // Only add this surface texture if it is still needed.
        for (int i = 0; i < newScreenA.length; i++) {
            if (newScreenA[i] == desc) {
                failure = false;
            }
        }

        if (!failure) {
            surfaceStorage.put(desc, data);
        }
    }

    public synchronized void setLegendImage(SurfaceTextureDescription desc,
            ByteBuffer data) {
        boolean failure = true;
        // Only add this legend texture if it is still needed.
        for (int i = 0; i < newScreenA.length; i++) {
            if (newScreenA[i] == desc) {
                failure = false;
            }
        }

        if (!failure) {
            legendStorage.put(desc, data);
        }
    }

    public boolean doneWithLastRequest() {
        boolean failure = false;

        for (SurfaceTextureDescription desc : newScreenA) {
            if (surfaceStorage.get(desc) == null
                    || legendStorage.get(desc) == null) {
                failure = true;
            }
        }

        return !failure;
    }
}
