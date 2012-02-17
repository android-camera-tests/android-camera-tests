package se.birkler.samplecapturer.opencvutil;

import java.nio.ByteBuffer;

public class ExtraUtil {
	static native long nativeCreateMatFromBytebuffer(ByteBuffer buf, int rows, int cols, int type) ;

	
    static {
        System.loadLibrary("extra_util");
    }
}
