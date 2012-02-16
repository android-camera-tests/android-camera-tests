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

package com.sizetool.accelcalibration.camera;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.zip.GZIPOutputStream;

import javax.vecmath.Vector3f;

import org.codehaus.jackson.map.ObjectMapper;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.sizetool.accelcalibration.R;
import com.sizetool.accelcalibration.util.XLog;




/**
 * The barcode reader activity itself. This is loosely based on the
 * CameraPreview example included in the Android SDK.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */
public final class CalibrationActivity extends Activity  implements ShutterCallback, PictureCallback, SensorEventListener {
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
	class MetaData {
		public MetaData() {
			accel = new Vector3f();
			magnetic = new Vector3f();
			orientation = new float[3];
		}
		String user;
		String deviceSerialNum;
		String accelSensorName;
		String magneticSensorName;
		String cameraStats;
		Vector3f accel;
		Vector3f magnetic;
		java.util.Date captureTime;
		int timeOfDay; //Hour of the day in local time (
		String projectId;
		String sequenceId;
		public String deviceBrand;
		public String deviceBoard;
		public String deviceName;
		public String deviceDisplayName;
		public String deviceManufacturer;
		public String deviceVersionRelease;
		public float[] orientation;
	};
	MetaData mMetaData = new MetaData();

	static void drawRect(Canvas c, float[] pts,int idx, Paint p) {
		Path path = new Path();
		path.moveTo(pts[idx+0],pts[idx+1]);
		path.lineTo(pts[idx+2],pts[idx+3]);
		path.lineTo(pts[idx+4],pts[idx+5]);
		path.lineTo(pts[idx+6],pts[idx+7]);
		path.close();
		c.drawPath(path, p);
	}

	private PreviewView viewfinderView;
	private View statusRootView;
	private TextView statusView;
	private View resultView;
	private boolean hasSurface;
	private SurfaceView surfaceView;
	private Bitmap mBitmap;
	private ImageView mLeftGuidanceView;
	private TextView mDistanceTextView;

    public static final int     VIEW_MODE_RGBA     = 0;
    public static final int     VIEW_MODE_GRAY     = 1;
    public static final int     VIEW_MODE_CANNY    = 2;
    public static final int     VIEW_MODE_CANNY_OVERLAY    = 3;
    public static final int     VIEW_MODE_FEATURES = 5;
    public static final int     VIEW_MODE_GOOD_FEAT = 6;
	private static final int VIEW_MODE_RECTANGLES = 7;

    private int           viewMode           = VIEW_MODE_RGBA;
	public boolean mDebugOutOn =false;
	private float mDistance = -1;



    private MenuItem            mItemPreviewRGBA;
    private MenuItem            mItemPreviewGray;
    private MenuItem            mItemPreviewCanny;
    private MenuItem            mItemPreviewCannyOverlay;
    private MenuItem            mItemPreviewFeatures;
    private MenuItem 			mItemPreviewRectangles;
	private Bitmap mLeftGuidanceBitmap;
	private MenuItem mItemPreviewDebugOnOff;
	private Handler mHandler;
	private Sensor mMagnetometer;
	private Bitmap mRightBitmap;
	private Bitmap mLeftBitmap;
	

    public boolean onCreateOptionsMenu(Menu menu) {
        XLog.i("onCreateOptionsMenu");
        mItemPreviewDebugOnOff = menu.add("Debug On/Off");
        mItemPreviewRectangles = menu.add("Find rectangles");
        mItemPreviewRGBA = menu.add("Preview RGBA");
        mItemPreviewGray = menu.add("Preview GRAY");
        mItemPreviewCanny = menu.add("Canny");
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
		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.sampler);
		mLeftGuidanceView = (ImageView)findViewById(R.id.imageview_leftprevious);
		viewfinderView = (PreviewView) findViewById(R.id.viewfinder_view);
		statusRootView = (View) findViewById(R.id.status_rootview);
		mDistanceTextView = (TextView)findViewById(R.id.textDistance);
		
        Button startButton = (Button)findViewById(R.id.button_start);
        startButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				//viewfinderView.takePicture(CalibrationActivity.this,CalibrationActivity.this);
				viewfinderView.recordAndNextCalibrationPoint();
			}
		});
        Button captureButton = (Button)findViewById(R.id.button_capture);
        captureButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				viewfinderView.takePicture(CalibrationActivity.this,CalibrationActivity.this);
			}
		});
        mHandler = new Handler() {
        	@Override
			public void handleMessage(Message msg) {
        		
        	}
        };
        /*
        mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				if (mDistance > 0) {
	        	    String distanceText = String.format("%.3fm", mDistance);
	        	    mDistanceTextView.setText(distanceText);
				}
				else {
					mDistanceTextView.setText("");
				}
        	    mHandler.postDelayed(this,1000);
			}
        },1000); */
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }


    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void onSensorChanged(SensorEvent event) {
    	synchronized(mMetaData) {
			if (event.sensor == mAccelerometer) {
				viewfinderView.setXYZ(event.values[0],event.values[1],event.values[2]);
				mMetaData.accel.set(event.values);
			} 
			else if (event.sensor == mMagnetometer) {
				mMetaData.magnetic.set(event.values);
			}
    	}
    }
        
	@Override
	protected void onResume() {
		super.onResume();
	    mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
	} 

	@Override
	protected void onPause() {
		super.onPause();
        mSensorManager.unregisterListener(this);
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
		//TODO: This should be moved to a queue and file writing should be done on low prio process, pic takes and meta dat should be cpatured in one object and put in queue
		synchronized (mMetaData) {
			mMetaData.captureTime = new Date(System.currentTimeMillis());
			mMetaData.timeOfDay = (int) System.currentTimeMillis();
			//mMetaData.deviceSerialNum = android.os.Build.SERIAL;
			mMetaData.deviceBrand = android.os.Build.BRAND;
			mMetaData.deviceBoard = android.os.Build.BOARD;
			mMetaData.deviceName = android.os.Build.DEVICE;
			mMetaData.deviceDisplayName = android.os.Build.DISPLAY;
			mMetaData.deviceManufacturer = android.os.Build.MANUFACTURER;
			mMetaData.deviceVersionRelease = android.os.Build.VERSION.RELEASE;
			float[] matrixR = new float[9];
			float[] matrixI = new float[9];
			float[] gravVal = new float[3];
			float[] magVal = new float[3];
			mMetaData.accel.get(gravVal);
			mMetaData.magnetic.get(magVal);
			SensorManager.getRotationMatrix(matrixR, matrixI, gravVal, magVal);
			mMetaData.orientation = new float[3];
			SensorManager.getOrientation(matrixR, mMetaData.orientation);
			
			CharSequence timeString = String.format("%020d",System.currentTimeMillis());
			String name = String.format("st%s.jpg",timeString);
			File file = new File(getExternalFilesDir("pictures"), name);
			ObjectMapper mapper = new ObjectMapper();
			//mapper.configure(Featur\\\\\\\\\\\\\\]]]tate)configure(SerializationConfig.Feature., false);
			try {
				byte json[] = mapper.writeValueAsBytes(mMetaData);
				ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
				GZIPOutputStream zos = new GZIPOutputStream(bos);
				zos.write(json);
				bos.close();
				final byte magic[] = {'J','B','0','1',0};
				ByteBuffer lengthbuf = ByteBuffer.allocateDirect(4);
				lengthbuf.putInt(bos.size());
				
				OutputStream os;
				file.createNewFile();
				os = new FileOutputStream(file);
				os.write(data);
				os.write(magic);
				os.write(lengthbuf.array());
				bos.writeTo(os);
				os.close();
			} catch (FileNotFoundException e) {
				XLog.e("file not found "+file.toString(), e);
			} catch (IOException e) {
				XLog.e("cannot write jpeg "+file.toString(), e);
			}
		}
		
		BitmapFactory.Options opt = new BitmapFactory.Options();
		opt.inPreferQualityOverSpeed = true;
		BitmapRegionDecoder brd;
		try {
			brd = BitmapRegionDecoder.newInstance(data, 0, data.length, false);
			Rect rect;
			rect = new Rect(0,0,brd.getWidth()/3,brd.getHeight());
			mRightBitmap = brd.decodeRegion(rect, opt);
			rect = new Rect(brd.getWidth() - brd.getWidth()/3,0,brd.getWidth(),brd.getHeight());
			mLeftBitmap = brd.decodeRegion(rect, opt);
		} catch (IOException e) {
			XLog.e("Region decoder doesn't want to cooperate",e);
		}
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
