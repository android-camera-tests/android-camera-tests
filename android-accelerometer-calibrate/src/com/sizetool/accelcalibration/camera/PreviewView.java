package com.sizetool.accelcalibration.camera;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import com.sizetool.accelcalibration.util.XLog;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.PorterDuff.Mode;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.ShutterCallback;
import android.hardware.Camera.Size;
import android.os.Debug;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import javax.vecmath.*;

/**
 * A simple wrapper around a Camera and a SurfaceView that renders a centered preview of the Camera
 * to the surface. We need to center the SurfaceView because not all devices have cameras that
 * support preview sizes at the same aspect ratio as the device's display.
 */
public class PreviewView extends PreviewViewRoot implements SurfaceHolder.Callback, Runnable {
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
	
	private ArrayList<CalibrationDataObservation> calibrationData = new ArrayList<PreviewView.CalibrationDataObservation>();
	
	int mCurrentCalibrationPoint = 0;

	private float mAverageFOV;
	
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

	interface OverlayDraw {
		public void draw(Canvas c);
	}
    Size mPreviewSize;
    Camera mCamera;
	float mFieldOfView = -1;
	
	class PreviewBuffer {
		public ByteBuffer mPreviewCallbackBuffer;
		public PreviewBuffer(int width, int height, int format) {
			int bytesneeded = height * width * ImageFormat.getBitsPerPixel(format) / 8; 
			mPreviewCallbackBuffer = ByteBuffer.allocateDirect(bytesneeded);
		}

	}
	PreviewBuffer mPreviewBuffer1;

    private ArrayBlockingQueue<PreviewBuffer> mPreviewFrames = new ArrayBlockingQueue<PreviewBuffer>(3);
	private boolean mThreadRun;
	private int frameCount;
	private long prevTime;
	private Thread mThread;
	private float mGravX;
	private float mGravY;
	private float mGravZ;
	int mResultSurfaceWidth;
	int mResultSurfaceHeight;
	
    public PreviewView(Context context) {
    	super(context);
    	init(context);
    }
	
    public PreviewView(Context context, AttributeSet attr) {
        super(context,attr);
        init(context);
    }
    
    private void init(Context context) {
        mSurfaceView = new SurfaceView(context);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
        lp.addRule(RelativeLayout.CENTER_IN_PARENT);
        mSurfaceView.setLayoutParams(lp);
        addView(mSurfaceView);
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mResultSurfaceView = new SurfaceView(context);
        lp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
        lp.addRule(RelativeLayout.CENTER_IN_PARENT);
        mResultSurfaceView.setLayoutParams(lp);
        addView(mResultSurfaceView);
        mResultSurfaceView.setZOrderOnTop(true);
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mResultHolder = mResultSurfaceView.getHolder();
        mResultHolder.setFormat(PixelFormat.TRANSPARENT);
        mResultHolder.addCallback(this);
        setWillNotDraw(false);
    }

    @Override
    public void  onFinishInflate () {
    	super.onFinishInflate();
    }
    
    public void setCamera(Camera camera) {
        mCamera = camera;
        if (mCamera != null) {
            mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
            requestLayout();
        }
    }

    public void switchCamera(Camera camera) {
       setCamera(camera);
       try {
           camera.setPreviewDisplay(mHolder);
       } catch (IOException exception) {
           XLog.e("IOException caused by setPreviewDisplay()", exception);
       }
       Camera.Parameters parameters = camera.getParameters();
       parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
       requestLayout();
       camera.setParameters(parameters);
    }


    
    void initCamera() {
    	if (mCamera == null) {
    	    mCamera = Camera.open();
    	    setCamera(mCamera);
		    mCamera.setPreviewCallbackWithBuffer (new PreviewCallback() {
				public void onPreviewFrame(byte[] data, Camera camera) {
		            try {
						PreviewView.this.mPreviewFrames.put(mPreviewBuffer1);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
		        }
		    });
		    mThread = new Thread(this);
		    mThread.setPriority(Thread.MIN_PRIORITY);
		    mThread.start();
    	}
    }


    
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
	    // The Surface has been created, acquire the camera and tell it where
	    // to draw.
		if (holder == mHolder) {
	        Debug.startMethodTracing("opencvtrace_viewfinder");
		} 
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
	    // Surface will be destroyed when we return, so stop the preview.
		if (holder == mHolder) {
			Debug.stopMethodTracing();
	        if (mCamera != null) {
	            mCamera.stopPreview();
	        }
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int w,
			int h) {
			    // Now that the size is known, set up the camera parameters and begin
			    // the preview.
				synchronized (mHolder) {
			    	if (holder == mHolder) {  
				    	initCamera();
				        try {
				            if (mCamera != null) {
				                mCamera.setPreviewDisplay(holder);
				            }
				        } catch (IOException exception) {
				            XLog.e("IOException caused by setPreviewDisplay()", exception);
				        }
				        Camera.Parameters parameters = mCamera.getParameters();
				        if (mPreviewSize != null) {
				        	parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
				        	parameters.setPictureSize(mPreviewSize.width, mPreviewSize.height);
				        	parameters.setJpegQuality(97);
					        mFieldOfView = parameters.getHorizontalViewAngle();
					        mFieldOfView = 65.0f;
					        mFieldOfView /= mPreviewSize.width; 
				        	
				            mCamera.setParameters(parameters);
							mPreviewBuffer1 = new PreviewBuffer(mPreviewSize.width,mPreviewSize.height, parameters.getPreviewFormat());
				            mCamera.addCallbackBuffer(mPreviewBuffer1.mPreviewCallbackBuffer.array());
				            // initialize Mats before usage
				        }
				        mCamera.startPreview();
				        requestLayout();
			    	} else if (holder == mResultHolder) {
			    		mResultSurfaceWidth = w;
			    		mResultSurfaceHeight = h;
			    	}
			    }
			}


    public float getFOVPerPixel() {
    	return mFieldOfView;
    }
    
    
	public void takePicture(ShutterCallback shuttercallback, PictureCallback piccallback) {
		if (mCamera != null) {
			mCamera.takePicture(shuttercallback, null, piccallback);
			mCamera.startPreview(); 
		}
	}
	
    public void run() {
        mThreadRun = true;
        XLog.i("Starting processing thread");
        while (mThreadRun) {
        	PreviewBuffer frame;
			try {
				frame = mPreviewFrames.take();
        	
	    		frameCount++;
	    		long now = System.currentTimeMillis();
	    		if (prevTime > 0) {
	    			long diff = now - prevTime;
	    			if (diff > 2000) {
	    				Log.d("OpenCVDemo:",String.format("Frames/s:%.2f data.size=%d",frameCount * 1000.0f / diff,frame == null ? 0 : frame.mPreviewCallbackBuffer.capacity()));
	    				frameCount = 0;
	    				prevTime = now;
	    			}
	    		}
	    		else {
	    			prevTime = now;
	    		}
	
                Canvas canvas = mResultHolder.lockCanvas();
                if (canvas != null) {
                	//mProcessor.processFrame(canvas, mPreviewSize.width,mPreviewSize.height, yuvMat,grayMat);
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
                	mResultHolder.unlockCanvasAndPost(canvas);
                } 
	            //Need to add back the callback buffer
	            byte[] framedata = frame.mPreviewCallbackBuffer.array();
	            mCamera.addCallbackBuffer(framedata);
			} catch (InterruptedException e) {
				e.printStackTrace();
				mThreadRun = false;
			}
        }
    }



	public void setXYZ(float f, float g, float h) {
		mGravX = g;
		mGravY = f;
		mGravZ = h;
	}
}
