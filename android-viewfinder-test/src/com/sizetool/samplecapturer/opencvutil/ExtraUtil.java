package com.sizetool.samplecapturer.opencvutil;

import java.nio.ByteBuffer;

import android.graphics.Bitmap;

public class ExtraUtil {
	
	
	static native long nativeCreateMatFromBytebuffer(ByteBuffer buf, int rows, int cols, int type) ;

	static native long nativeCreateMatFromBitmap(Bitmap bitmap);
	
    static {
        System.loadLibrary("extra_util");
    }
}
