package com.sizetool.samplecapturer.camera;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import com.sizetool.samplecapturer.opencvutil.MatByteBufferWrapper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.Debug;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class OpenCVViewfinderView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
	
	public interface openCVProcessor {
		void processFrame(Canvas canvas, int width, int height, Mat yuvData, Mat grayData);
	}
	
    private static final String TAG = "Sample::SurfaceView";

    private Camera              mCamera;
    private SurfaceHolder       mHolder;
    private int                 mFrameWidth;
    private int                 mFrameHeight;
    private boolean             mThreadRun;

	protected ByteBuffer mPreviewCallbackBuffer;

	private openCVProcessor mProcessor;

	private MatByteBufferWrapper mYuv;

	private Mat mGraySubmat;
	
	// This constructor is used when the class is built from an XML resource.
	public OpenCVViewfinderView(Context context, AttributeSet attrs) {
		super(context, attrs);
        mHolder = getHolder();
        mHolder.addCallback(this);
        Log.i(TAG, "Instantiated new " + this.getClass());
	}

    public int getFrameWidth() {
        return mFrameWidth;
    }

    public int getFrameHeight() {
        return mFrameHeight;
    }
    
    public void setProcessor(openCVProcessor processor)
    {
    	mProcessor = processor;
    }
    

    public void surfaceChanged(SurfaceHolder _holder, int format, int width, int height) {
        Log.i(TAG, "surfaceCreated");
        if (mCamera != null) {
            Camera.Parameters params = mCamera.getParameters();
            List<Camera.Size> sizes = params.getSupportedPreviewSizes();
            mFrameWidth = width;
            mFrameHeight = height;

            // selecting optimal camera preview size
            {
                double minDiff = Double.MAX_VALUE;
                for (Camera.Size size : sizes) {
                    if (Math.abs(size.height - height) < minDiff) {
                        mFrameWidth = size.width;
                        mFrameHeight = size.height;
                        minDiff = Math.abs(size.height - height);
                    }
                }
            }

            params.setPreviewSize(getFrameWidth(), getFrameHeight());
            mCamera.setParameters(params);
            try {
				mCamera.setPreviewDisplay(null);
			} catch (IOException e) {
				Log.e(TAG, "mCamera.setPreviewDisplay fails: " + e);
			}
            params = mCamera.getParameters();
            Camera.Size size = params.getPreviewSize();
            int bytesneeded = size.height * size.width * ImageFormat.getBitsPerPixel(params.getPreviewFormat()) / 8; 
            mPreviewCallbackBuffer = ByteBuffer.allocateDirect(bytesneeded);
            mCamera.addCallbackBuffer(mPreviewCallbackBuffer.array());
            mCamera.startPreview();
            // initialize Mats before usage
            mYuv = new MatByteBufferWrapper(mPreviewCallbackBuffer,getFrameHeight() + getFrameHeight() / 2, getFrameWidth(), CvType.CV_8UC1);
            //mYuv = new Mat(getFrameHeight() + getFrameHeight() / 2, getFrameWidth(), CvType.CV_8UC1);
            mGraySubmat = mYuv.submat(0, getFrameHeight(), 0, getFrameWidth());
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.i(TAG, "surfaceCreated");
        Debug.startMethodTracing("opencvtrace_viewfinder");
        mCamera = Camera.open();
        mCamera.setPreviewCallbackWithBuffer (new PreviewCallback() {
            private int frameCount;
			private long prevTime;

			public void onPreviewFrame(byte[] data, Camera camera) {
        		frameCount++;
        		long now = System.currentTimeMillis();
        		if (prevTime > 0) {
        			long diff = now - prevTime;
        			if (diff > 2000) {
        				Log.d("OpenCVDemo:",String.format("Frames/s:%.2f data.size=%d",frameCount * 1000.0f / diff,data == null ? 0 : data.length));
        				frameCount = 0;
        				prevTime = now;
        			}
        		}
        		else {
        			prevTime = now;
        		}
        		if (mProcessor != null) {
                    Canvas canvas = mHolder.lockCanvas();
                    if (canvas != null) {
                    	mProcessor.processFrame(canvas, getFrameWidth(),getFrameHeight(), mYuv,mGraySubmat);
                        mHolder.unlockCanvasAndPost(canvas);
                    }
        		}
                byte[] framedata = mPreviewCallbackBuffer.array();
                mCamera.addCallbackBuffer(framedata);
            }
        });
    }


    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i(TAG, "surfaceDestroyed");
        Debug.stopMethodTracing();
        mThreadRun = false;
        if (mCamera != null) {
            synchronized (this) {
                mCamera.stopPreview();
                mCamera.setPreviewCallback(null);
                mCamera.release();
                mCamera = null;
            }
        }
    }

    public void run() {
        mThreadRun = true;
        Log.i(TAG, "Starting processing thread");
        while (mThreadRun) {
            Bitmap bmp = null;

            //Need to add back the callback buffer
            byte[] data = mPreviewCallbackBuffer.array();
            mCamera.addCallbackBuffer(data);
            synchronized (this) {
                try {
                    this.wait();
//                    bmp = processFrame(mFrame);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (bmp != null) {
                Canvas canvas = mHolder.lockCanvas();
                if (canvas != null) {
                    canvas.drawBitmap(bmp, (canvas.getWidth() - getFrameWidth()) / 2, (canvas.getHeight() - getFrameHeight()) / 2, null);
                    mHolder.unlockCanvasAndPost(canvas);
                }
                //bmp.recycle();
            }
        }
    }
}