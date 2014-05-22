package nl.esciencecenter.visualization.esalsa.data;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import nl.esciencecenter.visualization.esalsa.ByteBufferTexture;
import nl.esciencecenter.visualization.esalsa.IntArrayTexture;
import nl.esciencecenter.visualization.esalsa.Texture2D;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jogamp.common.nio.Buffers;

public class TextureStorage {
    private static final int LEGEND_TEXTURE_HEIGHT = 500;
    private static final int LEGEND_TEXTURE_WIDTH = 1;

    private final static Logger logger = LoggerFactory.getLogger(TextureStorage.class);

    private final SurfaceTextureDescription[] oldScreenA;
    private final SurfaceTextureDescription[] newScreenA;
    private List<TextureCombo> storage;

    private final DatasetManager manager;

    private final ByteBufferTexture EMPTY_SURFACE_BUFFER;
    private final ByteBufferTexture EMPTY_LEGEND_BUFFER;

    private final int width;
    private final int height;
    private final int surfaceMultiTexUnit;
    private final int legendMultiTexUnit;

    public class TextureCombo {
        private final SurfaceTextureDescription description;
        private final Texture2D surfaceTexture;
        private final Texture2D legendTexture;

        public TextureCombo(SurfaceTextureDescription description, Texture2D surfaceTexture, Texture2D legendTexture) {
            this.description = description;
            this.surfaceTexture = surfaceTexture;
            this.legendTexture = legendTexture;
        }

        public SurfaceTextureDescription getDescription() {
            return description;
        }

        public Texture2D getSurfaceTexture() {
            return surfaceTexture;
        }

        public Texture2D getLegendTexture() {
            return legendTexture;
        }
    }

    public TextureStorage(DatasetManager dsManager, int screens, int width, int height, int surfaceMultiTexUnit,
            int legendMultiTexUnit) {
        this.width = width;
        this.height = height;
        this.surfaceMultiTexUnit = surfaceMultiTexUnit;
        this.legendMultiTexUnit = legendMultiTexUnit;

        oldScreenA = new SurfaceTextureDescription[screens];
        newScreenA = new SurfaceTextureDescription[screens];

        this.manager = dsManager;

        ByteBuffer surfaceBuffer = Buffers.newDirectByteBuffer(width * height * 4);
        ByteBuffer legendBuffer = Buffers.newDirectByteBuffer(LEGEND_TEXTURE_WIDTH * LEGEND_TEXTURE_HEIGHT * 4);

        EMPTY_SURFACE_BUFFER = new ByteBufferTexture(surfaceMultiTexUnit, surfaceBuffer, width, height);

        EMPTY_LEGEND_BUFFER = new ByteBufferTexture(legendMultiTexUnit, legendBuffer, LEGEND_TEXTURE_WIDTH,
                LEGEND_TEXTURE_HEIGHT);

        storage = new ArrayList<TextureCombo>();

        logger.debug("Texture storage initialization, size: " + width + "x" + height);
    }

    public synchronized TextureCombo getImages(int screenNumber) {
        if (screenNumber < 0 || screenNumber > oldScreenA.length - 1) {
            logger.error("Get request for screen number out of range: " + screenNumber);
        }

        if (newScreenA[screenNumber] != null) {
            for (TextureCombo combo : storage) {
                if (newScreenA[screenNumber] == combo.getDescription()) {
                    return combo;
                }
            }
        }

        if (oldScreenA[screenNumber] != null) {
            for (TextureCombo combo : storage) {
                if (oldScreenA[screenNumber] == combo.getDescription()) {
                    return combo;
                }
            }
        }

        return new TextureCombo(null, EMPTY_SURFACE_BUFFER, EMPTY_LEGEND_BUFFER);
    }

    public synchronized boolean isRequested(SurfaceTextureDescription desc) {
        boolean requested = false;

        for (int i = 0; i < newScreenA.length; i++) {
            if (newScreenA[i] == desc) {
                requested = true;
            }
        }

        return requested;
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

        // Copy links to all of the actually used textures into a new list.
        ArrayList<SurfaceTextureDescription> usedDescs = new ArrayList<SurfaceTextureDescription>();
        for (int i = 0; i < oldScreenA.length; i++) {
            usedDescs.add(oldScreenA[i]);
            usedDescs.add(newScreenA[i]);
        }

        List<TextureCombo> newStorage = new ArrayList<TextureCombo>();
        for (TextureCombo combo : storage) {
            if (usedDescs.contains(combo.getDescription())) {
                newStorage.add(combo);
            } else {
                // Add all of the unused ones to the to-be-removed list UNLESS
                // it's the empty buffers
                if (combo.getSurfaceTexture() != EMPTY_SURFACE_BUFFER && combo.getSurfaceTexture().isInitialized()) {
                    oldTextures.add(combo.getSurfaceTexture());
                }

                if (combo.getLegendTexture() != EMPTY_LEGEND_BUFFER && combo.getSurfaceTexture().isInitialized()) {
                    oldTextures.add(combo.getLegendTexture());
                }
            }
        }

        // Overwrite the old list
        storage = newStorage;

        // And start the building of the newly requested images that are not yet
        // available
        boolean alreadyAvailable = false;
        for (TextureCombo combo : storage) {
            if (combo.getDescription() == newDesc) {
                alreadyAvailable = true;
            }
        }

        if (!alreadyAvailable) {
            logger.debug("requesting: " + newDesc.getVarName());
            manager.buildImages(newDesc);
        }

        // stopTimeMillis = System.currentTimeMillis();
        // logger.debug("Request complete: " + (stopTimeMillis -
        // startTimeMillis) / 1000.0);

        logger.debug("tex storage now holds : " + (storage.size()) + " texture combinations, " + oldTextures.size()
                + " textures will be deleted.");

        return oldTextures;
    }

    public synchronized void setImageCombo(SurfaceTextureDescription desc, ByteBuffer surfaceData, ByteBuffer legendData) {
        boolean failure = true;

        // Only add this surface texture if it is still needed.
        for (int i = 0; i < newScreenA.length; i++) {
            if (newScreenA[i] == desc) {
                failure = false;
            }
        }

        if (!failure) {
            storage.add(new TextureCombo(desc, new ByteBufferTexture(surfaceMultiTexUnit, surfaceData, width, height),
                    new ByteBufferTexture(legendMultiTexUnit, legendData, LEGEND_TEXTURE_WIDTH, LEGEND_TEXTURE_HEIGHT)));
        } else {
            logger.error("FAILURE in setImageCombo, " + desc);
        }
    }

    public synchronized void setImageCombo(SurfaceTextureDescription desc, int[] surfaceData, ByteBuffer legendData) {
        boolean failure = true;

        // Only add this surface texture if it is still needed.
        for (int i = 0; i < newScreenA.length; i++) {
            if (newScreenA[i] == desc) {
                failure = false;
            }
        }

        if (!failure) {
            storage.add(new TextureCombo(desc, new IntArrayTexture(surfaceMultiTexUnit, surfaceData, width, height),
                    new ByteBufferTexture(legendMultiTexUnit, legendData, LEGEND_TEXTURE_WIDTH, LEGEND_TEXTURE_HEIGHT)));
        } else {
            logger.error("FAILURE in setImageCombo, " + desc);
        }
    }

    public synchronized boolean doneWithLastRequest() {
        boolean done = true;

        for (SurfaceTextureDescription desc : newScreenA) {
            boolean found = false;
            for (TextureCombo combo : storage) {
                if (combo.getDescription() == desc) {
                    found = true;
                }
            }
            if (found == false) {
                done = false;
            }
        }

        return done;
    }
}
