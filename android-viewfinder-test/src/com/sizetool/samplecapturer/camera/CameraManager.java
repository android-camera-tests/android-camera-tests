/*
 * Copyright 2012 jorgen birkler 
 * 
 * jorgen@birkler.se
 * 
 *  _    _     _   _                  
 * | |__(_)_ _| |_| |___ _ _  ___ ___ 
 * | '_ \ | '_| / / / -_) '_|(_-</ -_)
 * |_.__/_|_| |_\_\_\___|_|(_)__/\___|
 *
 */

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

package com.sizetool.samplecapturer.camera;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import android.app.Activity;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.Handler;
import com.sizetool.samplecapturer.util.XLog;
import android.view.Surface;
import android.view.SurfaceHolder;


/**
 * This object wraps the Camera service object and expects to be the only one
 * talking to it. The implementation encapsulates the steps needed to take
 * preview-sized images, which are used for both preview and decoding.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class CameraManager {

	private static CameraManager cameraManager;

	private final Context context;
	private CameraConfigurationManager configManager;
	private Camera camera;
	private Rect mFramingRect;
	private boolean initialized;
	private boolean previewing;
	private final boolean useOneShotPreviewCallback = true;

	/**
	 * Preview frames are delivered here, which we pass on to the registered
	 * handler. Make sure to clear the handler so it will only receive one
	 * message.
	 */
	private PreviewCallback previewCallback;

	private Point mFramingRectViewFinderSurfaceResolution;

	private SurfaceHolder mSurfaceHolder;

	private int mCameraDisplayOrientation = 0;

	private Matrix mScaling;

	private Matrix mResultPointConversionMatrix;

	private Rect mFramingRectInSurface;

	private int mPreviewFormat = -1;

	private String mPreviewFormatString = null;
	
	byte previewBuffer[] = null;

	public Point getCameraPreviewSize() {
		return configManager.getPreviewResolution();
	}

	/**
	 * Gets the CameraManager singleton instance.
	 * 
	 * @return A reference to the CameraManager singleton.
	 */
	public static CameraManager get(Context context) {
		if (cameraManager == null) {
			cameraManager = new CameraManager(context);
		}
		if (cameraManager.configManager == null) {
			cameraManager.configManager = new CameraConfigurationManager(cameraManager.context);
		}
		return cameraManager;
	}

	private CameraManager(Context context) {
		this.context = context;
	}

	/**
	 * Opens the camera driver and initializes the hardware parameters.
	 * 
	 * @param holder
	 *            The surface object which the camera will draw preview frames
	 *            into.
	 * @throws IOException
	 *             Indicates the camera driver failed to open.
	 */
	public void openDriver(Activity activity, SurfaceHolder holder) throws IOException {
		if (camera == null) {
			camera = Camera.open();
			if (camera == null) {
				throw new IOException();
			}
		}
		{
			camera.setPreviewDisplay(holder);
			mSurfaceHolder = holder;
			setCameraDisplayOrientation(activity);
			if (!initialized) {
				initialized = true;
				configManager.initFromCameraParameters(camera);
			}
			configManager.setDesiredCameraParameters(camera);
			Point resolution = configManager.getCameraPictureResolution();
			previewBuffer = new byte[(resolution.x * resolution.y) * 2];
			camera.addCallbackBuffer(previewBuffer);
		}
	}

	/**
	 * Closes the camera driver if still in use.
	 */
	public void closeDriver() {
		if (camera != null) {
			//FlashlightManager.disableFlashlight();
			camera.release();
			camera = null;
		}
	}

	/**
	 * Asks the camera hardware to begin drawing preview frames to the screen.
	 */
	public void startPreview() {
		if (camera != null && !previewing) {
			camera.startPreview();
			previewing = true;
		}
	}

	/**
	 * Tells the camera to stop drawing preview frames.
	 */
	public void stopPreview() {
		if (camera != null && previewing) {
			if (!useOneShotPreviewCallback) {
				camera.setPreviewCallback(null);
			}
			camera.stopPreview();
			previewCallback.setHandler(null, 0);
			previewing = false;
		}
	}

	/**
	 * A single preview frame will be returned to the handler supplied. The data
	 * will arrive as byte[] in the message.obj field, with width and height
	 * encoded as message.arg1 and message.arg2, respectively.
	 * 
	 * @param handler
	 *            The handler to send the message to.
	 * @param message
	 *            The what field of the message to be sent.
	 */
	public void requestPreviewFrame(Handler handler, int message) {
		if (camera != null && previewing) {
			if (previewCallback == null) {
				previewCallback = new PreviewCallback(context,this);
			}

			previewCallback.setHandler(handler, message);
			if (useOneShotPreviewCallback) {
				camera.startPreview();
				camera.setOneShotPreviewCallback(previewCallback);
			} else {
				camera.setPreviewCallback(previewCallback);
			}
		}
	}
	

	private void setCameraDisplayOrientation(Activity activity) {
		if (isForceLandscape()) {
			// nothing to do here, skip throwing an exception.
			return;
		}
		try {
			Class<?> cameraInfoClass = Class.forName("android.hardware.Camera$CameraInfo");
			Method getCameraInfoMethod = android.hardware.Camera.class.getMethod("getCameraInfo", new Class[] { int.class, cameraInfoClass });
			Method setDisplayOrientationMethod = android.hardware.Camera.class.getMethod("setDisplayOrientation", new Class[] { int.class });
			Method getRotationMethod = android.view.Display.class.getMethod("getRotation", new Class[] {});

			Object info = cameraInfoClass.newInstance();
			getCameraInfoMethod.invoke(camera, 0, info);

			// Force update of framing rects
			mFramingRect = null;

			int rotation = (Integer) getRotationMethod.invoke(activity.getWindowManager().getDefaultDisplay());

			int degrees = 0;
			switch (rotation) {
			case Surface.ROTATION_0:
				degrees = 0;
				break;
			case Surface.ROTATION_90:
				degrees = 90;
				break;
			case Surface.ROTATION_180:
				degrees = 180;
				break;
			case Surface.ROTATION_270:
				degrees = 270;
				break;
			}

			int result;
			Field facingField = cameraInfoClass.getField("facing");
			Field orientationField = cameraInfoClass.getField("orientation");

			if (facingField.getInt(info) == 1 /*
											 * Camera.CameraInfo.CAMERA_FACING_FRONT
											 */) {
				result = (orientationField.getInt(info) + degrees) % 360;
				result = (360 - result) % 360; // compensate the mirror
			} else { // back-facing
				result = (orientationField.getInt(info) - degrees + 360) % 360;
			}
			boolean isPreviewing = previewing;
			if (isPreviewing) {
				camera.stopPreview();
			}
			mCameraDisplayOrientation = result;
			setDisplayOrientationMethod.invoke(camera, result);
			if (isPreviewing) {
				camera.startPreview();
			}
		} catch (RuntimeException e) {
			// Older device, we need to force landscape...
			mCameraDisplayOrientation = Surface.ROTATION_0;
		} catch (Exception e) {
			// Older device, we need to force landscape...
			mCameraDisplayOrientation = Surface.ROTATION_0;
		}
	}

	private Rect getRectFromRefence(Rect src, Rect limit, double w_scale, double h_scale) {
		int width = (int) (src.width() * w_scale);
		if (width < limit.left) {
			width = limit.left;
		} else if (width > limit.right) {
			width = limit.right;
		}
		if (width > src.width()) {
			width = src.width();
		}
		int height = (int) (src.height() * h_scale);
		if (height < limit.top) {
			height = limit.top;
		} else if (height > limit.bottom) {
			height = limit.bottom;
		}
		if (height > src.height()) {
			height = src.height();
		}

		return new Rect(src.centerX() - width / 2, src.centerY() - height / 2, src.centerX() + width / 2, src.centerY() + height / 2);
	}


	private void updateFramingRects() {
		Point viewfinderSurfaceSize = null;
		Point viewfinderDataSize = null;
		if (mSurfaceHolder != null) {
			viewfinderSurfaceSize = new Point(0, 0);
			viewfinderSurfaceSize.x = mSurfaceHolder.getSurfaceFrame().width();
			viewfinderSurfaceSize.y = mSurfaceHolder.getSurfaceFrame().height();
			viewfinderDataSize = configManager.getPreviewResolution();
		}
		if (camera == null || viewfinderSurfaceSize == null || viewfinderDataSize == null) {
			mFramingRect = null;
			mFramingRectInSurface = null;
			mResultPointConversionMatrix = null;
			return;
		}
		if (mFramingRectViewFinderSurfaceResolution == null || mFramingRect == null || mFramingRectInSurface == null
				|| mFramingRectViewFinderSurfaceResolution.x != viewfinderSurfaceSize.x
				|| mFramingRectViewFinderSurfaceResolution.y != viewfinderSurfaceSize.y) {
			Rect viewFinderRect = new Rect(0, 0, viewfinderSurfaceSize.x, viewfinderSurfaceSize.y);
			Rect limit = viewFinderRect;
			mFramingRectViewFinderSurfaceResolution = viewfinderSurfaceSize;
			mFramingRect = getRectFromRefence(viewFinderRect, limit, 0.75, 0.60);
			mScaling = new Matrix();
			mResultPointConversionMatrix = new Matrix();
			float from[] = new float[6];
			float to[] = new float[6];
			if (mCameraDisplayOrientation == 0) {
				//Upper left is upper left
				from[0] = 0; from[1] = 0; to[0] = 0; to[1] = 0;
				//Lowerright is lower right
				from[2] = viewfinderSurfaceSize.x; from[3] = viewfinderSurfaceSize.y; to[2] = viewfinderDataSize.x; to[3] = viewfinderDataSize.y;
				mScaling.setPolyToPoly(from, 0, to, 0, 2);
			} else {
				//Upper left is lower left
				from[0] = 0; from[1] = 0; to[0] = 0; to[1] = viewfinderDataSize.y;
				//Lower left is lower right
				from[2] = 0; from[3] = viewfinderSurfaceSize.y; to[2] = viewfinderDataSize.x; to[3] = viewfinderDataSize.y;
				//Upper right is upper left
				from[4] = viewfinderSurfaceSize.x; from[5] = 0; to[4] = 0; to[5] = 0;
				mScaling.setPolyToPoly(from, 0, to, 0, 3);
			}
			float pt[] = new float[2];
			pt[0] = 5; pt[1] = 5;
			mScaling.mapPoints(pt);
			
			RectF temp = new RectF();
			mScaling.mapRect(temp, new RectF(mFramingRect));
			mFramingRectInSurface = new Rect();
			temp.round(mFramingRectInSurface);
			
			mScaling.invert(mResultPointConversionMatrix);
			mResultPointConversionMatrix.preTranslate(temp.left, temp.top);

			pt[0] = 5; pt[1] = 5;
			mResultPointConversionMatrix.mapPoints(pt);
			
			XLog.d("framingRect:" + mFramingRect.toShortString() + " framingRectInSurface:" + mFramingRectInSurface.toShortString());
		}
	}

	/**
	 * Calculates the framing rect which the UI should draw to show the user
	 * where to place the bar code. This target helps with alignment as well as
	 * forces the user to hold the device far enough away to ensure the image
	 * will be in focus.
	 * 
	 * @return The rectangle to draw on screen in window coordinates.
	 */
	public Rect getFramingRect() {
		updateFramingRects();
		if (mFramingRect != null) {
			return mFramingRect;
		} else {
			return new Rect(0, 0, 0, 0);
		}
	}

	/**
	 * Like {@link #getFramingRect} but coordinates are in terms of the preview
	 * frame, not UI / screen.
	 */
	public Rect getFramingRectInSurface() {
		updateFramingRects();
		if (mFramingRectInSurface != null) {
			return mFramingRectInSurface;
		} else {
			// Why do we even end up here sometimes...? Should be investigated
			// and fixed.
			return new Rect(0, 0, 0, 0);
		}
	}

	public Matrix getResultPointConversionMatrix() {
		return new Matrix(mResultPointConversionMatrix);
	}

	/**
	 * A factory method to build the appropriate LuminanceSource object based on
	 * the format of the preview buffers, as described by Camera.Parameters.
	 * 
	 * @param data
	 *            A preview frame.
	 * @param width
	 *            The width of the image.
	 * @param height
	 *            The height of the image.
	 * @return A PlanarYUVLuminanceSource instance.
	 */
	public RenderableLuminanceSource buildLuminanceSource(byte[] data, int width, int height) {
		Rect rect = getFramingRectInSurface();
		if (mPreviewFormat < 0) {
			mPreviewFormat = configManager.getPreviewFormat();
		}
		if (mPreviewFormatString == null) {
			mPreviewFormatString = configManager.getPreviewFormatString();
		}

		boolean reverseHorizontal = false;

		switch (mPreviewFormat) {
		// This is the standard Android format which all devices are REQUIRED to
		// support.
		// In theory, it's the only one we should ever care about.
		case PixelFormat.YCbCr_420_SP:
			// This format has never been seen in the wild, but is compatible as
			// we only care
			// about the Y channel, so allow it.
		case PixelFormat.YCbCr_422_SP:
			return new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top, rect.width(), rect.height(), reverseHorizontal);
		default:
			// The Samsung Moment incorrectly uses this variant instead of the
			// 'sp' version.
			// Fortunately, it too has all the Y data up front, so we can read
			// it.
			if ("yuv420p".equals(mPreviewFormatString)) {
				return new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top, rect.width(), rect.height(), reverseHorizontal);
			}
		}
		throw new IllegalArgumentException("Unsupported picture format: " + mPreviewFormat + '/' + mPreviewFormatString);
	}

	public Parameters getParameters() {
		if (camera != null) {
			return camera.getParameters();
		}
		return null;
	}

	public void setParameters(Parameters parameters) {
		if (camera != null) {
			camera.setParameters(parameters);
		}
	}

	public int getRotation() {
		return mCameraDisplayOrientation;
	}

	static public boolean isForceLandscape() {
		return !isSetOrientationEnabled();
	}

	private static boolean isSetOrientationEnabled() {
		try {
			Class.forName("android.hardware.Camera$CameraInfo");
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public float getFocusDistance() {
		return configManager.getFocusDistance(camera);
	}
}