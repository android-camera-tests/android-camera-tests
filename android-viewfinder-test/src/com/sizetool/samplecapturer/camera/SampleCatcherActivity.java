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
import java.nio.ByteBuffer;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import com.sizetool.samplecapturer.R;
import com.sizetool.samplecapturer.opencvutil.MatBitmapHolder;
import com.sizetool.samplecapturer.opencvutil.MatByteBufferWrapper;
import com.sizetool.samplecapturer.util.XLog;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Bitmap.Config;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Xfermode;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * The barcode reader activity itself. This is loosely based on the
 * CameraPreview example included in the Android SDK.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */
public final class SampleCatcherActivity extends Activity  implements ShutterCallback, PictureCallback {

	private PreviewView viewfinderView;
	private View statusRootView;
	private TextView statusView;
	private View resultView;
	private boolean hasSurface;
	private SurfaceView surfaceView;
	private Bitmap mBitmap;
	private ImageView mLeftGuidanceView;

    public static final int     VIEW_MODE_RGBA     = 0;
    public static final int     VIEW_MODE_GRAY     = 1;
    public static final int     VIEW_MODE_CANNY    = 2;
    public static final int     VIEW_MODE_CANNY_OVERLAY    = 3;
    public static final int     VIEW_MODE_FEATURES = 5;

    private int           viewMode           = VIEW_MODE_RGBA;


    class OpenCvProcessor implements OpenCVViewfinderView.openCVProcessor {
		private MatByteBufferWrapper mIntermediateMat;
		private Bitmap mCannyBitmap;
		private Paint mPaint;
		private MatBitmapHolder mRgbaMatHolder;
		
		public OpenCvProcessor() {
			mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
			mPaint.setARGB(255,255,255,100);
			Xfermode xferMode = new PorterDuffXfermode(Mode.SRC_OVER);
			mPaint.setXfermode(xferMode);
			mPaint.setColorFilter(new PorterDuffColorFilter(Color.rgb(255,255,100),Mode.SRC_OVER));
		}

		@Override
		public void processFrame(Canvas canvas, int width, int height, Mat yuvData, Mat grayData) {
			Mat rgbaMat;
			
			if (mIntermediateMat == null) {
				mIntermediateMat = new MatByteBufferWrapper(ByteBuffer.allocateDirect(width*height),height,width,CvType.CV_8U);
			}
    	    if (mRgbaMatHolder == null){
    	    	Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    	    	mRgbaMatHolder = new MatBitmapHolder(bitmap);    	    
    	    }
    	    
    	    rgbaMat = mRgbaMatHolder.pin();
    	    
	    	canvas.drawColor(Color.TRANSPARENT,Mode.CLEAR);
    	    
    	    boolean drawCanny = false;
    	    
			switch (viewMode) {
    	    case VIEW_MODE_GRAY:
    	        Imgproc.cvtColor(grayData, rgbaMat, Imgproc.COLOR_GRAY2RGBA, 4);
    	        break;
    	    case VIEW_MODE_RGBA:
    	        //Imgproc.cvtColor(yuvData, mRgba, Imgproc.COLOR_YUV420sp2RGB, 4);
    	        break;
    	    case VIEW_MODE_CANNY:
    	    	canvas.drawColor(Color.BLACK);
    	        Imgproc.Canny(grayData, mIntermediateMat, 80, 100);
    	        if (mCannyBitmap == null) {
    	        	mCannyBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);
    	        }
    	        mCannyBitmap.copyPixelsFromBuffer(mIntermediateMat.getBuf());
    	        drawCanny  = true;
    	        break;
    	    case VIEW_MODE_CANNY_OVERLAY:
    	        Imgproc.cvtColor(yuvData, rgbaMat, Imgproc.COLOR_YUV420sp2RGB, 4);
    	        Imgproc.Canny(grayData, mIntermediateMat, 50, 100);
    	        //Imgproc.cvtColor(mIntermediateMat, mRgba, Imgproc.COLOR_GRAY2BGRA, 4);
    	        if (mCannyBitmap == null) {
    	        	mCannyBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);
    	        }
    	        mCannyBitmap.copyPixelsFromBuffer(mIntermediateMat.getBuf());
    	        drawCanny  = true;
    	        
        	    //if (Utils.matToBitmap(mIntermediateMat,mCannyBitmap)) {
        	    //	XLog.d("ok");
        	    //}
    	        break;
    	    case VIEW_MODE_FEATURES:
    	        //Imgproc.cvtColor(yuvData, mRgba, Imgproc.COLOR_YUV420sp2RGB, 4);
    	        rgbaMat.setTo(new Scalar(0,0,0,0));
    	        FindFeatures(grayData.getNativeObjAddr(), rgbaMat.getNativeObjAddr());
    	        break;
    	    }
			
    	    mRgbaMatHolder.unpin(rgbaMat);

   	    	canvas.drawBitmap(mRgbaMatHolder.getBitmap(), (canvas.getWidth() - width) / 2, (canvas.getHeight() - height) / 2, null);
   	    	
   	    	
   	    	if (mCannyBitmap != null && drawCanny) {
       	    	mPaint.setStyle(Paint.Style.FILL);
       	    	Paint p = new Paint();
       	    	p.setColor(Color.YELLOW);
       	    	canvas.drawBitmap(mCannyBitmap,0,0,p);
   	    	}
		}
    }
    
    static native void FindFeatures(long grayDataPtr, long mRgba);

    private MenuItem            mItemPreviewRGBA;
    private MenuItem            mItemPreviewGray;
    private MenuItem            mItemPreviewCanny;
    private MenuItem            mItemPreviewCannyOverlay;
    private MenuItem            mItemPreviewFeatures;
	private Bitmap mLeftGuidanceBitmap;


    public boolean onCreateOptionsMenu(Menu menu) {
        XLog.i("onCreateOptionsMenu");
        mItemPreviewRGBA = menu.add("Preview RGBA");
        mItemPreviewGray = menu.add("Preview GRAY");
        mItemPreviewCanny = menu.add("Canny");
        mItemPreviewCannyOverlay = menu.add("Canny Overlay");
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
        else if (item == mItemPreviewCannyOverlay)
            viewMode = VIEW_MODE_CANNY_OVERLAY;
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
		mLeftGuidanceView = (ImageView)findViewById(R.id.imageview_leftprevious);
		viewfinderView = (PreviewView) findViewById(R.id.viewfinder_view);
		statusRootView = (View) findViewById(R.id.status_rootview);
		viewfinderView.setProcessor(new OpenCvProcessor());
		
        Button startButton = (Button)findViewById(R.id.button_start);
        startButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				viewfinderView.takePicture(SampleCatcherActivity.this,SampleCatcherActivity.this);
				
			}
		});
		
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

	@Override
	public void onPictureTaken(byte[] data, Camera camera) {
		
		
	}

	@Override
	public void onShutter() {
		Bitmap b = mBitmap;
		if (b != null) {
			int width = b.getWidth() / 8;
			int heightextra = b.getHeight() / 8;
			mLeftGuidanceBitmap = Bitmap.createBitmap(width*2, b.getHeight(), Config.ARGB_8888);
			Matrix m = new Matrix();
			//From middle 
			m.setRectToRect(new RectF(b.getWidth() / 2 - width,0,b.getWidth() / 2 + width,b.getHeight()), new RectF(0,-heightextra,width,b.getHeight() + heightextra), Matrix.ScaleToFit.FILL);
			Canvas c = new Canvas(mLeftGuidanceBitmap);
			c.drawBitmap(b, m, null);
			mLeftGuidanceView.setImageBitmap(mLeftGuidanceBitmap);
			mLeftGuidanceView.setAlpha(128);
		}
	}
}
