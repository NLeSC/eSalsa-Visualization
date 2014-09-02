package nl.esciencecenter.visualization.esalsa;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import nl.esciencecenter.neon.NeonNewtWindow;
import nl.esciencecenter.neon.input.InputHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImauApp {
    private final static ImauSettings settings = ImauSettings.getInstance();
    private final static Logger log = LoggerFactory.getLogger(ImauApp.class);

    private static JFrame frame;
    private static ImauPanel imauPanel;
    private static ImauWindow imauWindow;

    public static void main(final String[] arguments) {
        // Create the Swing interface elements
        imauPanel = new ImauPanel();

        // Create the GLEventListener
        imauWindow = new ImauWindow(InputHandler.getInstance());

        NeonNewtWindow window = new NeonNewtWindow(true, imauWindow.getInputHandler(), imauWindow,
                settings.getDefaultScreenWidth(), settings.getDefaultScreenHeight(), "eSalsa Visualization");

        // Create the frame
        final JFrame frame = new JFrame("eSalsa Visualization");
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent arg0) {
                System.exit(0);
            }
        });

        frame.setAlwaysOnTop(true);

        frame.setSize(ImauApp.settings.getInterfaceWidth(), ImauApp.settings.getInterfaceHeight());

        frame.setResizable(false);

        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    frame.getContentPane().add(imauPanel);
                    
                    if (arguments.length > 1 && arguments[0].compareTo("-i") ==0) {
                    	File dir = new File(arguments[1]);
                    	if (!dir.isDirectory()) {
                    		return;
                    	}
                    	
                		imauPanel.handleFiles(dir.listFiles(new FilenameFilter() {
                			FileNameExtensionFilter fnef = new FileNameExtensionFilter("nc", "nc");
							@Override
							public boolean accept(File dir, String name) {								
								File f = new File(dir.getAbsolutePath() + "/"+name);
								if (!f.isDirectory()) {
									System.out.println(f.getAbsolutePath()+ " : "+ fnef.accept(f));
									return fnef.accept(f);
								}
								return false;
							}
						}));
                    }
                } catch (final Exception e) {
                    e.printStackTrace(System.err);
                    System.exit(1);
                }
            }
        });

        frame.setVisible(true);
    }

    public static BufferedImage getFrameImage() {
        Component component = frame.getContentPane();
        BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_RGB);

        // call the Component's paint method, using
        // the Graphics object of the image.
        component.paint(image.getGraphics());

        return image;
    }

    public static Dimension getFrameSize() {
        return frame.getContentPane().getSize();
    }

    public static Point getCanvaslocation() {
        return imauPanel.getCanvasLocation();
    }

    public static void feedMouseEventToPanel(int x, int y) {
        Point p = new Point(x, y);
        SwingUtilities.convertPointFromScreen(p, frame.getContentPane());

        System.out.println("x " + x + " y " + y);
        System.out.println("p.x " + p.x + " p.y " + p.y);

        if ((p.x > 0 && p.x < frame.getWidth()) && (p.y > 0 && p.y < frame.getHeight())) {
            Component comp = SwingUtilities.getDeepestComponentAt(frame.getContentPane(), p.x, p.y);

            System.out.println(comp.toString());

            Toolkit.getDefaultToolkit().getSystemEventQueue()
                    .postEvent(new MouseEvent(comp, MouseEvent.MOUSE_PRESSED, 0, 0, p.x, p.y, 1, false));
            Toolkit.getDefaultToolkit().getSystemEventQueue()
                    .postEvent(new MouseEvent(comp, MouseEvent.MOUSE_RELEASED, 0, 0, p.x, p.y, 1, false));
            Toolkit.getDefaultToolkit().getSystemEventQueue()
                    .postEvent(new MouseEvent(comp, MouseEvent.MOUSE_CLICKED, 0, 0, p.x, p.y, 1, false));
        }
    }
}
