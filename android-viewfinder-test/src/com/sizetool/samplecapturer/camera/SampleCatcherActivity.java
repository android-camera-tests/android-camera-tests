/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sizetool.samplecapturer.camera;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import com.sizetool.samplecapturer.R;
import com.sizetool.samplecapturer.util.XLog;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * The barcode reader activity itself. This is loosely based on the
 * CameraPreview example included in the Android SDK.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */
public final class SampleCatcherActivity extends Activity {

	private OpenCVViewfinderView viewfinderView;
	private View statusRootView;
	private TextView statusView;
	private View resultView;
	private boolean hasSurface;
	private SurfaceView surfaceView;

    public static final int     VIEW_MODE_RGBA     = 0;
    public static final int     VIEW_MODE_GRAY     = 1;
    public static final int     VIEW_MODE_CANNY    = 2;
    public static final int     VIEW_MODE_FEATURES = 5;

    private int           viewMode           = VIEW_MODE_RGBA;


    class OpenCvProcessor implements OpenCVViewfinderView.openCVProcessor {
		private Mat mIntermediateMat;
		private Mat mRgba;
		private Bitmap mBitmap;

		@Override
		public Bitmap processFrame(int width, int height, Mat yuvData, Mat grayData) {
			if (mRgba == null) {
				mRgba = new Mat();
			}
			if (mIntermediateMat == null) {
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
    	        FindFeatures(grayData.getNativeObjAddr(), mRgba.getNativeObjAddr());
    	        break;
    	    }
    	    if (mBitmap == null || mBitmap.getWidth() != width || mBitmap.getHeight() != height){
    	    	mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    	    }
    	    if (Utils.matToBitmap(mRgba, mBitmap)) {
    	        return mBitmap;
    	    }
			return null;
		}
    }
    
    static native void FindFeatures(long grayDataPtr, long mRgba);

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


	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.sampler);
		viewfinderView = (OpenCVViewfinderView) findViewById(R.id.viewfinder_view);
		statusRootView = (View) findViewById(R.id.status_rootview);
		viewfinderView.setProcessor(new OpenCvProcessor());
	}
	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}


	// Don't display the share menu item if the result overlay is showing.
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public void finish() {
		super.finish();
	}
}
