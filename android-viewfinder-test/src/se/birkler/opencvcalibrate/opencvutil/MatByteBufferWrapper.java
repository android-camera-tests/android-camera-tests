package se.birkler.opencvcalibrate.opencvutil;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import se.birkler.opencvcalibrate.util.XLog;



public class MatByteBufferWrapper extends Mat {
	private ByteBuffer mBuf;
	
	public MatByteBufferWrapper(ByteBuffer b, int rows, int cols, int type) {
		super(createMatFromByteBuffer(b,rows,cols,type));
		mBuf = b;
		XLog.d(this.toString());
	} 
	
	static private long createMatFromByteBuffer(ByteBuffer buf, int rows, int cols, int type) {
		if (!buf.isDirect()) throw new UnsupportedOperationException();
		if (buf.capacity() < sizeNeeded(rows,cols,type)) throw new BufferOverflowException();
		long nativeMat = nativeCreateMatFromBytebuffer(buf, rows,cols, type);
		return nativeMat;
	} 
	
	private static int sizeNeeded(int rows, int cols, int type) {
		return rows*cols*CvType.ELEM_SIZE(type);
		
	}

	public ByteBuffer getBuf() {
		return mBuf;
	}
	
	static native long nativeCreateMatFromBytebuffer(ByteBuffer buf, int rows, int cols, int type) ;
	
    static {
        System.loadLibrary("opencvutil");
    }
}
