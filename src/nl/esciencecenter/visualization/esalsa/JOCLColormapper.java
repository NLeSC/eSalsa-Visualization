package nl.esciencecenter.visualization.esalsa;

import static org.jocl.CL.CL_CONTEXT_PLATFORM;
import static org.jocl.CL.CL_DEVICE_TYPE_ALL;
import static org.jocl.CL.CL_MEM_READ_WRITE;
import static org.jocl.CL.CL_MEM_WRITE_ONLY;
import static org.jocl.CL.CL_TRUE;
import static org.jocl.CL.clBuildProgram;
import static org.jocl.CL.clCreateBuffer;
import static org.jocl.CL.clCreateCommandQueue;
import static org.jocl.CL.clCreateContext;
import static org.jocl.CL.clCreateKernel;
import static org.jocl.CL.clCreateProgramWithSource;
import static org.jocl.CL.clEnqueueNDRangeKernel;
import static org.jocl.CL.clEnqueueReadBuffer;
import static org.jocl.CL.clEnqueueWriteBuffer;
import static org.jocl.CL.clGetDeviceIDs;
import static org.jocl.CL.clGetPlatformIDs;
import static org.jocl.CL.clSetKernelArg;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.ImageIcon;
import javax.swing.JComboBox;

import nl.esciencecenter.neon.swing.ColormapInterpreter.Dimensions;
import nl.esciencecenter.neon.swing.ImageComboBoxRenderer;
import nl.esciencecenter.neon.swing.SimpleImageIcon;

import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jogamp.common.nio.Buffers;

/**
 * A class that uses a simple OpenCL kernel to compute a colormapped image
 */
public class JOCLColormapper {
    private final static Logger logger = LoggerFactory.getLogger(JOCLColormapper.class);

    /**
     * Extension filter for the filtering of filenames in directory structures.
     * 
     * @author Maarten van Meersbergen <m.van.meersbergen@esciencecenter.nl>
     * 
     */
    static class ExtFilter implements FilenameFilter {
        private final String ext;

        /**
         * Basic constructor for ExtFilter.
         * 
         * @param ext
         *            The extension to filter for.
         */
        public ExtFilter(String ext) {
            this.ext = ext;
        }

        @Override
        public boolean accept(File dir, String name) {
            return (name.endsWith(ext));
        }
    }

    /** Storage for the colormaps. */
    private static Map<String, int[]> colorMaps;

    /**
     * The OpenCL context
     */
    private cl_context context;

    /**
     * The OpenCL command queue
     */
    private cl_command_queue commandQueue;

    /**
     * The OpenCL kernel which will actually compute the Colormap and store the
     * pixel data in a CL memory object
     */
    private cl_kernel kernel;
    private cl_kernel logKernel;

    /**
     * An OpenCL memory object which stores a nifty color map, encoded as
     * integers combining the RGB components of the colors.
     */
    private cl_mem colorMapMem;

    private class ReservedMemoryConstruct {
        int width, height;

        /**
         * The OpenCL memory object which stores the pixel data
         */
        private final cl_mem outputMem;

        /**
         * The OpenCL memory object which stores the raw data
         */
        private final cl_mem dataMem;

        public ReservedMemoryConstruct(int width, int height, cl_mem output, cl_mem data) {
            this.width = width;
            this.height = height;
            this.dataMem = data;
            this.outputMem = output;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public cl_mem getOutputMem() {
            return outputMem;
        }

        public cl_mem getDataMem() {
            return dataMem;
        }
    }

    private final List<ReservedMemoryConstruct> reservedMemory;

    static {
        // Create and fill the memory object containing the color maps
        rebuildMaps();
    }

    /**
     * Creates the JOCLColormapper with the given width and height
     */
    public JOCLColormapper() {
        reservedMemory = new ArrayList<ReservedMemoryConstruct>();

        // Initialize OpenCL
        initCL();
    }

    /** Storage for the statically built legend images. */
    private static Map<String, Color[][]> legends;
    private static Map<String, ByteBuffer> legendByteBuffers;

    private final static int LEGEND_WIDTH = 150;
    private final static int LEGEND_HEIGHT = 150;
    private final static int COLORMAP_FINAL_ENTRIES = 500;

    /**
     * Initialize OpenCL: Create the context, the command queue and the kernel.
     */
    private void initCL() {
        final int platformIndex = 0;
        final long deviceType = CL_DEVICE_TYPE_ALL;
        final int deviceIndex = 0;

        // Enable exceptions and subsequently omit error checks in this sample
        CL.setExceptionsEnabled(true);

        // Obtain the number of platforms
        int numPlatformsArray[] = new int[1];
        clGetPlatformIDs(0, null, numPlatformsArray);
        int numPlatforms = numPlatformsArray[0];

        // Obtain a platform ID
        cl_platform_id platforms[] = new cl_platform_id[numPlatforms];
        clGetPlatformIDs(platforms.length, platforms, null);
        cl_platform_id platform = platforms[platformIndex];

        // Initialize the context properties
        cl_context_properties contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);

        // Obtain the number of devices for the platform
        int numDevicesArray[] = new int[1];
        clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
        int numDevices = numDevicesArray[0];

        // Obtain a device ID
        cl_device_id devices[] = new cl_device_id[numDevices];
        clGetDeviceIDs(platform, deviceType, numDevices, devices, null);
        cl_device_id device = devices[deviceIndex];

        // Create a context for the selected device
        context = clCreateContext(contextProperties, 1, new cl_device_id[] { device }, null, null, null);

        // Create a command-queue for the selected device
        commandQueue = clCreateCommandQueue(context, device, 0, null);

        // Program Setup
        String source = readFile("kernels/Colormapper.cl");

        // Create the program
        cl_program cpProgram = clCreateProgramWithSource(context, 1, new String[] { source }, null, null);

        // Build the program
        clBuildProgram(cpProgram, 0, null, "-cl-mad-enable", null, null);

        // Create the kernel
        kernel = clCreateKernel(cpProgram, "mapColors", null);

        // Same Program Setup for Logarithmic scale colormapper
        source = readFile("kernels/LogColormapper.cl");
        cl_program cpProgram2 = clCreateProgramWithSource(context, 1, new String[] { source }, null, null);
        clBuildProgram(cpProgram2, 0, null, "-cl-mad-enable", null, null);
        logKernel = clCreateKernel(cpProgram2, "mapColors", null);
    }

    /**
     * Rebuilds (and re-reads) the storage of colormaps. Outputs succesfully
     * read colormap names to the command line.
     */
    private static void rebuildMaps() {
        colorMaps = new HashMap<String, int[]>();
        legends = new HashMap<String, Color[][]>();
        legendByteBuffers = new HashMap<String, ByteBuffer>();
        try {
            String[] colorMapFileNames = getColorMaps();
            for (String fileName : colorMapFileNames) {
                ArrayList<Color> colorList = new ArrayList<Color>();

                BufferedReader in = new BufferedReader(new FileReader("colormaps/" + fileName + ".ncmap"));
                String str;

                while ((str = in.readLine()) != null) {
                    String[] numbers = str.split(" ");
                    colorList.add(new Color(Integer.parseInt(numbers[0]), Integer.parseInt(numbers[1]), Integer
                            .parseInt(numbers[2]), 255));
                }

                in.close();

                int[] colorArray = initColorMapToSetSize(COLORMAP_FINAL_ENTRIES, colorList);
                colorMaps.put(fileName, colorArray);
                legends.put(fileName, makeLegendImage(LEGEND_WIDTH, LEGEND_HEIGHT, colorArray));
                legendByteBuffers.put(fileName,
                        initColorMapToSetSizeForLegendTexture(COLORMAP_FINAL_ENTRIES, colorList));
                logger.info("Colormap " + fileName + " registered for use.");
            }

        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    /**
     * Getter for the list of colormap names in the directory. Used to load
     * these maps.
     * 
     * @return the array containing all of the colormap names in the directory.
     *         These are unchecked.
     */
    private static String[] getColorMaps() {
        final String[] ls = new File("colormaps").list(new ExtFilter("ncmap"));
        final String[] result = new String[ls.length];

        for (int i = 0; i < ls.length; i++) {
            result[i] = ls[i].split("\\.")[0];
        }

        return result;
    }

    /**
     * Getter for the list of colormap names in the directory. Used to load
     * these maps.
     * 
     * @return the array containing all of the currently available colormap
     *         names.
     */
    public static String[] getColormapNames() {
        String[] names = new String[legends.size()];
        int i = 0;
        for (Entry<String, Color[][]> entry : legends.entrySet()) {
            names[i] = entry.getKey();
            i++;
        }

        return names;
    }

    /**
     * Helper function which reads the file with the given name and returns the
     * contents of this file as a String. Will exit the application if the file
     * can not be read.
     * 
     * @param fileName
     *            The name of the file to read.
     * @return The contents of the file
     */
    private String readFile(String fileName) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
            StringBuffer sb = new StringBuffer();
            String line = null;
            while (true) {
                line = br.readLine();
                if (line == null) {
                    break;
                }
                sb.append(line).append("\n");
            }
            br.close();
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
            return null;
        }
    }

    /**
     * Creates the colorMap array which contains RGB colors as integers,
     * interpolated through the given colors with colors.length * stepSize steps
     * 
     * @param stepSize
     *            The number of interpolation steps between two colors
     * @param colors
     *            The colors for the map
     */
    private static int[] initColorMap(int stepSize, ArrayList<Color> colors) {
        int[] colorMap = new int[stepSize * colors.size()];
        int index = 0;
        for (int i = 0; i < colors.size() - 1; i++) {
            Color c0 = colors.get(i);
            int r0 = c0.getRed();
            int g0 = c0.getGreen();
            int b0 = c0.getBlue();

            Color c1 = colors.get(i + 1);
            int r1 = c1.getRed();
            int g1 = c1.getGreen();
            int b1 = c1.getBlue();

            int dr = r1 - r0;
            int dg = g1 - g0;
            int db = b1 - b0;

            for (int j = 0; j < stepSize; j++) {
                float alpha = (float) j / (stepSize - 1);
                int r = (int) (r0 + alpha * dr);
                int g = (int) (g0 + alpha * dg);
                int b = (int) (b0 + alpha * db);

                int abgr = (255 << 24) | (b << 16) | (g << 8) | r << 0;
                colorMap[index++] = abgr;
            }
        }
        return colorMap;
    }

    /**
     * Creates the colorMap array which contains RGB colors as integers,
     * interpolated through the given colors with a set final length
     * 
     * @param finalSize
     *            The total number of color entries in the resulting map
     * @param colors
     *            The colors for the map
     */
    private static int[] initColorMapToSetSize(int finalSize, ArrayList<Color> colors) {
        int[] colorMap = new int[finalSize];
        int cmEntries = colors.size();

        for (int i = 0; i < finalSize; i++) {
            float rawIndex = cmEntries * (i / (float) finalSize);

            int iLow = (int) Math.floor(rawIndex);
            int iHigh = (int) Math.ceil(rawIndex);

            Color cLow;
            if (iLow == cmEntries) {
                cLow = colors.get(cmEntries - 1);
            } else if (iLow < 0) {
                cLow = colors.get(0);
            } else {
                cLow = colors.get(iLow);
            }

            Color cHigh;
            if (iHigh == cmEntries) {
                cHigh = colors.get(cmEntries - 1);
            } else if (iHigh < 0) {
                cHigh = colors.get(0);
            } else {
                cHigh = colors.get(iHigh);
            }

            float percentage = rawIndex - iLow;

            int r0 = cLow.getRed();
            int g0 = cLow.getGreen();
            int b0 = cLow.getBlue();

            int r1 = cHigh.getRed();
            int g1 = cHigh.getGreen();
            int b1 = cHigh.getBlue();

            int dr = r1 - r0;
            int dg = g1 - g0;
            int db = b1 - b0;

            int r = (int) (r0 + percentage * dr);
            int g = (int) (g0 + percentage * dg);
            int b = (int) (b0 + percentage * db);

            int abgr = (255 << 24) | (b << 16) | (g << 8) | r << 0;
            colorMap[i] = abgr;
        }

        return colorMap;
    }

    /**
     * Creates the colorMap array which contains RGB colors as integers,
     * interpolated through the given colors with a set final length
     * 
     * @param finalSize
     *            The total number of color entries in the resulting map
     * @param colors
     *            The colors for the map
     */
    private static ByteBuffer initColorMapToSetSizeForLegendTexture(int finalSize, ArrayList<Color> colors) {
        ByteBuffer colorMap = Buffers.newDirectByteBuffer(finalSize * 4);
        int cmEntries = colors.size();

        for (int i = finalSize - 1; i >= 0; i--) {
            float rawIndex = cmEntries * (i / (float) finalSize);

            int iLow = (int) Math.floor(rawIndex);
            int iHigh = (int) Math.ceil(rawIndex);

            Color cLow;
            if (iLow == cmEntries) {
                cLow = colors.get(cmEntries - 1);
            } else if (iLow < 0) {
                cLow = colors.get(0);
            } else {
                cLow = colors.get(iLow);
            }

            Color cHigh;
            if (iHigh == cmEntries) {
                cHigh = colors.get(cmEntries - 1);
            } else if (iHigh < 0) {
                cHigh = colors.get(0);
            } else {
                cHigh = colors.get(iHigh);
            }

            float percentage = rawIndex - iLow;

            int r0 = cLow.getRed();
            int g0 = cLow.getGreen();
            int b0 = cLow.getBlue();

            int r1 = cHigh.getRed();
            int g1 = cHigh.getGreen();
            int b1 = cHigh.getBlue();

            int dr = r1 - r0;
            int dg = g1 - g0;
            int db = b1 - b0;

            int r = (int) (r0 + percentage * dr);
            int g = (int) (g0 + percentage * dg);
            int b = (int) (b0 + percentage * db);

            colorMap.put((byte) (r & 0xFF));
            colorMap.put((byte) (g & 0xFF));
            colorMap.put((byte) (b & 0xFF));
            colorMap.put((byte) (255));
        }

        colorMap.flip();

        return colorMap;
    }

    /**
     * Creates the colorMap array which contains RGB colors as integers,
     * interpolated through the given colors with colors.length * stepSize steps
     * 
     * @param stepSize
     *            The number of interpolation steps between two colors
     * @param colors
     *            The colors for the map
     */
    private static int[] initColorMap(ArrayList<Color> colors) {
        int[] colorMap = new int[colors.size()];
        for (int i = 0; i < colors.size(); i++) {
            Color c0 = colors.get(i);
            int r = c0.getRed();
            int g = c0.getGreen();
            int b = c0.getBlue();
            int abgr = (255 << 24) | (b << 16) | (g << 8) | r << 0;
            colorMap[i] = abgr;
        }
        return colorMap;
    }

    /**
     * Execute the kernel function and return the resulting pixel data in an
     * array
     */
    public synchronized int[] makeImage(String colormapName, Dimensions dim, float[] data, float fillValue,
            boolean logScale, int width, int height) {
        // select colormap and write to GPU
        int[] colorMap = colorMaps.get(colormapName);
        if (colorMap == null) {
            System.out.println("Non-existing colormap selected: " + colormapName);
        }
        if (colorMapMem == null) {
            colorMapMem = clCreateBuffer(context, CL_MEM_READ_WRITE, colorMap.length * Sizeof.cl_uint, null, null);
        }
        clEnqueueWriteBuffer(commandQueue, colorMapMem, CL_TRUE, 0, colorMap.length * Sizeof.cl_uint,
                Pointer.to(colorMap), 0, null, null);

        // Check if an ooutput and databuffer for this size were previously
        // allocated, if not create them.
        ReservedMemoryConstruct predefinedConstruct = null;
        for (ReservedMemoryConstruct memoryConstruct : reservedMemory) {
            if (memoryConstruct.width == width && memoryConstruct.height == height) {
                predefinedConstruct = memoryConstruct;
            }
        }
        if (predefinedConstruct == null) {
            cl_mem outputMem = clCreateBuffer(context, CL_MEM_WRITE_ONLY, width * height * Sizeof.cl_uint, null, null);
            cl_mem dataMem = clCreateBuffer(context, CL_MEM_READ_WRITE, width * height * Sizeof.cl_float, null, null);
            predefinedConstruct = new ReservedMemoryConstruct(width, height, outputMem, dataMem);
            reservedMemory.add(predefinedConstruct);
        }

        cl_mem dataMem = predefinedConstruct.getDataMem();
        cl_mem outputMem = predefinedConstruct.getOutputMem();

        // write data to GPU
        clEnqueueWriteBuffer(commandQueue, dataMem, CL_TRUE, 0, width * height * Sizeof.cl_float, Pointer.to(data), 0,
                null, null);

        // Set work size
        long globalWorkSize[] = new long[2];
        globalWorkSize[0] = width;
        globalWorkSize[1] = height;

        cl_kernel currentKernel = kernel;
        if (logScale) {
            currentKernel = logKernel;
        }

        // load uniforms
        clSetKernelArg(currentKernel, 0, Sizeof.cl_mem, Pointer.to(dataMem));
        clSetKernelArg(currentKernel, 1, Sizeof.cl_mem, Pointer.to(outputMem));
        clSetKernelArg(currentKernel, 2, Sizeof.cl_mem, Pointer.to(colorMapMem));
        clSetKernelArg(currentKernel, 3, Sizeof.cl_uint, Pointer.to(new int[] { width }));
        clSetKernelArg(currentKernel, 4, Sizeof.cl_uint, Pointer.to(new int[] { height }));
        clSetKernelArg(currentKernel, 5, Sizeof.cl_float, Pointer.to(new float[] { fillValue }));
        clSetKernelArg(currentKernel, 6, Sizeof.cl_float, Pointer.to(new float[] { dim.getMin() }));
        clSetKernelArg(currentKernel, 7, Sizeof.cl_float, Pointer.to(new float[] { dim.getMax() }));
        clSetKernelArg(currentKernel, 8, Sizeof.cl_uint, Pointer.to(new int[] { 0 << 24 | 0 << 16 | 0 << 8 | 0 << 0 }));
        clSetKernelArg(currentKernel, 9, Sizeof.cl_uint, Pointer.to(new int[] { colorMap.length }));

        // and execute the currentKernel
        clEnqueueNDRangeKernel(commandQueue, currentKernel, 2, null, globalWorkSize, null, 0, null, null);

        // Read the output pixel data into the array
        int pixels[] = new int[width * height];
        clEnqueueReadBuffer(commandQueue, outputMem, CL_TRUE, 0, width * height * Sizeof.cl_uint, Pointer.to(pixels),
                0, null, null);

        return pixels;
    }

    public void dispose() {
        System.out.println("NOW RELEASING JOCL MEMORY");

        for (ReservedMemoryConstruct memoryConstruct : reservedMemory) {
            CL.clReleaseMemObject(memoryConstruct.getDataMem());
            CL.clReleaseMemObject(memoryConstruct.getOutputMem());
        }
        reservedMemory.clear();

        CL.clReleaseKernel(kernel);
        CL.clReleaseKernel(logKernel);

        CL.clReleaseMemObject(colorMapMem);

        CL.clReleaseCommandQueue(commandQueue);
        CL.clReleaseContext(context);

        kernel = null;
        logKernel = null;
        colorMapMem = null;
        commandQueue = null;
        context = null;
    }

    public ByteBuffer getColormapForLegendTexture(String colormapName) {
        return legendByteBuffers.get(colormapName);
    }

    /**
     * Function that returns a combobox with all of the legends of the entire
     * list of colormaps for selection.
     * 
     * @param preferredDimensions
     *            The dimensions of the combobox to be returned.
     * @return The combobox.
     */
    public static JComboBox<SimpleImageIcon> getLegendJComboBox(Dimension preferredDimensions) {

        int width = (int) (preferredDimensions.width * .8), height = (int) (preferredDimensions.height * .8);

        SimpleImageIcon[] simpleImageIcons = new SimpleImageIcon[legends.size()];

        int i = 0;
        for (Entry<String, Color[][]> entry : legends.entrySet()) {
            String description = entry.getKey();
            Color[][] legendImageBuffer = makeLegendImage(width, height, colorMaps.get(description));

            BufferedImage legend = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
            WritableRaster raster = legend.getRaster();

            for (int col = 0; col < width; col++) {
                for (int row = 0; row < height; row++) {
                    raster.setSample(col, row, 0, legendImageBuffer[col][row].getBlue());
                    raster.setSample(col, row, 1, legendImageBuffer[col][row].getGreen());
                    raster.setSample(col, row, 2, legendImageBuffer[col][row].getRed());
                }
            }

            ImageIcon icon = new ImageIcon(legend);

            SimpleImageIcon simpleImageIcon = new SimpleImageIcon(description, icon);

            simpleImageIcons[i] = simpleImageIcon;
            i++;
        }

        JComboBox<SimpleImageIcon> legendList = new JComboBox<SimpleImageIcon>(simpleImageIcons);

        ImageComboBoxRenderer renderer = new ImageComboBoxRenderer(simpleImageIcons);
        renderer.setPreferredSize(preferredDimensions);
        legendList.setRenderer(renderer);
        legendList.setMaximumRowCount(10);

        return legendList;
    }

    /**
     * Function that makes a Legend for a colormap.
     * 
     * @param width
     *            The width of the image to create.
     * @param height
     *            The hieght of the image to create.
     * @param colorMap
     *            The colormap to create a legend of.
     * @return A Color[][] that holds the pixels corresponding to an image for a
     *         colormap legend.
     */
    private static Color[][] makeLegendImage(int width, int height, int[] colorMap) {
        Color[][] outBuf = new Color[width][height];

        for (int col = 0; col < width; col++) {
            float index = col / (float) width;

            int cmEntries = colorMap.length;
            int cmIndex = (int) (index * cmEntries);

            Color color;
            if (cmIndex == cmEntries) {
                color = new Color(colorMap[cmEntries - 1], true);
            } else if (cmIndex < 0) {
                color = new Color(colorMap[0], true);
            } else {
                color = new Color(colorMap[cmIndex], true);
            }

            for (int row = 0; row < height; row++) {
                outBuf[col][row] = color;
            }
        }

        return outBuf;
    }

    /**
     * Getter for the index number of a specific colormap name (used by swing).
     * 
     * @param colorMap
     *            The name of the colormap selected
     * @return The index number.
     */
    public static int getIndexOfColormap(String colorMap) {
        int i = 0;
        for (Entry<String, Color[][]> entry : legends.entrySet()) {
            String name = entry.getKey();

            if (name.compareTo(colorMap) == 0) {
                return i;
            }
            i++;
        }

        return -1;
    }

}