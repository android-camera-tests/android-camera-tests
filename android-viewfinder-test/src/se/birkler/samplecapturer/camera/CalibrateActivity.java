package se.birkler.samplecapturer.camera;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;

import javax.vecmath.Point2f;
import javax.vecmath.Vector3f;

import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import se.birkler.samplecapturer.opencvutil.MatBitmapHolder;
import se.birkler.samplecapturer.util.XLog;
import se.birkler.samplecapturer.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Bitmap.Config;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Xfermode;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.ShutterCallback;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;




/**
 */
public final class CalibrateActivity extends CaptureBaseActivity {
	private ArrayList<CalibrationDataObservation> calibrationData = new ArrayList<CalibrationDataObservation>();
	
    Size mPreviewSize;
    Camera mCamera;
	float mFieldOfView = -1;
	
	private float mGravX;
	private float mGravY;
	private float mGravZ;
	int mResultSurfaceWidth;
	int mResultSurfaceHeight;

	int mCurrentCalibrationPoint = 0;

	private float mAverageFOV;

	
	final private ArrayList<PointF> calibrationPoints = new ArrayList<PointF>() {
		private static final long serialVersionUID = 1L;

	{
			add(new PointF( 0.00f, 0.00f));
			add(new PointF(+0.45f, 0.00f));
			add(new PointF(-0.45f, 0.00f));
			add(new PointF( 0.00f, 0.00f));
			add(new PointF( 0.00f,-0.35f));
			add(new PointF( 0.00f,+0.35f));
			add(new PointF( 0.00f, 0.00f));
			add(new PointF(-0.45f,-0.35f));
			add(new PointF(+0.45f,+0.35f));
	}};
	
	class CalibrationDataObservation {
		private Point2f viewXY; //X value in percent -50% to +50%
		private Vector3f gravVec;
		CalibrationDataObservation(float vx,float vy,float x,float y,float z) {
			viewXY = new Point2f(vx,vy);
			gravVec = new Vector3f(y, x, z);
		}
		
		float angle(CalibrationDataObservation other) {
			return gravVec.angle(other.gravVec);
		}
		float distance(CalibrationDataObservation other) {
			return viewXY.distance(other.viewXY);
		}
	}
	
	public void recordAndNextCalibrationPoint() {
		calibrationData.add(new CalibrationDataObservation(
				calibrationPoints.get(mCurrentCalibrationPoint).x,
				calibrationPoints.get(mCurrentCalibrationPoint).y,
				mGravY,
				mGravX,
				mGravZ));
		mCurrentCalibrationPoint++;
		if (mCurrentCalibrationPoint >= calibrationPoints.size()) {
			mCurrentCalibrationPoint = 0;
		}
		mAverageFOV = -1;
		float angleSum = 0;
		float distanceSum = 0;
		for (int i=0;i<calibrationData.size();i++) {
			for (int j=i+1;j<calibrationData.size();j++) {
				angleSum +=  calibrationData.get(i).angle(calibrationData.get(j));
				distanceSum += calibrationData.get(i).distance(calibrationData.get(j));
			}
		}
		mAverageFOV = (float) ((angleSum * 180.0f) / (Math.PI * distanceSum));
	}
	
	private PreviewView viewfinderView;
	private View statusRootView;
	private TextView statusView;
	private View resultView;
	private boolean hasSurface;
	private SurfaceView surfaceView;
	private Bitmap mBitmap;
	private TextView mDistanceTextView;

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
        	canvas.drawColor(Color.TRANSPARENT,Mode.CLEAR);
        	Paint paint = new Paint();
        	
        	paint.setColor(Color.YELLOW);
        	PointF calibrationPoint = calibrationPoints.get(mCurrentCalibrationPoint);
        	canvas.drawCircle(
        			mResultSurfaceWidth * (0.5f+calibrationPoint.x),
        			mResultSurfaceHeight / 2  + mResultSurfaceWidth * (calibrationPoint.y),
        			(float) (8.0f),
        			paint);
        	
        	paint.setColor(Color.BLUE);
        	paint.setStrokeWidth(4.0f);
        	
        	canvas.drawText(String.format("%.1f",mAverageFOV), 20.0f,20.0f, paint);
        	canvas.drawCircle(mResultSurfaceWidth /2 + mGravX * 10,mResultSurfaceHeight / 2  + mGravY* 10,(float) (5.0f + mGravZ*1.5f),paint);
		}
    }
    
    static native void findFeatures(long grayDataPtr, long mRgba);
    static native int findRectangles(long grayDataPtr,float[] rects, long mRgba);

    private MenuItem            mItemPreviewDebugOnOff;

    public boolean onCreateOptionsMenu(Menu menu) {
        XLog.i("onCreateOptionsMenu");
        mItemPreviewDebugOnOff = menu.add("Debug On/Off");
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        XLog.i("Menu Item selected " + item);
        if (item == mItemPreviewDebugOnOff)
            mDebugOutOn = !mDebugOutOn;
        return true;
    }
    
    
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.sampler);
		viewfinderView = (PreviewView) findViewById(R.id.viewfinder_view);
		viewfinderView.setProcessor(new OpenCvProcessor());
        Button startButton = (Button)findViewById(R.id.button_start);
        startButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				viewfinderView.takePicture(CalibrateActivity.this);
				
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
