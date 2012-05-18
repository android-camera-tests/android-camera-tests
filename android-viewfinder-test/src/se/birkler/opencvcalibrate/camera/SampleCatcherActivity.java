/**
 * Copyright 2012 Jorgen Birkler
 * jorgen@birkler.se
 *
 * Camera calibration and open cv demonstration applications
 *
 * TODO: Calibrate pictures taken and not calibrating the viewfinder; take snapshots, save, analyse and
 *
 */

package se.birkler.opencvcalibrate.camera;


import java.util.List;
import java.util.Vector;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.core.Core.MinMaxLocResult;
import org.opencv.imgproc.Imgproc;

import se.birkler.opencvcalibrate.camera.PreviewView;
import se.birkler.opencvcalibrate.opencvutil.MatBitmapHolder;
import se.birkler.opencvcalibrate.util.XLog;
import se.birkler.opencvcalibrate.R;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Xfermode;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;


/**
 */
public final class SampleCatcherActivity extends CaptureBaseActivity  implements PreviewView.PictureCallback {
	protected static final int MSG_UPDATE_ORIENTATION_ANGLE = 1;

    public static final int     VIEW_MODE_RGBA             = 0;
    public static final int     VIEW_MODE_CALIB_ACIRCLES   = 20;
    public static final int     VIEW_MODE_GRAY             = 1;
    public static final int     VIEW_MODE_CANNY            = 2;
    public static final int     VIEW_MODE_CANNY_OVERLAY    = 3;
    public static final int     VIEW_MODE_FEATURES         = 5;
    public static final int     VIEW_MODE_FEATURES_ORB     = 6;
    public static final int     VIEW_MODE_FEATURES_MSER    = 7;
    public static final int     VIEW_MODE_FEATURES_FAST    = 8;
    public static final int     VIEW_MODE_FEATURES_SURF    = 9;
    public static final int     VIEW_MODE_GOOD_FEAT        = 10;
	public static final int     VIEW_MODE_RECTANGLES       = 11;

	private int mViewMode = VIEW_MODE_CALIB_ACIRCLES;

	private View mStatusRootView;
	private TextView mStatusView;
	private View mResultView;
	private boolean mHasResultSurface;
	private SurfaceView mResultSurfaceView; 
	private Bitmap mBitmap;
	private ImageView mLeftGuidanceView;
	private TextView mInfoTextView;

	public boolean mDebugOutOn =false;
	private float mDistance = -1;
	
	FOVKalmanFilter mFieldOfViewKalmanFilter = new FOVKalmanFilter();

	protected float[] mOrientationRotationMatrix = new float[9];
	protected float[] mOrientationInclinationMatrix = new float[9];
	protected float[] mOrientationRotationMatrixReference = null;

	CalibrationEntries mCalibrationEntries = new CalibrationEntries();
	CameraCalibrationData mCameraCalibrationData = null;
	
	class CameraCalibrationData {
		CameraCalibrationData() {
			K = new Mat();
			kdist = new Mat();
		}
		Mat K;
		Mat kdist;
		double rms;
		
		String formatCalibrationDataString() {
			double fx = K.get(0, 0)[0];
			double fy = K.get(1, 1)[0];
			double px = K.get(0, 2)[0];
			double py = K.get(1, 2)[0];
			return String.format("rms=%.3f fx=%.1f fy=%.1f px=%.1f py = %.1f",rms, fx,fy,px,py);
		}
	}

	
	
	/**
	 * This is the meat of the open cv processing
	 * It checks current view mode and does some operations on the graMat with is view finder data directly.
	 * 
	 * The android graphics pipeline is setup as follows (hardware acceleration is requested in manifest):
	 * 
	 *        __________
	 *       /         /_   Android views        
	 *      /         / /_  Output surface view (mResultSurfaceView)       
	 *     /         / / /  Camera surface view (viewfinderView.mSurfaceView)      
	 *    /         / / /      
	 *   /         / / /       
	 *  /         / / /      
	 * /_________/ / /
	 *  /_________/ / 
	 *   /_________/
	 * 
	 * 
	 * Below demonstrates a more effcient way of obtaining a Mat from the viewfinder gray data without any copying or color conversion.
	 * This is achieved mainly by introducing a new classes; @see MatBitmapHolder and @see MatByteBufferWrapper
	 * 
	 * Two main advantages: 1. FPS is dramatically increased from the stock android opencv example. 2. No need to use native camera lib making it compatable with all android devices.
	 * 
	 * General setup is:
	 * - Obtain Mat wrappers for gray viewfinder data
	 * - Obtain Mat for output RGB bitmap (can use opencv graphical routines to write to android bitmap)
	 * - Surface is normally made transparent by filling with #00000000
	 * - Do opencv functions on gray viewfinder data
	 * - Output result or rgb birmap Mat
	 * - Draw rgbbitmap data to surface view
	 * - Draw any other output on surface view canvas
	 * - Release Mat wrappers
	 * .
	 * 
	 * 
	 * Demonstrators:
	 * - Color conversion
	 * - Edge detction
	 * - Feature detection with feature points highlighted in viewfinder
	 * - Call JNI function for furhter processing
	 * - Call calibration routines
	 * - Call dyi rectangle detector
	 * .
	 */

    class OpenCvProcessor implements PreviewView.OpenCVProcessor {
		private Paint mPaint;
		private MatBitmapHolder mRgbaMatHolder;
		private MatBitmapHolder mIntermediateMatHolder;
		private int newlyAdded;
		
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
	        float[] rectPoints = new float[5*21];//five rects
	        int rect_points = 0;
	        int calib_circles_points = 0;
	        if (!mFieldOfViewKalmanFilter.isConfigured()) {
	        	mFieldOfViewKalmanFilter.configure(width, height);
	        }
			
    	    if (mRgbaMatHolder == null){
    	    	Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    	    	mRgbaMatHolder = new MatBitmapHolder(bitmap);    	    
    	    }
    	    
    	    rgbaMat = mRgbaMatHolder.pin();
    	    
	    	canvas.drawColor(Color.TRANSPARENT,Mode.CLEAR);
    	    
    	    boolean drawRgb = false;
    	    
	        Mat centersCalibCircles = new Mat();
			boolean patternWasFound = false;
			
			
			switch (mViewMode) {
    	    case VIEW_MODE_GRAY:
    	        Imgproc.cvtColor(grayData, rgbaMat, Imgproc.COLOR_GRAY2RGBA, 4);
    	        drawRgb = true;
    	        break;
    	    case VIEW_MODE_RGBA:
    	        //Imgproc.cvtColor(yuvData, mRgba, Imgproc.COLOR_YUV420sp2RGB, 4);
    	        break;
    	    case VIEW_MODE_CANNY:
    	    case VIEW_MODE_CANNY_OVERLAY:
    	    	//This demonstrates how to pass a "gray" Mat to opencv that we use as a alpha8 mask in Android
    	    	//Thus we can overlay edge detction data on top of the viewfinder efficiently
    			if (mIntermediateMatHolder == null) {
    				Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);
    				mIntermediateMatHolder = new MatBitmapHolder(b);
    				
    			}
    	    	Mat result = mIntermediateMatHolder.pin();
    	        Imgproc.Canny(grayData, result, 80, 100);
    	    	mIntermediateMatHolder.unpin(result);

    	    	if (mViewMode == VIEW_MODE_CANNY) { 
        	    	canvas.drawColor(Color.BLACK);
    	    	}
       	    	mPaint.setStyle(Paint.Style.FILL);
       	    	Paint p = new Paint();
       	    	p.setColor(Color.YELLOW);
       	    	canvas.drawBitmap(mIntermediateMatHolder.getBitmap(),0,0,p);
    	        break;
    	    
    	    case VIEW_MODE_FEATURES:
    	    case VIEW_MODE_FEATURES_ORB:
    	    case VIEW_MODE_FEATURES_MSER:
    	    case VIEW_MODE_FEATURES_FAST:
    	        //Imgproc.cvtColor(yuvData, mRgba, Imgproc.COLOR_YUV420sp2RGB, 4);
    	        rgbaMat.setTo(new Scalar(0,0,0,0));
    	        findFeatures(mViewMode - VIEW_MODE_FEATURES, grayData.getNativeObjAddr(), rgbaMat.getNativeObjAddr());
    	        drawRgb = true;
    	        break;
    	        
    	    case VIEW_MODE_CALIB_ACIRCLES:
    	        rgbaMat.setTo(new Scalar(0,0,0,0));
    	        Size patternSize = CalibrationEntries.Pattern_ASYMMETRIC_CIRCLES_GRID_SIZE;
    	        Size imageSize = new Size(grayData.cols(),grayData.rows());
    	        patternWasFound = Calib3d.findCirclesGridDefault(grayData, patternSize, centersCalibCircles,Calib3d.CALIB_CB_ASYMMETRIC_GRID);
				Calib3d.drawChessboardCorners(rgbaMat, patternSize, centersCalibCircles, patternWasFound);
				
				if (patternWasFound && mOrientationRotationMatrix != null) {
					int addedIdx = mCalibrationEntries.addEntry(mOrientationRotationMatrix, centersCalibCircles);
					if (addedIdx >= 0) {
						Log.d("CALIB", String.format("Added calibration entry at %d tot: %d", addedIdx,mCalibrationEntries.getNumEntries()));
						newlyAdded++;
						if (newlyAdded > 5 && mCalibrationEntries.haveEnoughCalibrationData()) {
							newlyAdded = 0;
							List<Mat> objectPoints= mCalibrationEntries.getObjectPointsAsymmentricList();
							List<Mat> imagePoints= mCalibrationEntries.getPoints();
							mCameraCalibrationData = new CameraCalibrationData();
							List<Mat> rvecs = new Vector<Mat>(imagePoints.size());
							List<Mat> tvecs = new Vector<Mat>(imagePoints.size());
							int flags = 0;
							flags |= Calib3d.CALIB_FIX_K4 | Calib3d.CALIB_FIX_K5; 
							Log.d("CALIB", String.format("Calling Calib3d.calibrateCamera"));
							mCameraCalibrationData.rms = Calib3d.calibrateCamera(objectPoints, imagePoints, imageSize, mCameraCalibrationData.K, mCameraCalibrationData.kdist, rvecs, tvecs, flags);
							Log.d("CALIB", String.format("Calibration data: %s", mCameraCalibrationData.formatCalibrationDataString()));
						}
					}
				}
    	        drawRgb = true;
    	        break;
    	        
    	    case VIEW_MODE_RECTANGLES:
    	        //Imgproc.cvtColor(yuvData, mRgba, Imgproc.COLOR_YUV420sp2RGB, 4);
    	        rgbaMat.setTo(new Scalar(0,0,0,0));
    	        
    	        rect_points = findRectangles(grayData.getNativeObjAddr(),rectPoints,mDebugOutOn  ? rgbaMat.getNativeObjAddr() : 0);
    	        drawRgb = true;
    	        break;
    	    }
			
    	    mRgbaMatHolder.unpin(rgbaMat);

	        Paint paint = new Paint();
	        paint.setAntiAlias(true);
	        paint.setStrokeWidth(5.0f);
	        paint.setTextSize(20.0f);
	        paint.setShadowLayer(3.0f,0.0f,0.0f,Color.BLACK);
    	    if (drawRgb) {
    	    	canvas.drawBitmap(mRgbaMatHolder.getBitmap(), (canvas.getWidth() - width) / 2, (canvas.getHeight() - height) / 2, null);
    	    	double fov = mFieldOfViewKalmanFilter.getDiagFOV();
		        String s = String.format("fov:%.3f", fov*180/Math.PI);
		        paint.setColor(Color.WHITE);
	        	canvas.drawText(s,20,20,paint);
    	    }
    	    
	    	if (calib_circles_points > 0) {
		        for (int k=0;k < calib_circles_points;k+=2) {
			        paint.setColor(Color.GREEN);
			        paint.setStrokeWidth(3.0f);
			        canvas.drawCircle(rectPoints[k], rectPoints[k+1], 10.0f, paint);
		        }
	    	}

	    	//Draw calibration circles outline
			if (centersCalibCircles.rows() > 5 && centersCalibCircles.cols() == 1) {
				Mat centers_2n = centersCalibCircles.reshape(1); //centersCalibCircles is a Nx1 matrix of Point2f => reshape to Nx2 of float
				Mat centers_x = centers_2n.col(0);
				Mat centers_y = centers_2n.col(1);
				MinMaxLocResult x_min_max = Core.minMaxLoc(centers_x);
				MinMaxLocResult y_min_max = Core.minMaxLoc(centers_y);
				Paint rectPaint = new Paint();
				rectPaint.setColor(Color.YELLOW);
				rectPaint.setAntiAlias(true);
				rectPaint.setStrokeWidth(5.0f);
				rectPaint.setTextSize(20.0f);
				rectPaint.setShadowLayer(3.0f,0.0f,0.0f,Color.BLACK);
				rectPaint.setStyle(Paint.Style.STROKE);
				float margin = 10.0f;
				drawRect(canvas,(float)x_min_max.minVal - margin, (float)y_min_max.minVal - margin, (float)x_min_max.maxVal + margin, (float)y_min_max.maxVal + margin,rectPaint);
			}

	    	
	    	if (rect_points > 0) {
		        for (int k=rect_points;k>0;) {
		        	k--;
			        paint.setColor(Color.GREEN);
		        	drawQuad(canvas,rectPoints,k*21,paint);
			        float epsilon = rectPoints[8+k*21];
			        float distance;
			        float pixelArea;
			        if (epsilon < 1.0) {
				        pixelArea = rectPoints[19+k*21];
				        float paperHeightPixels = (float) Math.sqrt(pixelArea*8.5f/11.0f); 
				        float fov = viewfinderView.getFOVPerPixel();
				        float angle = (float) (fov * paperHeightPixels);
				        distance = (float) (0.28f / Math.tan(Math.PI * angle/180.0));
			        }
			        else {
			        	distance = 0;
			        }
			        String s = String.format("%d:%.3f:%.1f", k,epsilon,distance);
			        paint.setColor(Color.WHITE);
			        if (k==0) {
			        	float centerX = (rectPoints[0]+rectPoints[4])/2;
			        	float centerY = (rectPoints[1]+rectPoints[5])/2;
			        	canvas.drawText(s,centerX,centerY,paint);
			        	//bitmap = Bitmap.createBitmap(source, x, y, width, height)
			        	
			        	mFieldOfViewKalmanFilter.predict();
			        	if (mGravityValues != null) {
			        		mFieldOfViewKalmanFilter.updateOrientation(mGravityValues);
			        	}
			        	mFieldOfViewKalmanFilter.update(centerX - grayData.cols(), centerY - grayData.rows(), epsilon*50, epsilon * 50);
			        }
		        }
	    	}
		}
    }
    
    static native void findFeatures(int featureType, long grayDataPtr, long mRgba);
    static native int findRectangles(long grayDataPtr,float[] rects, long mRgba);
    static native int findCalibrationCircles(long grayDataPtr,float[] points, long mRgba);

    private MenuItem            mItemPreviewRGBA;
    private MenuItem            mItemPreviewGray;
    private MenuItem            mItemPreviewCanny;
    private MenuItem            mItemPreviewCannyOverlay;
    private MenuItem            mItemPreviewFeaturesOrb;
    private MenuItem            mItemPreviewFeaturesMser;
    private MenuItem            mItemPreviewFeaturesFast;
    private MenuItem            mItemPreviewFeaturesSurf;
    private MenuItem 			mItemPreviewRectangles;
    private MenuItem 			mItemPreviewCalibrationCircles;
    private Bitmap 				mLeftGuidanceBitmap;
	private MenuItem			mItemPreviewDebugOnOff;
	private Handler				mHandler;
	
	private long				prevInfoTextUpdateTicks = 0;
	

    public boolean onCreateOptionsMenu(Menu menu) {
        XLog.i("onCreateOptionsMenu");
        mItemPreviewDebugOnOff = menu.add("Debug On/Off");
        mItemPreviewCalibrationCircles = menu.add("Calib Circles");
        mItemPreviewRectangles = menu.add("Find rectangles");
        //mItemPreviewRGBA = menu.add("Preview RGBA");
        mItemPreviewGray = menu.add("Preview GRAY");
        //mItemPreviewCanny = menu.add("Canny");
        mItemPreviewCannyOverlay = menu.add("Canny Overlay");
        mItemPreviewFeaturesOrb = menu.add("Find ORB");
        mItemPreviewFeaturesMser = menu.add("Find Mser");
        mItemPreviewFeaturesFast = menu.add("Find Fast");
        mItemPreviewFeaturesSurf = menu.add("Find Surf");
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        XLog.i("Menu Item selected " + item);
        if (item == mItemPreviewDebugOnOff)
            mDebugOutOn = !mDebugOutOn;
        else if (item == mItemPreviewCalibrationCircles)
            mViewMode = VIEW_MODE_CALIB_ACIRCLES;
        else if (item == mItemPreviewRGBA)
            mViewMode = VIEW_MODE_RGBA;
        else if (item == mItemPreviewGray)
            mViewMode = VIEW_MODE_GRAY;
        else if (item == mItemPreviewCanny)
            mViewMode = VIEW_MODE_CANNY;
        else if (item == mItemPreviewCannyOverlay)
            mViewMode = VIEW_MODE_CANNY_OVERLAY;
        else if (item == mItemPreviewFeaturesOrb)
            mViewMode = VIEW_MODE_FEATURES_ORB;
        else if (item == mItemPreviewFeaturesMser)
            mViewMode = VIEW_MODE_FEATURES_MSER;
        else if (item == mItemPreviewFeaturesFast)
            mViewMode = VIEW_MODE_FEATURES_FAST;
        else if (item == mItemPreviewFeaturesSurf)
            mViewMode = VIEW_MODE_FEATURES_SURF;
        else if (item == mItemPreviewRectangles)
            mViewMode = VIEW_MODE_RECTANGLES;
        return true;
    }
    
     

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.sampler);
		mLeftGuidanceView = (ImageView)findViewById(R.id.imageview_leftprevious);
		viewfinderView = (PreviewView) findViewById(R.id.viewfinder_view);
		mStatusRootView = (View) findViewById(R.id.status_rootview);
		viewfinderView.setProcessor(new OpenCvProcessor());
		mInfoTextView = (TextView)findViewById(R.id.textDistance);
        Button startButton = (Button)findViewById(R.id.button_start);
        startButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mOrientationRotationMatrixReference = new float[9];
				System.arraycopy(mOrientationRotationMatrix, 0, mOrientationRotationMatrixReference, 0, mOrientationRotationMatrix.length);
				viewfinderView.takePicture(SampleCatcherActivity.this);
			}
		});
        mHandler = new Handler() {
        	@Override
			public void handleMessage(Message msg) {
        		if (msg.what == MSG_UPDATE_ORIENTATION_ANGLE) {
        			updateRotationText();
        		}
        	}
        };
	}

	void updateRotationText() {
		if (mGravityValues != null  && mMagnetometerValues != null) {
			SensorManager.getRotationMatrix(mOrientationRotationMatrix, mOrientationInclinationMatrix, mGravityValues, mMagnetometerValues);
		}
		
		if (mOrientationRotationMatrixReference != null && mInfoTextView != null) {
	
			float angles[] = new float[3];
			SensorManager.getAngleChange(angles, mOrientationRotationMatrix, mOrientationRotationMatrixReference);
			for (int i = 0; i < angles.length;i++) angles[i] *= 180.0f / 3.14f; 
			Mat Rref = new Mat(3,3,CvType.CV_32F);
			Mat Rnow = new Mat(3,3,CvType.CV_32F);
			Mat Rdelta = new Mat(3,3,CvType.CV_32F);
			//Mat Rnone = Mat.eye(3,3, CvType.CV_32F);
			Rref.put(0, 0,mOrientationRotationMatrixReference); 
			Rnow.put(0, 0,mOrientationRotationMatrix); 
			
			Core.gemm(Rnow, Rref, 1.0, Mat.zeros(3,3,CvType.CV_32F), 0.0, Rdelta,Core.GEMM_2_T);
			//Core.multiply(Rnow, Rref.inv(), Rdelta); <= don't use: broken
			
			//Log.d("Angles",String.format("det Rdelta: %.5f Rref %.5f Rnow %.5f",Core.determinant(Rdelta),Core.determinant(Rref.inv()),Core.determinant(Rnow)));
			Mat rvect = new Mat(1,3,CvType.CV_32F);
			Calib3d.Rodrigues(Rdelta, rvect);
			double angle = Core.norm(rvect) * 180.0 / 3.14;
			mInfoTextView.setText(String.format("%.2f (%.0f,%.0f,%.0f)",angle,angles[0],angles[1],angles[2]));
		}
	}
	
	public void onSensorChanged(SensorEvent event) {
		super.onSensorChanged(event);
		long ticks = System.currentTimeMillis();
		if (mHandler != null && ticks > prevInfoTextUpdateTicks + 500) {
			prevInfoTextUpdateTicks = ticks;
			mHandler.removeMessages(MSG_UPDATE_ORIENTATION_ANGLE);
			mHandler.sendEmptyMessage(MSG_UPDATE_ORIENTATION_ANGLE);
		}
	}
	
	@Override
	public void onPictureTaken(byte[] data) {
		super.onPictureTaken(data);
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
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public void finish() {
		super.finish();
	}
	
	
	
}
