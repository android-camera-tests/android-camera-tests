package se.birkler.opencvcalibrate.camera;

import java.util.concurrent.ArrayBlockingQueue;

import se.birkler.opencvcalibrate.camera.PreviewView;
import se.birkler.opencvcalibrate.util.XLog;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Window;
import android.view.WindowManager;

public class CaptureBaseActivity extends Activity implements PreviewView.PictureCallback, SensorEventListener {
	protected static final int MSG_NOTIFY_ACTIVITY = 0;

	protected PreviewView mViewfinderView;

    private ArrayBlockingQueue<PictureCaptureData> mCaptureDataQueue = new ArrayBlockingQueue<PictureCaptureData>(5);
	private SensorManager mSensorManager;
	private Sensor mMagnetometer;
	private Sensor mGravity;
	protected float[] mMagnetometerValues;
	protected float[] mGravityValues;
	protected float[] mMagnetometerValuesOnShutter;
	protected float[] mGravityValuesOnShutter;
	private Handler				mNotifyActivityHandler;

	
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
	            XLog.i("Starting picture snapshot data thread");
	            while (mThreadRun) {
	            	PictureCaptureData picData;
	    			try {
	    				picData = mCaptureDataQueue.take();
	    				XLog.d("Poping pic data from queue and executing action");
	    				boolean notifyActivity = picData.execute(CaptureBaseActivity.this);
	    				XLog.d("Action done");
	    				if (notifyActivity) {
	    					mNotifyActivityHandler.removeMessages(MSG_NOTIFY_ACTIVITY);
	    					mNotifyActivityHandler.sendEmptyMessage(MSG_NOTIFY_ACTIVITY);
	    				}
	    			} catch (InterruptedException e) {
	    				mThreadRun = false;
	    			}
	            }
			}
	    };
	    mThread.setPriority(Thread.MIN_PRIORITY);
	}
	private Thread mThread;
	
	protected void onCaptureQueueNotify() {
	}
	
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		mNotifyActivityHandler = new Handler() {
        	@Override
			public void handleMessage(Message msg) {
        		if (msg.what == MSG_NOTIFY_ACTIVITY) {
        			onCaptureQueueNotify();
        		}
        	}


        };
		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
	    mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
	    mGravity = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
	    if (mGravity == null) {
	    	mGravity = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
	    }
		    
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
		if (event.sensor == mMagnetometer) {
			mMagnetometerValues = event.values.clone();
		}
		else if (event.sensor == mGravity) {
			mGravityValues = event.values.clone();
		}
	}
	    
	@Override
	protected void onResume() {
		super.onResume();
	    mSensorManager.registerListener(this, mMagnetometer, SensorManager.SENSOR_DELAY_FASTEST);
	    mSensorManager.registerListener(this, mGravity, SensorManager.SENSOR_DELAY_FASTEST);
	} 
	
	@Override
	protected void onPause() {
		super.onPause();
	    mSensorManager.unregisterListener(this);
	}

	
	protected boolean addToCaptureQueue(PictureCaptureData picData, byte[] data) {
		if (mCaptureDataQueue.remainingCapacity() > 0) {
			picData.setCaptureTime();
			picData.setGravitySensorData(mGravityValuesOnShutter);
			picData.setMagneticFieldSensorData(mMagnetometerValuesOnShutter);
			if (mGravityValuesOnShutter != null && mMagnetometerValuesOnShutter != null) {
				float R[] = new float[9];
				float I[] = new float[9];
				SensorManager.getRotationMatrix(R, I, mGravityValuesOnShutter, mMagnetometerValuesOnShutter);
				picData.setOrientationMatrix(R);
			}
			picData.setPictureData(data.clone());
			XLog.d("Adding pic data to write queue");
			try {
				mCaptureDataQueue.add(picData);
				return true;
			} catch (Exception e) {
				XLog.e("No space in write queue",e);
			}
		} else {
			XLog.e("No space in write queue");
		}
		return false;
	}
	
	@Override
	public void onPictureTaken(byte[] data) {
	}

	@Override
	public void onShutter() {
		if (mMagnetometerValues != null) {
			mMagnetometerValuesOnShutter = mMagnetometerValues.clone();
		}
		if (mGravityValues != null) {
			mGravityValuesOnShutter = mGravityValues.clone();
		}
	}
}