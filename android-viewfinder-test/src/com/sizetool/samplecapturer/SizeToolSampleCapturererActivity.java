package com.sizetool.samplecapturer;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import com.sizetool.samplecapturer.camera.OpenCVViewfinderView;
import com.sizetool.samplecapturer.util.XLog;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

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
        viewFinder = (OpenCVViewfinderView)findViewById(R.id.viewfinder_view);
        viewFinder.setProcessor(new OpenCvProcessor());
        Button startButton = (Button)findViewById(R.id.button_start);
        startButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
			}
		});
    }
    
    class OpenCvProcessor implements OpenCVViewfinderView.openCVProcessor {
		private Mat mIntermediateMat;
		private Mat mRgba;
		private Bitmap mBitmap;

		@Override
		public void processFrame(Canvas canvas, int width, int height, Mat yuvData, Mat grayData) {
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
    	    }
		}
    }

    private MenuItem            mItemPreviewRGBA;
    private MenuItem            mItemPreviewGray;
    private MenuItem            mItemPreviewCanny;
    private MenuItem            mItemPreviewFeatures;
	private OpenCVViewfinderView viewFinder;
	private Bitmap mLeftGuidanceBitmap;


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