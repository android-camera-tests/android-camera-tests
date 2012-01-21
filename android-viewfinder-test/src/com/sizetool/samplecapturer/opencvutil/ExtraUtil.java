package com.sizetool.samplecapturer.opencvutil;

import java.nio.ByteBuffer;
import org.opencv.core.Mat;
import android.graphics.Bitmap;

public class ExtraUtil {
	public static Mat createMatFromBitmap(Bitmap bitmap) {
		if (bitmap == null) throw new NullPointerException("bitmap null");
		if (bitmap.isRecycled()) throw new NullPointerException("bitmap is recycled");
		Bitmap.Config config = bitmap.getConfig();
		
		if (!Bitmap.Config.ALPHA_8.equals(config) && !Bitmap.Config.ARGB_8888.equals(config)) throw new UnsupportedOperationException("Can only wrap 8bit or 32bits in Mat");

		Mat result = new Mat(nativeCreateMatFromBitmap(bitmap));
		return result;
	}
	
	static native long nativeCreateMatFromBytebuffer(ByteBuffer buf, int rows, int cols, int type) ;

	static native long nativeCreateMatFromBitmap(Bitmap bitmap);
	
    static {
        System.loadLibrary("extra_util");
    }
}
