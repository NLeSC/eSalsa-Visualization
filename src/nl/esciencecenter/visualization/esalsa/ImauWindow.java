package nl.esciencecenter.visualization.esalsa;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

import javax.media.opengl.GL;
import javax.media.opengl.GL3;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLContext;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLException;

import nl.esciencecenter.neon.datastructures.FrameBufferObject;
import nl.esciencecenter.neon.datastructures.IntPixelBufferObject;
import nl.esciencecenter.neon.exceptions.CompilationFailedException;
import nl.esciencecenter.neon.exceptions.UninitializedException;
import nl.esciencecenter.neon.input.InputHandler;
import nl.esciencecenter.neon.math.Color4;
import nl.esciencecenter.neon.math.Float2Vector;
import nl.esciencecenter.neon.math.Float3Vector;
import nl.esciencecenter.neon.math.Float4Matrix;
import nl.esciencecenter.neon.math.Float4Vector;
import nl.esciencecenter.neon.math.FloatMatrixMath;
import nl.esciencecenter.neon.math.Point4;
import nl.esciencecenter.neon.models.GeoSphere;
import nl.esciencecenter.neon.models.Model;
import nl.esciencecenter.neon.models.Quad;
import nl.esciencecenter.neon.shaders.ShaderProgram;
import nl.esciencecenter.neon.shaders.ShaderProgramLoader;
import nl.esciencecenter.neon.text.MultiColorText;
import nl.esciencecenter.neon.text.jogampexperimental.Font;
import nl.esciencecenter.neon.text.jogampexperimental.FontFactory;
import nl.esciencecenter.visualization.esalsa.data.DatasetNotFoundException;
import nl.esciencecenter.visualization.esalsa.data.SurfaceTextureDescription;
import nl.esciencecenter.visualization.esalsa.data.TextureStorage.TextureCombo;
import nl.esciencecenter.visualization.esalsa.data.TimedPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImauWindow implements GLEventListener {
    private final static Logger logger = LoggerFactory.getLogger(ImauWindow.class);
    private final ImauSettings settings = ImauSettings.getInstance();

    private Quad fsq;

    protected final ShaderProgramLoader loader;
    protected final InputHandler inputHandler;

    private ShaderProgram shaderProgram_Sphere, shaderProgram_Legend, shaderProgram_Atmosphere,
            shaderProgram_FlattenLayers, shaderProgram_PostProcess, shaderProgram_Text;

    private Model sphereModel, legendModel, atmModel;

    private FrameBufferObject atmosphereFBO, hudTextFBO, legendTextureFBO, sphereTextureFBO;

    private IntPixelBufferObject finalPBO;

    private final BufferedImage currentImage = null;

    private final int fontSize = 42;

    private boolean reshaped = false;

    private SurfaceTextureDescription[] cachedTextureDescriptions;
    private FrameBufferObject[] cachedFBOs;
    private MultiColorText[] varNames;
    private MultiColorText[] legendTextsMin;
    private MultiColorText[] legendTextsMax;
    private MultiColorText[] dates;
    private MultiColorText[] dataSets;

    private int cachedScreens = 1;

    private TimedPlayer timer;
    private float aspect;

    protected int fontSet = FontFactory.UBUNTU;
    protected Font font;
    protected int canvasWidth, canvasHeight;

    protected final float radius = 1.0f;
    protected final float ftheta = 0.0f;
    protected final float phi = 0.0f;

    protected final float fovy = 45.0f;
    protected final float zNear = 0.1f;
    protected final float zFar = 3000.0f;

    private final Texture2D[] cachedSurfaceTextures;
    private final Texture2D[] cachedLegendTextures;

    private final float[] topTexCoords;
    private final float[] bottomTexCoords;

    public ImauWindow(InputHandler inputHandler) {
        this.loader = new ShaderProgramLoader();
        this.inputHandler = inputHandler;
        this.font = FontFactory.get(fontSet).getDefault();

        cachedScreens = settings.getNumScreensRows() * settings.getNumScreensCols();

        cachedTextureDescriptions = new SurfaceTextureDescription[cachedScreens];
        cachedFBOs = new FrameBufferObject[cachedScreens];
        varNames = new MultiColorText[cachedScreens];
        legendTextsMin = new MultiColorText[cachedScreens];
        legendTextsMax = new MultiColorText[cachedScreens];
        dates = new MultiColorText[cachedScreens];
        dataSets = new MultiColorText[cachedScreens];

        cachedSurfaceTextures = new Texture2D[cachedScreens];
        cachedLegendTextures = new Texture2D[cachedScreens];

        topTexCoords = new float[cachedScreens];
        bottomTexCoords = new float[cachedScreens];
    }

    public static void contextOn(GLAutoDrawable drawable) {
        try {
            final int status = drawable.getContext().makeCurrent();
            if ((status != GLContext.CONTEXT_CURRENT) && (status != GLContext.CONTEXT_CURRENT_NEW)) {
                System.err.println("Error swapping context to onscreen.");
            }
        } catch (final GLException e) {
            System.err.println("Exception while swapping context to onscreen.");
            e.printStackTrace();
        }
    }

    public static void contextOff(GLAutoDrawable drawable) {
        // Release the context.
        try {
            drawable.getContext().release();
        } catch (final GLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        contextOn(drawable);

        final GL3 gl = drawable.getContext().getGL().getGL3();

        // Check if the shape is still correct (if we have missed a reshape
        // event this might not be the case).
        if (canvasWidth != GLContext.getCurrent().getGLDrawable().getWidth()
                || canvasHeight != GLContext.getCurrent().getGLDrawable().getHeight()) {
            doReshape(gl);
        }

        TimedPlayer timer = ImauPanel.getTimer();
        if (timer.isInitialized()) {
            this.timer = timer;

            Float2Vector clickCoords = null;

            int currentScreens = settings.getNumScreensRows() * settings.getNumScreensCols();
            if (currentScreens != cachedScreens) {
                initDatastores(gl);
            }

            try {
                displayContext(timer, clickCoords);
            } catch (DatasetNotFoundException e) {
                logger.error(e.getMessage());
                e.printStackTrace();
            }
        }

        if (timer.isScreenshotNeeded()) {
            finalPBO.makeScreenshotPNG(gl, timer.getScreenshotFileName());

            timer.setScreenshotNeeded(false);
        }

        reshaped = false;

        contextOff(drawable);
    }

    private void displayContext(TimedPlayer timer, Float2Vector clickCoords) throws DatasetNotFoundException {
        final int width = GLContext.getCurrent().getGLDrawable().getWidth();
        final int height = GLContext.getCurrent().getGLDrawable().getHeight();
        aspect = (float) width / (float) height;

        final GL3 gl = GLContext.getCurrentGL().getGL3();

        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);

        final Point4 eye = new Point4((float) (radius * Math.sin(ftheta) * Math.cos(phi)), (float) (radius
                * Math.sin(ftheta) * Math.sin(phi)), (float) (radius * Math.cos(ftheta)));
        final Point4 at = new Point4(0.0f, 0.0f, 0.0f);
        final Float4Vector up = new Float4Vector(0.0f, 1.0f, 0.0f, 0.0f);

        Float4Matrix mv = FloatMatrixMath.lookAt(eye, at, up);
        mv = mv.mul(FloatMatrixMath.translate(new Float3Vector(0f, 0f, inputHandler.getViewDist())));
        mv = mv.mul(FloatMatrixMath.rotationX(inputHandler.getRotation().getX()));
        mv = mv.mul(FloatMatrixMath.rotationY(inputHandler.getRotation().getY()));

        drawAtmosphere(gl, mv, atmosphereFBO);

        if (settings.isRequestedNewConfiguration()) {
            SurfaceTextureDescription currentDesc;

            boolean allRequestsFullfilled = true;
            for (int i = 0; i < cachedScreens; i++) {
                // Get the currently needed description from the settings
                // manager
                currentDesc = settings.getSurfaceDescription(i);

                if (currentDesc != null) {
                    // Ask the TextureStorage for the currently displayed/ready
                    // image
                    TextureCombo result = timer.getTextureStorage(currentDesc.getVarName()).getImages(i);

                    if (result.getDescription() != currentDesc) {
                        // Check if we need to request new images, or if we are
                        // waiting for new images
                        if (!timer.getTextureStorage(currentDesc.getVarName()).isRequested(currentDesc)) {
                            // We need to request new ones
                            logger.debug("requesting: " + currentDesc.toString());

                            List<Texture2D> oldTextures = timer.getTextureStorage(currentDesc.getVarName())
                                    .requestNewConfiguration(i, currentDesc);
                            // Remove all of the (now unused) textures
                            for (Texture2D tex : oldTextures) {
                                try {
                                    tex.delete(gl);
                                } catch (UninitializedException e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }
                            }
                        }
                        // We are waiting for images to be generated
                        allRequestsFullfilled = false;
                    } else {
                        // We might have received a new request here
                        if (cachedSurfaceTextures[i] != result.getSurfaceTexture()
                                || cachedLegendTextures[i] != result.getLegendTexture()) {
                            logger.debug("adding new texture for screen " + i + " to opengl: " + currentDesc);

                            // Apparently a new image was just created for us,
                            // so lets store it
                            cachedSurfaceTextures[i] = result.getSurfaceTexture();
                            cachedLegendTextures[i] = result.getLegendTexture();

                            cachedSurfaceTextures[i].init(gl);
                            cachedLegendTextures[i].init(gl);

                            topTexCoords[i] = result.getTopTexCoords();
                            bottomTexCoords[i] = result.getBottomTexCoords();

                            // And set the appropriate text to accompany it.
                            String variableName = currentDesc.getVarName();
                            String fancyName = variableName;
                            varNames[i].setString(gl, fancyName, Color4.WHITE, fontSize);

                            String min, max;
                            if (currentDesc.isDiff()) {
                                min = Float.toString(settings.getCurrentVarDiffMin(currentDesc.getVarName()));
                                max = Float.toString(settings.getCurrentVarDiffMax(currentDesc.getVarName()));
                            } else {
                                min = Float.toString(settings.getCurrentVarMin(currentDesc.getVarName()));
                                max = Float.toString(settings.getCurrentVarMax(currentDesc.getVarName()));
                            }
                            dates[i].setString(gl, settings.getFancyDate(currentDesc.getFrameNumber()), Color4.WHITE,
                                    fontSize);
                            dataSets[i].setString(gl, currentDesc.verbalizeDataMode(), Color4.WHITE, fontSize);
                            legendTextsMin[i].setString(gl, min, Color4.WHITE, fontSize);
                            legendTextsMax[i].setString(gl, max, Color4.WHITE, fontSize);
                        }
                    }
                }
            }

            if (allRequestsFullfilled) {
                settings.setRequestedNewConfiguration(false);
            }
        }

        for (int i = 0; i < cachedScreens; i++) {
            if (cachedLegendTextures[i] != null && cachedSurfaceTextures[i] != null) {
                drawSingleWindow(width, height, gl, mv, cachedLegendTextures[i], cachedSurfaceTextures[i], varNames[i],
                        dates[i], dataSets[i], legendTextsMin[i], legendTextsMax[i], cachedFBOs[i], clickCoords,
                        topTexCoords[i], bottomTexCoords[i]);
            }
        }
        // logger.debug("Tiling windows");
        renderTexturesToScreen(gl, width, height);
    }

    private void drawSingleWindow(final int width, final int height, final GL3 gl, Float4Matrix mv, Texture2D legend,
            Texture2D globe, MultiColorText varNameText, MultiColorText dateText, MultiColorText datasetText,
            MultiColorText legendTextMin, MultiColorText legendTextMax, FrameBufferObject target,
            Float2Vector clickCoords, float topTexCoord, float bottomTexCoord) {
        // logger.debug("Drawing Text");
        drawHUDText(gl, width, height, varNameText, dateText, datasetText, legendTextMin, legendTextMax, hudTextFBO);

        // logger.debug("Drawing HUD");
        drawHUDLegend(gl, width, height, legend, legendTextureFBO);

        // logger.debug("Drawing Sphere");
        drawSphere(gl, mv, globe, sphereTextureFBO, clickCoords, topTexCoord, bottomTexCoord);

        // logger.debug("Flattening Layers");
        flattenLayers(gl, hudTextFBO, legendTextureFBO, sphereTextureFBO, atmosphereFBO, target);
    }

    private void drawHUDText(GL3 gl, int width, int height, MultiColorText varNameText, MultiColorText dateText,
            MultiColorText datasetText, MultiColorText legendTextMin, MultiColorText legendTextMax,
            FrameBufferObject target) {
        try {
            target.bind(gl);
            gl.glClear(GL.GL_DEPTH_BUFFER_BIT | GL.GL_COLOR_BUFFER_BIT);

            // Draw text
            int textLength = varNameText.toString().length() * fontSize;

            varNameText.drawHudRelative(gl, shaderProgram_Text, width, height, 2 * width - textLength - 150, 40);

            textLength = datasetText.toString().length() * fontSize;
            datasetText.drawHudRelative(gl, shaderProgram_Text, width, height, 10, 1.9f * height);

            textLength = dateText.toString().length() * fontSize;
            dateText.drawHudRelative(gl, shaderProgram_Text, width, height, 10, 40);

            textLength = legendTextMin.toString().length() * fontSize;
            legendTextMin.drawHudRelative(gl, shaderProgram_Text, width, height, 2 * width - textLength - 100,
                    .2f * height);

            textLength = legendTextMax.toString().length() * fontSize;
            legendTextMax.drawHudRelative(gl, shaderProgram_Text, width, height, 2 * width - textLength - 100,
                    1.75f * height);

            target.unBind(gl);
        } catch (UninitializedException e) {
            e.printStackTrace();
        }
    }

    private void drawHUDLegend(GL3 gl, int width, int height, Texture2D legendTexture, FrameBufferObject target) {
        try {
            target.bind(gl);
            gl.glClear(GL.GL_DEPTH_BUFFER_BIT | GL.GL_COLOR_BUFFER_BIT);

            // Draw legend texture
            legendTexture.use(gl);
            shaderProgram_Legend.setUniform("top_texCoord", 1f);
            shaderProgram_Legend.setUniform("bottom_texCoord", 0f);
            shaderProgram_Legend.setUniform("texture_map", legendTexture.getMultitexNumber());
            shaderProgram_Legend.setUniformMatrix("MVMatrix", new Float4Matrix());
            shaderProgram_Legend.setUniformMatrix("PMatrix", new Float4Matrix());

            shaderProgram_Legend.use(gl);
            legendModel.draw(gl, shaderProgram_Legend);

            target.unBind(gl);
        } catch (UninitializedException e) {
            e.printStackTrace();
        }
    }

    private void drawSphere(GL3 gl, Float4Matrix mv, Texture2D surfaceTexture, FrameBufferObject target,
            Float2Vector clickCoords, float topTexCoord, float bottomTexCoord) {
        try {
            target.bind(gl);
            gl.glClear(GL.GL_DEPTH_BUFFER_BIT | GL.GL_COLOR_BUFFER_BIT);

            final Float4Matrix p = FloatMatrixMath.perspective(fovy, aspect, zNear, zFar);

            shaderProgram_Sphere.setUniformMatrix("MVMatrix", new Float4Matrix(mv));
            shaderProgram_Sphere.setUniformMatrix("PMatrix", p);

            surfaceTexture.use(gl);
            shaderProgram_Sphere.setUniform("top_texCoord", topTexCoord);
            shaderProgram_Sphere.setUniform("bottom_texCoord", bottomTexCoord);
            shaderProgram_Sphere.setUniform("texture_map", surfaceTexture.getMultitexNumber());

            shaderProgram_Sphere.use(gl);
            sphereModel.draw(gl, shaderProgram_Sphere);

            target.unBind(gl);
        } catch (UninitializedException e) {
            e.printStackTrace();
        }
    }

    private void drawAtmosphere(GL3 gl, Float4Matrix mv, FrameBufferObject target) {
        try {
            target.bind(gl);
            gl.glClear(GL.GL_DEPTH_BUFFER_BIT | GL.GL_COLOR_BUFFER_BIT);

            final Float4Matrix p = FloatMatrixMath.perspective(fovy, aspect, zNear, zFar);
            shaderProgram_Atmosphere.setUniformMatrix("MVMatrix", new Float4Matrix(mv));
            shaderProgram_Atmosphere.setUniformMatrix("PMatrix", p);
            shaderProgram_Atmosphere.setUniformMatrix("NormalMatrix", FloatMatrixMath.getNormalMatrix(mv));

            shaderProgram_Atmosphere.use(gl);
            atmModel.draw(gl, shaderProgram_Atmosphere);

            target.unBind(gl);
        } catch (UninitializedException e) {
            e.printStackTrace();
        }
    }

    private void flattenLayers(GL3 gl, FrameBufferObject hudTextFBO, FrameBufferObject hudLegendFBO,
            FrameBufferObject sphereTextureFBO, FrameBufferObject atmosphereFBO, FrameBufferObject target) {
        try {
            target.bind(gl);
            gl.glClear(GL.GL_DEPTH_BUFFER_BIT | GL.GL_COLOR_BUFFER_BIT);

            shaderProgram_FlattenLayers.setUniform("textTex", hudTextFBO.getTexture().getMultitexNumber());
            shaderProgram_FlattenLayers.setUniform("legendTex", hudLegendFBO.getTexture().getMultitexNumber());
            shaderProgram_FlattenLayers.setUniform("dataTex", sphereTextureFBO.getTexture().getMultitexNumber());
            shaderProgram_FlattenLayers.setUniform("atmosphereTex", atmosphereFBO.getTexture().getMultitexNumber());

            shaderProgram_FlattenLayers.setUniformMatrix("MVMatrix", new Float4Matrix());
            shaderProgram_FlattenLayers.setUniformMatrix("PMatrix", new Float4Matrix());

            shaderProgram_FlattenLayers.setUniform("scrWidth", canvasWidth);
            shaderProgram_FlattenLayers.setUniform("scrHeight", canvasHeight);

            shaderProgram_FlattenLayers.use(gl);
            fsq.draw(gl, shaderProgram_FlattenLayers);

            target.unBind(gl);
        } catch (final UninitializedException e) {
            e.printStackTrace();
        }
    }

    public void renderTexturesToScreen(GL3 gl, int width, int height) {
        try {
            gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);

            for (int i = 0; i < cachedScreens; i++) {
                shaderProgram_PostProcess.setUniform("sphereTexture_" + i, cachedFBOs[i].getTexture()
                        .getMultitexNumber());
            }

            shaderProgram_PostProcess.setUniformMatrix("MVMatrix", new Float4Matrix());
            shaderProgram_PostProcess.setUniformMatrix("PMatrix", new Float4Matrix());

            shaderProgram_PostProcess.setUniform("scrWidth", width);
            shaderProgram_PostProcess.setUniform("scrHeight", height);

            int selection = settings.getWindowSelection();

            shaderProgram_PostProcess.setUniform("divs_x", settings.getNumScreensCols());
            shaderProgram_PostProcess.setUniform("divs_y", settings.getNumScreensRows());
            shaderProgram_PostProcess.setUniform("selection", selection);

            shaderProgram_PostProcess.use(gl);
            fsq.draw(gl, shaderProgram_PostProcess);
        } catch (final UninitializedException e) {
            e.printStackTrace();
        }
    }

    private void initDatastores(GL3 gl) {
        cachedScreens = settings.getNumScreensRows() * settings.getNumScreensCols();

        cachedTextureDescriptions = new SurfaceTextureDescription[cachedScreens];
        cachedFBOs = new FrameBufferObject[cachedScreens];
        varNames = new MultiColorText[cachedScreens];
        legendTextsMin = new MultiColorText[cachedScreens];
        legendTextsMax = new MultiColorText[cachedScreens];
        dates = new MultiColorText[cachedScreens];
        dataSets = new MultiColorText[cachedScreens];

        for (int i = 0; i < cachedScreens; i++) {
            cachedTextureDescriptions[i] = settings.getSurfaceDescription(i);

            if (cachedFBOs[i] != null) {
                cachedFBOs[i].delete(gl);
            }
            cachedFBOs[i] = new FrameBufferObject(canvasWidth, canvasHeight, (GL.GL_TEXTURE6 + i));
            cachedFBOs[i].init(gl);

            varNames[i] = new MultiColorText(font);
            legendTextsMin[i] = new MultiColorText(font);
            legendTextsMin[i] = new MultiColorText(font);
            legendTextsMax[i] = new MultiColorText(font);
            dates[i] = new MultiColorText(font);
            dataSets[i] = new MultiColorText(font);
        }
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int w, int h) {
        contextOn(drawable);

        GL3 gl = drawable.getGL().getGL3();

        doReshape(gl);

        contextOff(drawable);
    }

    private void doReshape(GL3 gl) {
        canvasWidth = GLContext.getCurrent().getGLDrawable().getWidth();
        canvasHeight = GLContext.getCurrent().getGLDrawable().getHeight();

        gl.glViewport(0, 0, canvasWidth, canvasHeight);

        atmosphereFBO.delete(gl);
        hudTextFBO.delete(gl);
        legendTextureFBO.delete(gl);
        sphereTextureFBO.delete(gl);

        atmosphereFBO = new FrameBufferObject(canvasWidth, canvasHeight, GL.GL_TEXTURE0);
        hudTextFBO = new FrameBufferObject(canvasWidth, canvasHeight, GL.GL_TEXTURE1);
        legendTextureFBO = new FrameBufferObject(canvasWidth, canvasHeight, GL.GL_TEXTURE2);
        sphereTextureFBO = new FrameBufferObject(canvasWidth, canvasHeight, GL.GL_TEXTURE3);

        atmosphereFBO.init(gl);
        hudTextFBO.init(gl);
        legendTextureFBO.init(gl);
        sphereTextureFBO.init(gl);

        initDatastores(gl);

        finalPBO.delete(gl);
        finalPBO = new IntPixelBufferObject(canvasWidth, canvasHeight);
        finalPBO.init(gl);

        reshaped = true;
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
        contextOn(drawable);

        final GL3 gl = drawable.getGL().getGL3();

        timer.stop();
        try {
            loader.cleanup(gl);
        } catch (UninitializedException e) {
            e.printStackTrace();
        }
        timer.close();

        sphereModel.delete(gl);
        atmModel.delete(gl);
        legendModel.delete(gl);

        for (int i = 0; i < cachedScreens; i++) {
            cachedFBOs[i].delete(gl);
        }

        finalPBO.delete(gl);

        contextOff(drawable);
    }

    @Override
    public void init(GLAutoDrawable drawable) {
        contextOn(drawable);

        canvasWidth = drawable.getWidth();
        canvasHeight = drawable.getHeight();

        // First, init the 'normal' context
        GL3 gl = drawable.getGL().getGL3();

        // Anti-Aliasing
        gl.glEnable(GL3.GL_LINE_SMOOTH);
        gl.glHint(GL3.GL_LINE_SMOOTH_HINT, GL3.GL_NICEST);
        gl.glEnable(GL3.GL_POLYGON_SMOOTH);
        gl.glHint(GL3.GL_POLYGON_SMOOTH_HINT, GL3.GL_NICEST);

        // Depth testing
        gl.glEnable(GL3.GL_DEPTH_TEST);
        gl.glDepthFunc(GL3.GL_LEQUAL);
        gl.glClearDepth(1.0f);

        // Culling
        gl.glEnable(GL3.GL_CULL_FACE);
        gl.glCullFace(GL3.GL_BACK);

        // Enable Blending (needed for both Transparency and
        // Anti-Aliasing
        gl.glBlendFunc(GL3.GL_SRC_ALPHA, GL3.GL_ONE_MINUS_SRC_ALPHA);
        gl.glEnable(GL3.GL_BLEND);

        // Enable Vertical Sync
        gl.setSwapInterval(1);

        // Set black background
        gl.glClearColor(0f, 0f, 0f, 0f);

        logger.debug("W: " + canvasWidth + ", H: " + canvasHeight);

        atmosphereFBO = new FrameBufferObject(canvasWidth, canvasHeight, GL.GL_TEXTURE0);
        hudTextFBO = new FrameBufferObject(canvasWidth, canvasHeight, GL.GL_TEXTURE1);
        legendTextureFBO = new FrameBufferObject(canvasWidth, canvasHeight, GL.GL_TEXTURE2);
        sphereTextureFBO = new FrameBufferObject(canvasWidth, canvasHeight, GL.GL_TEXTURE3);

        atmosphereFBO.init(gl);
        hudTextFBO.init(gl);
        legendTextureFBO.init(gl);
        sphereTextureFBO.init(gl);

        fsq = new Quad(2, 2, new Float3Vector(0, 0, 0.1f));
        fsq.init(gl);

        sphereModel = new GeoSphere(60, 60, 50f, false);
        sphereModel.init(gl);

        legendModel = new Quad(1.5f, .1f, new Float3Vector(1, 0, 0.1f));
        legendModel.init(gl);

        atmModel = new GeoSphere(60, 60, 53f, false);
        atmModel.init(gl);

        initDatastores(gl);

        inputHandler.setViewDist(-130f);

        try {
            shaderProgram_Sphere = loader.createProgram(gl, "shaderProgram_Sphere", new File("shaders/vs_texture.vp"),
                    new File("shaders/fs_texture.fp"));

            shaderProgram_Legend = loader.createProgram(gl, "shaderProgram_Legend", new File("shaders/vs_texture.vp"),
                    new File("shaders/fs_texture.fp"));

            shaderProgram_Text = loader.createProgram(gl, "shaderProgram_Text", new File(
                    "shaders/vs_multiColorTextShader.vp"), new File("shaders/fs_multiColorTextShader.fp"));

            shaderProgram_Atmosphere = loader.createProgram(gl, "shaderProgram_Atmosphere", new File(
                    "shaders/vs_atmosphere.vp"), new File("shaders/fs_atmosphere.fp"));

            shaderProgram_PostProcess = loader.createProgram(gl, "shaderProgram_PostProcess", new File(
                    "shaders/vs_postprocess.vp"), new File("shaders/fs_postprocess.fp"));

            shaderProgram_FlattenLayers = loader.createProgram(gl, "shaderProgram_FlattenLayers", new File(
                    "shaders/vs_flatten3.vp"), new File("shaders/fs_flatten3.fp"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (CompilationFailedException e) {
            e.printStackTrace();
        }

        finalPBO = new IntPixelBufferObject(canvasWidth, canvasHeight);
        finalPBO.init(gl);

        contextOff(drawable);
    }

    public void makeSnapshot() {
        if (timer != null) {
            timer.setScreenshotNeeded(true);
        }
    }

    public BufferedImage getScreenshot() {
        BufferedImage frame = ImauApp.getFrameImage();

        int frameWidth = frame.getWidth();
        int frameHeight = frame.getHeight();

        int[] frameRGB = new int[frameWidth * frameHeight];
        frame.getRGB(0, 0, frameWidth, frameHeight, frameRGB, 0, frameWidth);

        int glWidth = currentImage.getWidth();
        int glHeight = currentImage.getHeight();

        int[] glRGB = new int[glWidth * glHeight];
        currentImage.getRGB(0, 0, glWidth, glHeight, glRGB, 0, glWidth);

        Point p = ImauApp.getCanvaslocation();

        for (int y = p.y; y < p.y + glHeight; y++) {
            int offset = (y - p.y) * glWidth;
            System.arraycopy(glRGB, offset, frameRGB, y * frameWidth + p.x, glWidth);
        }

        BufferedImage result = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);

        result.setRGB(0, 0, result.getWidth(), result.getHeight(), frameRGB, 0, result.getWidth());

        return result;
    }

    public InputHandler getInputHandler() {
        return inputHandler;
    }
}
