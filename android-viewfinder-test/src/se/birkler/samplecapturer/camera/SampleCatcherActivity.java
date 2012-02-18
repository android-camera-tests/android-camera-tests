
package se.birkler.samplecapturer.camera;


import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import se.birkler.samplecapturer.opencvutil.MatBitmapHolder;
import se.birkler.samplecapturer.util.XLog;
import com.sizetool.samplecapturer.R;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Xfermode;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
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


/**
 */
public final class SampleCatcherActivity extends CaptureBaseActivity  implements ShutterCallback, PictureCallback {
	private View statusRootView;
	private TextView statusView;
	private View resultView;
	private boolean hasSurface;
	private SurfaceView surfaceView;
	private Bitmap mBitmap;
	private ImageView mLeftGuidanceView;
	private TextView mDistanceTextView;

    public static final int     VIEW_MODE_RGBA             = 0;
    public static final int     VIEW_MODE_GRAY             = 1;
    public static final int     VIEW_MODE_CANNY            = 2;
    public static final int     VIEW_MODE_CANNY_OVERLAY    = 3;
    public static final int     VIEW_MODE_FEATURES         = 5;
    public static final int     VIEW_MODE_GOOD_FEAT        = 6;
	public static final int     VIEW_MODE_RECTANGLES          = 7;
    private int           viewMode           = VIEW_MODE_RGBA;
	public boolean mDebugOutOn =false;
	private float mDistance = -1;

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
			
    	    if (mRgbaMatHolder == null){
    	    	Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    	    	mRgbaMatHolder = new MatBitmapHolder(bitmap);    	    
    	    }
    	    
    	    rgbaMat = mRgbaMatHolder.pin();
    	    
	    	canvas.drawColor(Color.TRANSPARENT,Mode.CLEAR);
    	    
    	    boolean drawRgb = false;
    	    
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
    	        //Imgproc.cvtColor(yuvData, mRgba, Imgproc.COLOR_YUV420sp2RGB, 4);
    	        rgbaMat.setTo(new Scalar(0,0,0,0));
    	        findFeatures(grayData.getNativeObjAddr(), rgbaMat.getNativeObjAddr());
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

    	    if (drawRgb) {
    	    	canvas.drawBitmap(mRgbaMatHolder.getBitmap(), (canvas.getWidth() - width) / 2, (canvas.getHeight() - height) / 2, null);
    	    }
	    	if (rect_points > 0) {
		        Paint paint = new Paint();
		        paint.setAntiAlias(true);
		        paint.setStrokeWidth(5.0f);
		        paint.setTextSize(20.0f);
		        paint.setShadowLayer(3.0f,0.0f,0.0f,Color.BLACK);
		        for (int k=rect_points;k>0;) {
		        	k--;
			        paint.setColor(Color.GREEN);
		        	drawRect(canvas,rectPoints,k*21,paint);
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
			        }
			        
			        
			        
		        }
	    	}
		}
    }
    
    static native void findFeatures(long grayDataPtr, long mRgba);
    static native int findRectangles(long grayDataPtr,float[] rects, long mRgba);

    private MenuItem            mItemPreviewRGBA;
    private MenuItem            mItemPreviewGray;
    private MenuItem            mItemPreviewCanny;
    private MenuItem            mItemPreviewCannyOverlay;
    private MenuItem            mItemPreviewFeatures;
    private MenuItem 			mItemPreviewRectangles;
    private Bitmap mLeftGuidanceBitmap;
	private MenuItem mItemPreviewDebugOnOff;
	private Handler mHandler;
	

    public boolean onCreateOptionsMenu(Menu menu) {
        XLog.i("onCreateOptionsMenu");
        mItemPreviewDebugOnOff = menu.add("Debug On/Off");
        mItemPreviewRectangles = menu.add("Find rectangles");
        //mItemPreviewRGBA = menu.add("Preview RGBA");
        mItemPreviewGray = menu.add("Preview GRAY");
        //mItemPreviewCanny = menu.add("Canny");
        mItemPreviewCannyOverlay = menu.add("Canny Overlay");
        mItemPreviewFeatures = menu.add("Find features");
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        XLog.i("Menu Item selected " + item);
        if (item == mItemPreviewDebugOnOff)
            mDebugOutOn = !mDebugOutOn;
        else if (item == mItemPreviewRGBA)
            viewMode = VIEW_MODE_RGBA;
        else if (item == mItemPreviewGray)
            viewMode = VIEW_MODE_GRAY;
        else if (item == mItemPreviewCanny)
            viewMode = VIEW_MODE_CANNY;
        else if (item == mItemPreviewCannyOverlay)
            viewMode = VIEW_MODE_CANNY_OVERLAY;
        else if (item == mItemPreviewFeatures)
            viewMode = VIEW_MODE_FEATURES;
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
				viewfinderView.takePicture(SampleCatcherActivity.this,SampleCatcherActivity.this);
				
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
