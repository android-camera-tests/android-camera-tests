package se.birkler.samplecapturer.camera;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;

import se.birkler.samplecapturer.util.XLog;
import se.birkler.samplecapturer.camera.PreviewView;
import android.app.Activity;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

public class CaptureBaseActivity extends Activity implements PreviewView.PictureCallback, SensorEventListener {
	protected PreviewView viewfinderView;

    private ArrayBlockingQueue<PictureCaptureData> mCaptureDataQueue = new ArrayBlockingQueue<PictureCaptureData>(5);
	private SensorManager mSensorManager;
	private Sensor mAccelerometer;
	private Sensor mMagnetometer;
	private Sensor mGravity;
	protected float[] mMagnetometerValues;
	protected float[] mAccelerometerValues;
	protected float[] mGravityValues;
	
	protected static void drawQuad(Canvas c, float[] pts, int idx,Paint p) {
		Path path = new Path();
		path.moveTo(pts[idx+0],pts[idx+1]);
		path.lineTo(pts[idx+2],pts[idx+3]);
		path.lineTo(pts[idx+4],pts[idx+5]);
		path.lineTo(pts[idx+6],pts[idx+7]);
		path.close();
		c.drawPath(path, p);
	}
	
	/**
	 * Draws a rect in the canvas
	 * Not using Canvas.drawRect because it does not shadow inside of rect
	 * */
	protected static void drawRect(Canvas c, float left, float top, float right, float bottom,Paint p) {
		Path path = new Path();
		path.moveTo(left,top);
		path.lineTo(right,top);
		path.lineTo(right,bottom);
		path.lineTo(left,bottom);
		path.close();
		c.drawPath(path, p);
	}
	
	

	public CaptureBaseActivity() {
		super();
	    mThread = new Thread() {
	        private boolean mThreadRun = true;
			public void run() {
	            XLog.i("Starting picture data writing thread");
	            while (mThreadRun) {
	            	PictureCaptureData picData;
	    			try {
	    				picData = mCaptureDataQueue.take();
	    				XLog.d("Poping pic data from write queue and writing");
	    				picData.writeFile(CaptureBaseActivity.this);
	    				XLog.d("Write file done");
	    			} catch (InterruptedException e) {
	    				mThreadRun = false;
	    			}
	            }
			}
	    };
	    mThread.setPriority(Thread.MIN_PRIORITY);
	}
	private Thread mThread;

	
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
	    mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
	    mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
	    mGravity = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
	    mMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
	    mThread.setName("Picture data writer thread");
	    mThread.start();
	}
	
	@Override
	public void onDestroy() {
		super.onStop();
	    mThread.interrupt();
	    try {
			mThread.join(1000);
		} catch (InterruptedException e) {
		}
	}
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}
	
	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor == mAccelerometer) {
			mAccelerometerValues = event.values.clone();
		} 
		else if (event.sensor == mMagnetometer) {
			mMagnetometerValues = event.values.clone();
		}
		else if (event.sensor == mGravity) {
			mGravityValues = event.values.clone();
		}
	}
	    
	@Override
	protected void onResume() {
		super.onResume();
	    mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
	    mSensorManager.registerListener(this, mMagnetometer, SensorManager.SENSOR_DELAY_FASTEST);
	    mSensorManager.registerListener(this, mGravity, SensorManager.SENSOR_DELAY_FASTEST);
	} 
	
	@Override
	protected void onPause() {
		super.onPause();
	    mSensorManager.unregisterListener(this);
	}

	
	@Override
	public void onPictureTaken(byte[] data) {
		if (mCaptureDataQueue.remainingCapacity() > 0) {
			PictureCaptureData picData = new PictureCaptureData();
			picData.setCaptureTime();
			picData.setAccelerationSensorData(mAccelerometerValues);
			picData.setOrientationSensorData(mMagnetometerValues);
			picData.setPictureData(data.clone());
			XLog.d("Adding pic data to write queue");
			try {
				mCaptureDataQueue.add(picData);
			} catch (Exception e) {
				XLog.e("No space in write queue",e);
			}
		} else {
			XLog.e("No space in write queue");
		}
		
		BitmapFactory.Options opt = new BitmapFactory.Options();
		opt.inPreferQualityOverSpeed = true;
		BitmapRegionDecoder brd;
		try {
			brd = BitmapRegionDecoder.newInstance(data, 0, data.length, false);
			//Rect rect;
			//rect = new Rect(0,0,brd.getWidth()/3,brd.getHeight());
			//mRightBitmap = brd.decodeRegion(rect, opt);
			//rect = new Rect(brd.getWidth() - brd.getWidth()/3,0,brd.getWidth(),brd.getHeight());
			//mLeftBitmap = brd.decodeRegion(rect, opt);
		} catch (IOException e) {
			XLog.e("Region decoder doesn't want to cooperate",e);
		}
	}
		
}