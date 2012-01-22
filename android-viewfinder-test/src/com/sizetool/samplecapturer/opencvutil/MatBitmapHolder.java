package com.sizetool.samplecapturer.opencvutil;

import org.opencv.core.Mat;

import android.graphics.Bitmap;

public class MatBitmapHolder {
	static class MatFromBitmap extends Mat {
		
	}
	private Bitmap mBitmap;
	private Mat lockedMat;
	
	public MatBitmapHolder(Bitmap bitmap) {
		mBitmap = bitmap;
	}
	
	public Mat pin() {
		if (lockedMat != null) throw new IllegalStateException("bitmap is already locked");
		if (mBitmap == null) throw new NullPointerException("bitmap null");
		if (mBitmap.isRecycled()) throw new IllegalStateException("bitmap is recycled");
		Bitmap.Config config = mBitmap.getConfig();
		if (!Bitmap.Config.ALPHA_8.equals(config) && !Bitmap.Config.ARGB_8888.equals(config)) throw new UnsupportedOperationException("Can only wrap 8bit or 32bits in Mat");
		lockedMat = new Mat(nativeCreateMatFromBitmapAndLock(mBitmap));
		return lockedMat;
	}
	
	private static native long nativeCreateMatFromBitmapAndLock(Bitmap mBitmap);

	public void unpin(Mat mat) {
		if (mat != lockedMat) throw new IllegalStateException("trying to unlock  mat");
		long nativeobj = lockedMat.nativeObj;
		//lockedMat.nativeObj = 0; //You can no longer use this mat
		nativeFreeMatAndUnlockBitmap(nativeobj,mBitmap);
		lockedMat = null;
	}

	private static native int nativeFreeMatAndUnlockBitmap(long matnative, Bitmap mBitmap);

	public Bitmap getBitmap() {
		if (lockedMat != null) throw new IllegalStateException("mat is pinned, bitmap is unavail");
		return mBitmap;
	}
	
	
	
}
