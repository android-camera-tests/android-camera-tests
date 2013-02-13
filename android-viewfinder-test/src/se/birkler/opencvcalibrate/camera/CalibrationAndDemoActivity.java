/**
 * Copyright 2012 Jorgen Birkler
 * jorgen@birkler.se
 *
 * Camera calibration and open cv demonstration applications
 *
 */

package se.birkler.opencvcalibrate.camera;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.opencv.android.OpenCVLoader;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Scalar;
import org.opencv.core.Core.MinMaxLocResult;
import org.opencv.features2d.DMatch;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.KeyPoint;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;

import se.birkler.opencvcalibrate.opencvutil.CaptureDataAction;
import se.birkler.opencvcalibrate.opencvutil.MatBitmapHolder;
import se.birkler.opencvcalibrate.opencvutil.PreviewBaseActivity;
import se.birkler.opencvcalibrate.opencvutil.PreviewView;
import se.birkler.opencvcalibrate.service.UploaderService;
import se.birkler.opencvcalibrate.util.XLog;
import se.birkler.opencvcalibrate.R;
import android.content.Context;
import android.content.Intent;
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
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


/**
 */
public final class CalibrationAndDemoActivity extends PreviewBaseActivity  implements PreviewView.PictureCallback {
	
	private static final int ACTION_CALIBRATE = 1;
	private static final int ACTION_CALIB_FILE_WRITE_DONE = 2;

	protected static final int MSG_UPDATE_ORIENTATION_ANGLE = 1;
	protected static final int MSG_FOUND_CALIBRATION_CIRCLES= 2;
	

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

	CalibrationEntries mCalibrationEntriesViewFinder = new CalibrationEntries();
	CalibrationEntries mCalibrationEntriesSnapshot = new CalibrationEntries();

	abstract class Processor {
		abstract boolean process( Mat gray, Mat yuv, Mat rgba,Canvas overlay); 
		String getName() {
			String classname = this.getClass().getCanonicalName();
			int idx = classname.lastIndexOf('.');
		    if (idx != -1)
		       classname = classname.substring(idx + 1, classname.length());
		    classname = classname.replaceFirst("/Processor/", "");
		    return classname;
		}
		void onSnap() {
			
		}
	}
	
	
	class GrayProcessor extends Processor {
		boolean process(Mat grayData, Mat yuv, Mat rgbaMat, Canvas overlay) {
	        Imgproc.cvtColor(grayData, rgbaMat, Imgproc.COLOR_GRAY2RGBA, 4);
			return true;
		}
	}

	class RgbProcessor extends Processor {
		boolean process(Mat grayData, Mat yuvData, Mat rgbaMat, Canvas overlay) {
			Imgproc.cvtColor(yuvData, rgbaMat, Imgproc.COLOR_YUV420sp2RGB, 4);
			return true;
		}
	}
	
	class CannyProcessor extends Processor {
		private MatBitmapHolder mIntermediateMatHolder;

		boolean process(Mat grayData, Mat yuvData, Mat rgbaMat, Canvas canvas) {
			//This demonstrates how to pass a "gray" Mat to opencv that we use as a alpha8 mask in Android
			//Thus we can overlay edge detection data on top of the view finder efficiently
			if (mIntermediateMatHolder == null) {
				Bitmap b = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Bitmap.Config.ALPHA_8);
				mIntermediateMatHolder = new MatBitmapHolder(b);
				
			}
			Mat result = mIntermediateMatHolder.pin();
		    Imgproc.Canny(grayData, result, 80, 100);
			mIntermediateMatHolder.unpin(result);

		   	Paint p = new Paint();
		   	p.setColor(Color.YELLOW);
		   	canvas.drawBitmap(mIntermediateMatHolder.getBitmap(),0,0,p);
			return false;
		}
	}
	
	class DetectorProcessor extends Processor {
		protected FeatureDetector detector = null;
		protected DescriptorExtractor extractor = null;
		DescriptorMatcher matcher = null;
		
		MatOfKeyPoint keypoints = new MatOfKeyPoint();
		Mat descriptors = new Mat();
		Mat mOnSnapDescriptors;
		MatOfKeyPoint mOnSnapKeypoints;
		final Scalar keypointColor = new Scalar(-1);
		int mdetector;
		String mdetectorName;
		int mdescriptor;
		String mdescriptorName;
		int mmatcher;
		String mMatcherName;
		DetectorProcessor(int detector,String detectorName,int descriptor, String descriptorName,int _matcher,String matcherName) {
			mdetector = detector;
			mdetectorName = detectorName;
			mdescriptor = descriptor;
			mdescriptorName = descriptorName;
			mmatcher = _matcher;
			mMatcherName = matcherName;
		}
		void applyParameters() {
			
		}
		
		void createIfNotCreated() {
			if (detector == null && mdetector >= 0) {
				detector = FeatureDetector.create(mdetector);
			}
			if (extractor == null && mdescriptor >= 0) {
				extractor = DescriptorExtractor.create(mdescriptor);
			}
			if (matcher == null && mmatcher >= 0) {
				matcher = DescriptorMatcher.create(mmatcher);
			}
			applyParameters();
		}

		void onSnap() {
			mOnSnapDescriptors = descriptors.clone();
			mOnSnapKeypoints = new MatOfKeyPoint(keypoints.clone());
		}
		boolean process(Mat grayData, Mat yuvData, Mat rgbaMat, Canvas canvas) {
			createIfNotCreated();
			if (detector != null) {
				detector.detect(grayData, keypoints);
				//Features2d.drawKeypoints(grayData, keypoints, rgbaMat,keypointColor, Features2d.DRAW_OVER_OUTIMG);
				
			   	Paint p = new Paint();
			   	p.setColor(Color.MAGENTA);
			   	p.setStrokeWidth(2.0f);
			   	p.setStyle(Paint.Style.STROKE);
			   	KeyPoint list[] = keypoints.toArray();
			   	for (int i=0;i<list.length;i++) {
			   		canvas.drawCircle((float)list[i].pt.x, (float)list[i].pt.y, list[i].size, p);
			   	}
				//Features2d.drawKeypoints(grayData, keypoints, rgbaMat);
			   	
			   	if (extractor != null) {
					extractor.compute(grayData, keypoints, descriptors);
					if (matcher != null && mOnSnapDescriptors != null && !mOnSnapDescriptors.empty())
					{	   	
						List<MatOfDMatch> matches = new Vector<MatOfDMatch>();
						matcher.knnMatch(descriptors, mOnSnapDescriptors, matches, 3);
						for (MatOfDMatch matOfmatch : matches) {
							for (DMatch match : matOfmatch.toList()) {
								if (match.distance < 80) {
									org.opencv.core.Point from = mOnSnapKeypoints.toArray()[match.trainIdx].pt;
									org.opencv.core.Point to = keypoints.toArray()[match.queryIdx].pt;
									canvas.drawLine((float)from.x,(float)from.y,(float)to.x,(float)to.y, p);
								}
							}
						}
					}
			   	}
			}
			return false;
		}
		String getName() {
			String res = super.getName();
			if (mdetector >= 0) {
				res += " " + mdetectorName;
			}
			if (mdescriptor >= 0) {
				res += "+" + mdescriptorName;
			}
			if (mmatcher >= 0) {
				res += "[" + mMatcherName+"]";
			}
			return  res;
		}
	}

	
	class NlMeansDenoise extends Processor {
		boolean process(Mat grayData, Mat yuvData, Mat rgbaMat, Canvas canvas) {
			Bitmap b = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Bitmap.Config.ARGB_8888);
	    	MatBitmapHolder tempMatHolder = new MatBitmapHolder(b);    	    
	    	Mat tempMat = tempMatHolder.pin();
	    	//Imgproc.cvtColor(yuvData, tempMat, Imgproc.COLOR_YUV420sp2RGB, 4);
	    	Photo.fastNlMeansDenoising(grayData, tempMat, 3.0f, 7, 21);
	    	tempMatHolder.unpin(tempMat);
	    	canvas.drawColor(Color.BLACK);
   	    	Paint p = new Paint();
   	    	p.setColor(Color.CYAN);
   	    	canvas.drawBitmap(tempMatHolder.getBitmap(),0,0,p);
   	    	return false;
	    }

	}
	
	Processor processors[] = {
			new GrayProcessor(),
			new RgbProcessor(),
			new CannyProcessor(),
			new DetectorProcessor(FeatureDetector.FAST,"FAST",-1,"",-1,""),
			new DetectorProcessor(FeatureDetector.BRISK,"BRISK",DescriptorExtractor.BRISK,"BRISK",DescriptorMatcher.BRUTEFORCE_HAMMING,"HAMM"),
			new NlMeansDenoise(),
			
	};
	int processorNum = 0;
	
	/**
	 * This is the meat of the open cv processing
	 * It checks current view mode and does some operations on the grayMat with is view finder data directly.
	 * 
	 * The android graphics pipeline is setup as follows (hardware acceleration is requested in manifest):
	 * 
	 *        __________
	 *       /         /_         
	 *      /         / /_        
	 *     /         / / /     
	 *    /         /------ Android views     
	 *   /         / /----- Output surface view (mResultSurfaceView)    
	 *  /         / / /---- Camera surface view (viewfinderView.mSurfaceView)
	 * /_________/ / /         
	 *  /_________/ / 
	 *   /_________/
	 * 
	 * 
	 * Below demonstrates a more efficient way of obtaining a Mat from the viewfinder gray data without any copying or color conversion.
	 * This is achieved mainly by introducing a new classes; @see MatBitmapHolder and @see MatByteBufferWrapper
	 * 
	 * Two main advantages: 
	 * 1. FPS is dramatically increased from the stock android opencv example. 
	 * 2. No need to use native camera lib making it compatable with all android devices.
	 * 
	 * General setup is:
	 * - Obtain Mat wrappers for gray viewfinder data
	 * - Obtain Mat for output RGB bitmap (can use opencv graphical routines to write to android bitmap)
	 * - Surface is normally made transparent by filling with #00000000
	 * - Do opencv functions on gray viewfinder data
	 * - Output result or rgb bitmap Mat
	 * - Draw rgbbitmap data to surface view
	 * - Draw any other output on surface view canvas
	 * - Release Mat wrappers
	 * .
	 * 
	 * 
	 * Demonstrators:
	 * - Color conversion
	 * - Edge detection
	 * - Feature detection with feature points highlighted in viewfinder
	 * - Call JNI function for further processing
	 * - Drawing a ALPHA8 bitmap with a Paint
	 * - Call calibration routines
	 * - Call dyi rectangle detector (C++ code)
	 * .
	 */

    class OpenCvProcessor implements PreviewView.OpenCVProcessor {
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
    	    
    	    
	        MatOfPoint2f centersCalibCircles = new MatOfPoint2f();
			boolean patternWasFound = false;
			
	    	drawRgb=processors[processorNum].process(grayData, yuvData, rgbaMat, canvas);
/*    	        
    	    case VIEW_MODE_CALIB_ACIRCLES:
    	        rgbaMat.setTo(new Scalar(0,0,0,0));
    	        Size patternSize = CalibrationEntries.Pattern_ASYMMETRIC_CIRCLES_GRID_SIZE;
    	        patternWasFound = Calib3d.findCirclesGridDefault(grayData, patternSize, centersCalibCircles,Calib3d.CALIB_CB_ASYMMETRIC_GRID);
				Calib3d.drawChessboardCorners(rgbaMat, patternSize, centersCalibCircles, patternWasFound);
				
				if (patternWasFound && mOrientationRotationMatrix != null) {
					int addedIdx = mCalibrationEntriesViewFinder.addEntry(mOrientationRotationMatrix, centersCalibCircles);
					if (addedIdx >= 0) {
						mHandler.sendEmptyMessage(MSG_FOUND_CALIBRATION_CIRCLES);
						Log.d("CALIB", String.format("Added calibration entry at %d tot: %d", addedIdx,mCalibrationEntriesViewFinder.getNumEntries()));
						mViewfinderView.takePicture(CalibrationAndDemoActivity.this);
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
    	    } */
			
    	    mRgbaMatHolder.unpin(rgbaMat);

	        Paint paint = new Paint();
	        paint.setAntiAlias(true);
	        paint.setStrokeWidth(5.0f);
	        paint.setTextSize(20.0f);
	        paint.setShadowLayer(3.0f,0.0f,0.0f,Color.BLACK);
    	    if (drawRgb) {
    	    	canvas.drawBitmap(mRgbaMatHolder.getBitmap(), (canvas.getWidth() - width) / 2, (canvas.getHeight() - height) / 2, null);
    	    	//double fov = mFieldOfViewKalmanFilter.getDiagFOV();
		        //String s = String.format("fov:%.3f", fov*180/Math.PI);
		        paint.setColor(Color.WHITE);
	        	//canvas.drawText(s,20,20,paint);
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
			        double pixelArea;
			        if (epsilon < 1.0) {
				        pixelArea = rectPoints[19+k*21];
				        double paperHeightPixels = Math.sqrt(pixelArea*8.5f/11.0f); 
				        float fov = mViewfinderView.getFOVPerPixel();
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

    private Bitmap 				mLeftGuidanceBitmap;
	private MenuItem			mItemPreviewDebugOnOff;
	private Handler				mHandler;
	
	private long				prevInfoTextUpdateTicks = 0;
	private View mInstructionHelpView;
	private boolean mAlreadySavedCalibrationData;
	

    public boolean onCreateOptionsMenu(Menu menu) {
    	for (int i=0;i < processors.length;i++) {
    		MenuItem item = menu.add(processors[i].getName());
    		item.setIntent(new Intent(Integer.toString(i)));
    	}
        mItemPreviewDebugOnOff = menu.add(R.string.option_debug_on_off);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        XLog.i("Menu Item selected " + item);
        if (item.getIntent() != null) {
        	processorNum = Integer.parseInt(item.getIntent().getAction()); 
        }
        else if (item == mItemPreviewDebugOnOff)
            mDebugOutOn = !mDebugOutOn;
        
        return true;
    }
    
     

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.sampler);
		mLeftGuidanceView = (ImageView)findViewById(R.id.imageview_leftprevious);
		mViewfinderView = (PreviewView) findViewById(R.id.viewfinder_view);
		mStatusRootView = (View) findViewById(R.id.status_rootview);
		mInstructionHelpView = (View) findViewById(R.id.calibration_pattern_help_view);
		mViewfinderView.setProcessor(new OpenCvProcessor());
		mInfoTextView = (TextView)findViewById(R.id.textDistance);
        Button startButton = (Button)findViewById(R.id.button_start);
        Button optionsButton = (Button)findViewById(R.id.button_process);
        
        mStatusRootView.setVisibility(View.INVISIBLE);
        mInstructionHelpView.setVisibility(View.VISIBLE);
        
        startButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mOrientationRotationMatrixReference = new float[9];
				System.arraycopy(mOrientationRotationMatrix, 0, mOrientationRotationMatrixReference, 0, mOrientationRotationMatrix.length);
				mViewfinderView.takePicture(CalibrationAndDemoActivity.this);
			}
		});
        
        optionsButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				openOptionsMenu();
			}
		});
        
        
        mHandler = new Handler() {
        	@Override
			public void handleMessage(Message msg) {
        		if (msg.what == MSG_UPDATE_ORIENTATION_ANGLE) {
        			updateRotationText();
        		}
        		if (msg.what == MSG_FOUND_CALIBRATION_CIRCLES) {
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
	
	protected void initCaptureData(CaptureData picData, byte[] data) {
		picData.setCaptureTime();
		picData.setGravitySensorData(mGravityValuesOnShutter);
		picData.setMagneticFieldSensorData(mMagnetometerValuesOnShutter);
		picData.setPictureData(data);
	}
	
	
	
	@Override
	public void onPictureTaken(byte[] data) {
		super.onPictureTaken(data);
		if (false /*mViewMode == VIEW_MODE_CALIB_ACIRCLES*/) {
			PictureCaptureDataAnalyzeCalibration picData = new PictureCaptureDataAnalyzeCalibration(mCalibrationEntriesSnapshot);
			initCaptureData(picData, data);
			addToCaptureQueue(picData);
		}
		else {
			processors[processorNum].onSnap();
			PictureCaptureDataWriteToDisk picData = new PictureCaptureDataWriteToDisk(createNewPictureFileHandle());
			initCaptureData(picData, data);
			addToCaptureQueue(picData);
		}
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
	protected void onCaptureQueueNotify(CaptureDataAction actionDone) {
		if (actionDone instanceof PictureCaptureDataAnalyzeCalibration) {
			//We have found first or another calibration patters
	        mStatusRootView.setVisibility(View.VISIBLE);
	        mInstructionHelpView.setVisibility(View.INVISIBLE);

			CameraCalibrationData data = mCalibrationEntriesSnapshot.getCalibrationData();
			if (data != null) {
				//We have calibration data, if this is the first well fire off an action to save it to file
				String text = "Calibration: " + data.formatCalibrationDataString();
				int duration = Toast.LENGTH_LONG;
				Toast toast = Toast.makeText(this, text, duration);
				toast.show();
				
				
				if (!mAlreadySavedCalibrationData) {
					mAlreadySavedCalibrationData = true;
					CalibrationCaptureDataWriteToDisk calibData = new CalibrationCaptureDataWriteToDisk(createNewCalibrationDataFileHandle(),data);
					initCaptureData(calibData, null);
					addToCaptureQueue(calibData);
				}
			}
		}
		else if (actionDone instanceof CalibrationCaptureDataWriteToDisk) {
			//We have now saved the json calibration data to disc; kick off the uploader
			Intent intent = new Intent(this, UploaderService.class);
			startService(intent);
		}
	}
	
	@Override
	public void finish() {
		super.finish();
	}
	
	protected File createNewPictureFileHandle() { 
		CharSequence timeString = String.format("%020d",System.currentTimeMillis());
		String name = String.format("st%s.jpg",timeString);
		File file = new File(getExternalFilesDir("pictures"), name);
		return file;
	}
	
	protected File createNewCalibrationDataFileHandle() { 
		CharSequence timeString = String.format("%020d",System.currentTimeMillis());
		String name = String.format("st%s.json",timeString);
		File file = new File(getDir(UploaderService.UPLOAD_QUEUE_DIR,Context.MODE_PRIVATE), name);
		return file;
	}
	
	static {
	    if (!OpenCVLoader.initDebug()) {
	        // Handle initialization error
	    }
	}
}
