package com.sizetool.samplecapturer.camera;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import android.util.Log;

public class MatByteBufferWrapper extends Mat {
	private ByteBuffer mBuf;
	
	MatByteBufferWrapper(ByteBuffer b, int rows, int cols, int type) {
		super(createMatFromByteBuffer(b,rows,cols,type));
		mBuf = b;
		Log.d("XXX",this.toString());
	} 
	
	static private long createMatFromByteBuffer(ByteBuffer buf, int rows, int cols, int type) {
		if (!buf.isDirect()) throw new UnsupportedOperationException();
		if (buf.capacity() < sizeNeeded(rows,cols,type)) throw new BufferOverflowException();
		long nativeMat = nativeCreateMat(buf, rows,cols, type);
		return nativeMat;
	} 
	
	private static int sizeNeeded(int rows, int cols, int type) {
		return rows*cols*CvType.ELEM_SIZE(type);
		
	}

	private static native long nativeCreateMat(ByteBuffer buf, int rows, int cols, int type) ;

    static {
        System.loadLibrary("mixed_sample");
    }

}
