package nl.esciencecenter.visualization.esalsa;

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
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.ImageIcon;
import javax.swing.JComboBox;

import nl.esciencecenter.neon.swing.ImageComboBoxRenderer;
import nl.esciencecenter.neon.swing.SimpleImageIcon;

import org.slf4j.LoggerFactory;

import com.jogamp.common.nio.Buffers;

public class LegendColormapper {
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
     * The width of the image
     */
    private int                       width  = 0;

    /**
     * The height of the image
     */
    private int                       height = 0;

    static {
        // Create and fill the memory object containing the color maps
        rebuildMaps();
    }

    /**
     * Creates the JOCLColormapper with the given width and height
     */
    public LegendColormapper(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /** Storage for the statically built legend images. */
    private static Map<String, Color[][]>  legends;
    private static Map<String, ByteBuffer> legendByteBuffers;

    private final static int               LEGEND_WIDTH           = 150;
    private final static int               LEGEND_HEIGHT          = 150;
    private final static int               COLORMAP_FINAL_ENTRIES = 500;

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

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
