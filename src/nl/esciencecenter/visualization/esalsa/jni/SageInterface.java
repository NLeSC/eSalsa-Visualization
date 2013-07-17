package nl.esciencecenter.visualization.esalsa.jni;

import java.nio.ByteBuffer;

public class SageInterface {
    // public native int swapBuffers(int[] rgb);
    public native int setup(int width, int height, int fps);

    public native int start(ByteBuffer rgb);

    boolean setupDone = false;

    public SageInterface(int width, int height, int fps) {

        try {
            System.loadLibrary("sail");
            System.loadLibrary("quanta");
            System.loadLibrary("im-Linux-amd64");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Failed to load libraries.");
            System.err.println(e.getMessage());
            System.exit(1);
        }

        System.out.println("Libraries loaded, sending " + (width * height)
                + " pixels in WxH: " + width + "x" + height + ".");
        setup(width, height, fps);
    }

    public void display(ByteBuffer rgb) {
        start(rgb);
    }
}
