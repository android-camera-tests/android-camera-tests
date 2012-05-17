
package se.birkler.samplecapturer.camera;


import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.core.Core.MinMaxLocResult;
import org.opencv.imgproc.Imgproc;
import se.birkler.samplecapturer.opencvutil.MatBitmapHolder;
import se.birkler.samplecapturer.util.XLog;
import se.birkler.samplecapturer.R;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Xfermode;
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
import se.birkler.samplecapturer.camera.PreviewView;






/**
 */
public final class SampleCatcherActivity extends CaptureBaseActivity  implements PreviewView.PictureCallback {
	private View statusRootView;
	private TextView statusView;
	private View resultView;
	private boolean hasSurface;
	private SurfaceView surfaceView; 
	private Bitmap mBitmap;
	private ImageView mLeftGuidanceView;
	private TextView mDistanceTextView;

    public static final int     VIEW_MODE_RGBA             = 0;
    public static final int     VIEW_MODE_CALIBRATION_CIRCLES             = 20;
    
    public static final int     VIEW_MODE_GRAY             = 1;
    public static final int     VIEW_MODE_CANNY            = 2;
    public static final int     VIEW_MODE_CANNY_OVERLAY    = 3;
    public static final int     VIEW_MODE_FEATURES         = 5;
    public static final int     VIEW_MODE_FEATURES_ORB         = 6;
    public static final int     VIEW_MODE_FEATURES_MSER        = 7;
    public static final int     VIEW_MODE_FEATURES_FAST         = 8;
    public static final int     VIEW_MODE_FEATURES_SURF         = 9;
    public static final int     VIEW_MODE_GOOD_FEAT        = 10;
	public static final int     VIEW_MODE_RECTANGLES          = 11;
    private int           viewMode           = VIEW_MODE_CALIBRATION_CIRCLES;
	public boolean mDebugOutOn =false;
	private float mDistance = -1;
	
	FOVKalmanFilter fovFiler = new FOVKalmanFilter();
	static final int Pattern_CHESSBOARD = 1;
	static final int Pattern_CIRCLES_GRID = 2;
	static final int Pattern_ASYMMETRIC_CIRCLES_GRID = 3;

	
	private void calculateCalibrationObjectPoints(Size boardSize, float squareSize,  int patternType )
    {
		Mat corners = new Mat((int) (boardSize.height * boardSize.width),1,CvType.CV_32FC2);
		float data[] = new float[2];
		switch(patternType)
		{
			case Pattern_CHESSBOARD:
			case Pattern_CIRCLES_GRID:
				{
					int k=0;
					for( int i = 0; i < boardSize.height; ++i ) {
						for( int j = 0; j < boardSize.width; ++j ) {
							data[0] = (float)( j*squareSize );
							data[1] = (float)( i*squareSize );
							corners.put(k,0, data);
							k++;
						}
					}
				}
				break;
			
			case Pattern_ASYMMETRIC_CIRCLES_GRID:
				{
					int k=0;
					for( int i = 0; i < boardSize.height; i++ ) {
						for( int j = 0; j < boardSize.width; j++ ) {
							data[0] = (float)( (2*j + i % 2)*squareSize );
							data[1] = (float)( i*squareSize );
							corners.put(k,0, data);
							k++;
						}
					}
				}
				break;
		}
	}
	

    class OpenCvProcessor implements PreviewView.OpenCVProcessor {
		private Paint mPaint;
		private MatBitmapHolder mRgbaMatHolder;
		private MatBitmapHolder mIntermediateMatHolder;
		
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
	        if (!fovFiler.isConfigured()) {
	        	fovFiler.configure(width, height);
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
			
			
			switch (viewMode) {
    	    case VIEW_MODE_GRAY:
    	        Imgproc.cvtColor(grayData, rgbaMat, Imgproc.COLOR_GRAY2RGBA, 4);
    	        drawRgb = true;
    	        break;
    	    case VIEW_MODE_RGBA:
    	        //Imgproc.cvtColor(yuvData, mRgba, Imgproc.COLOR_YUV420sp2RGB, 4);
    	        break;
    	    case VIEW_MODE_CANNY:
    	    case VIEW_MODE_CANNY_OVERLAY:
    			if (mIntermediateMatHolder == null) {
    				Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);
    				mIntermediateMatHolder = new MatBitmapHolder(b);
    				
    			}
    	    	Mat result = mIntermediateMatHolder.pin();
    	        Imgproc.Canny(grayData, result, 80, 100);
    	    	mIntermediateMatHolder.unpin(result);

    	    	if (viewMode == VIEW_MODE_CANNY) { 
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
    	        findFeatures(viewMode - VIEW_MODE_FEATURES, grayData.getNativeObjAddr(), rgbaMat.getNativeObjAddr());
    	        drawRgb = true;
    	        break;
    	        
    	    case VIEW_MODE_CALIBRATION_CIRCLES:
    	        rgbaMat.setTo(new Scalar(0,0,0,0));
    	        //findCalibrationCircles(grayDataPtr, points, mRgba)
    	        Size patternSize = new Size(4,11);
    	        patternWasFound = Calib3d.findCirclesGridDefault(grayData, patternSize, centersCalibCircles,Calib3d.CALIB_CB_ASYMMETRIC_GRID);
				Calib3d.drawChessboardCorners(rgbaMat, patternSize, centersCalibCircles, patternWasFound);
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
    	    	double fov = fovFiler.getDiagFOV();
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
		        	draw4Corner(canvas,rectPoints,k*21,paint);
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
			        	
			        	fovFiler.predict();
			        	fovFiler.updateOrientation(mAccelerometerValues);
			        	fovFiler.update(centerX - grayData.cols(), centerY - grayData.rows(), epsilon*50, epsilon * 50);
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
    private Bitmap mLeftGuidanceBitmap;
	private MenuItem mItemPreviewDebugOnOff;
	private Handler mHandler;
	

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
            viewMode = VIEW_MODE_CALIBRATION_CIRCLES;
        else if (item == mItemPreviewRGBA)
            viewMode = VIEW_MODE_RGBA;
        else if (item == mItemPreviewGray)
            viewMode = VIEW_MODE_GRAY;
        else if (item == mItemPreviewCanny)
            viewMode = VIEW_MODE_CANNY;
        else if (item == mItemPreviewCannyOverlay)
            viewMode = VIEW_MODE_CANNY_OVERLAY;
        else if (item == mItemPreviewFeaturesOrb)
            viewMode = VIEW_MODE_FEATURES_ORB;
        else if (item == mItemPreviewFeaturesMser)
            viewMode = VIEW_MODE_FEATURES_MSER;
        else if (item == mItemPreviewFeaturesFast)
            viewMode = VIEW_MODE_FEATURES_FAST;
        else if (item == mItemPreviewFeaturesSurf)
            viewMode = VIEW_MODE_FEATURES_SURF;
        else if (item == mItemPreviewRectangles)
            viewMode = VIEW_MODE_RECTANGLES;
        return true;
    }
    
     

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.sampler);
		mLeftGuidanceView = (ImageView)findViewById(R.id.imageview_leftprevious);
		viewfinderView = (PreviewView) findViewById(R.id.viewfinder_view);
		statusRootView = (View) findViewById(R.id.status_rootview);
		viewfinderView.setProcessor(new OpenCvProcessor());
		mDistanceTextView = (TextView)findViewById(R.id.textDistance);
        Button startButton = (Button)findViewById(R.id.button_start);
        startButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				viewfinderView.takePicture(SampleCatcherActivity.this);
				
			}
		});
        mHandler = new Handler() {
        	@Override
			public void handleMessage(Message msg) {
        		
        	}
        };
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
