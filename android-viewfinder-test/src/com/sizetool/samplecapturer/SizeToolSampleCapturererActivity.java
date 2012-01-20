package com.sizetool.samplecapturer;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import com.sizetool.samplecapturer.camera.OpenCVViewfinderView;
import com.sizetool.samplecapturer.util.XLog;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

public class SizeToolSampleCapturererActivity extends Activity {
    public static final int     VIEW_MODE_RGBA     = 0;
    public static final int     VIEW_MODE_GRAY     = 1;
    public static final int     VIEW_MODE_CANNY    = 2;
    public static final int     VIEW_MODE_FEATURES = 5;

    private int           viewMode           = VIEW_MODE_RGBA;


	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        OpenCVViewfinderView view = (OpenCVViewfinderView)findViewById(R.id.viewfinder_view);
        view.setProcessor(new OpenCvProcessor());
    }
    
    class OpenCvProcessor implements OpenCVViewfinderView.openCVProcessor {
		private Mat mIntermediateMat;
		private Mat mRgba;
		private Bitmap mBitmap;

		@Override
		public Bitmap processFrame(int width, int height, Mat yuvData, Mat grayData) {
			if (mRgba == null) {
				mRgba = new Mat();
			}
			if (mIntermediateMat != null) {
				mIntermediateMat = new Mat();
			}
    	    
    	    switch (viewMode) {
    	    case VIEW_MODE_GRAY:
    	        Imgproc.cvtColor(grayData, mRgba, Imgproc.COLOR_GRAY2RGBA, 4);
    	        break;
    	    case VIEW_MODE_RGBA:
    	        Imgproc.cvtColor(yuvData, mRgba, Imgproc.COLOR_YUV420sp2RGB, 4);
    	        break;
    	    case VIEW_MODE_CANNY:
    	        Imgproc.Canny(grayData, mIntermediateMat, 80, 100);
    	        Imgproc.cvtColor(mIntermediateMat, mRgba, Imgproc.COLOR_GRAY2BGRA, 4);
    	        break;
    	    case VIEW_MODE_FEATURES:
    	        Imgproc.cvtColor(yuvData, mRgba, Imgproc.COLOR_YUV420sp2RGB, 4);
    	        //FindFeatures(grayData.getNativeObjAddr(), mRgba.getNativeObjAddr());
    	        break;
    	    }
    	    if (Utils.matToBitmap(mRgba, mBitmap)) {
    	        return mBitmap;
    	    }
			return null;
		}
    }

    private MenuItem            mItemPreviewRGBA;
    private MenuItem            mItemPreviewGray;
    private MenuItem            mItemPreviewCanny;
    private MenuItem            mItemPreviewFeatures;


    public boolean onCreateOptionsMenu(Menu menu) {
        XLog.i("onCreateOptionsMenu");
        mItemPreviewRGBA = menu.add("Preview RGBA");
        mItemPreviewGray = menu.add("Preview GRAY");
        mItemPreviewCanny = menu.add("Canny");
        mItemPreviewFeatures = menu.add("Find features");
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        XLog.i("Menu Item selected " + item);
        if (item == mItemPreviewRGBA)
            viewMode = VIEW_MODE_RGBA;
        else if (item == mItemPreviewGray)
            viewMode = VIEW_MODE_GRAY;
        else if (item == mItemPreviewCanny)
            viewMode = VIEW_MODE_CANNY;
        else if (item == mItemPreviewFeatures)
            viewMode = VIEW_MODE_FEATURES;
        return true;
    }


}