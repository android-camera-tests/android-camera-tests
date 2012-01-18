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
 * Copyright (C) 2010 ZXing authors
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

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;
import java.util.regex.Pattern;

import android.content.Context;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import com.sizetool.samplecapturer.util.XLog;
import android.view.Display;
import android.view.WindowManager;

final class CameraConfigurationManager {
	private static final int FOCUS_DISTANCE_OPTIMAL_INDEX = 1;

	private static final Pattern COMMA_PATTERN = Pattern.compile(",");
	private static final Pattern SEMICOLON_PATTERN = Pattern.compile(";");

	private final Context context;
	private Point screenResolution;
	private Point previewResolution;
	private int previewFormat;
	private String previewFormatString;

	private Point cameraResolution;

	private boolean mGetFocusDistanceNotSupported = false;

	private Method mMethodGetFocusDistances;
	
	CameraConfigurationManager(Context context) {
		this.context = context;
	}

	/**
	 * Reads, one time, values from the camera that are needed by the app.
	 */
	void initFromCameraParameters(Camera camera) {
		Camera.Parameters parameters = camera.getParameters();
		previewFormat = parameters.getPreviewFormat();
		previewFormatString = parameters.get("preview-format");
		XLog.d( "Default preview format: " + previewFormat + '/' + previewFormatString);
		WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		Display display = manager.getDefaultDisplay();
		screenResolution = new Point(display.getWidth(), display.getHeight());
		XLog.d( "Screen resolution: " + screenResolution);
		previewResolution = getPreviewResolution(parameters, screenResolution);
		XLog.d( "Preview resolution: " + previewResolution);
		cameraResolution = getCameraResolution(parameters, previewResolution,1);
		XLog.d( "Camera resolution: " + cameraResolution);
	}

	/**
	 * Sets the camera up to take preview images which are used for both preview
	 * and decoding. We detect the preview format here so that
	 * buildLuminanceSource() can build an appropriate LuminanceSource subclass.
	 * In the future we may want to force YUV420SP as it's the smallest, and the
	 * planar Y can be used for barcode scanning without a copy in some cases.
	 */
	void setDesiredCameraParameters(Camera camera) {
		Camera.Parameters parameters = camera.getParameters();
		parameters.setPreviewSize(previewResolution.x, previewResolution.y);
		parameters.setPictureSize(cameraResolution.x, cameraResolution.y);
		setFlash(parameters);
		// setSharpness(parameters);
		camera.setParameters(parameters);
		parameters = camera.getParameters();
		setFocusModeInfiniti(camera);
		XLog.d( "Set desired camera params result: " + pruneCameraParametersForXLog(parameters.flatten()));
	}
	
	static protected String pruneCameraParametersForXLog(String s) {
		StringBuffer result = new StringBuffer();
		for (String param : SEMICOLON_PATTERN.split(s)) {
			if (COMMA_PATTERN.split(param).length > 0) {
				//A list of values, ignore
			}
			else {
				result.append(param);
				result.append(";");			
			}
		}
		return result.toString();
	}
	
	void setFocusModeInfiniti(Camera camera) {
		try {
			Camera.Parameters parameters = camera.getParameters();
			parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
			camera.setParameters(parameters);
			parameters = camera.getParameters();
			parameters.set("focus-mode", "infiniti");
			camera.setParameters(parameters);
		}
		catch (RuntimeException e) {
		}
	}
	
	Point getPreviewResolution() {
		return previewResolution;
	}

	Point getCameraPictureResolution() {
		return previewResolution;
	}

	Point getScreenResolution() {
		return screenResolution;
	}

	int getPreviewFormat() {
		return previewFormat;
	}

	
	public float getFocusDistance(Camera camera) {
		float distance = (float) -1.0;
		if (!mGetFocusDistanceNotSupported && camera != null) {
			Camera.Parameters camera_params = camera.getParameters();
			try {
				if (mMethodGetFocusDistances == null) {
					mMethodGetFocusDistances = Parameters.class.getMethod("getFocusDistances", new Class[] {float[].class});
				}
				float distances[] = new float[3];
				mMethodGetFocusDistances.invoke(camera_params, distances);
				distance = distances[FOCUS_DISTANCE_OPTIMAL_INDEX];
			} catch (Exception e) {
				XLog.d( "getFocusDistances not supported");
				mGetFocusDistanceNotSupported  = true;
			}
		}
		return distance;
	}
	
	String getPreviewFormatString() {
		return previewFormatString;
	}

	/**
	 * Sets the preview size to match the screen resultion as much as possible (if VGA or bigger) other we  
	 * @param parameters
	 * @param screenResolution
	 * @return
	 */
	private static Point getPreviewResolution(Camera.Parameters parameters, Point screenResolution) {
		Point targetResultion = new Point(screenResolution);
		
		//We want preview resolution to be at least VGA
		targetResultion.x = 640;
		targetResultion.y = 480;

		String previewSizeValueString = parameters.get("preview-size-values");
		// saw this on Xperia
		if (previewSizeValueString == null) {
			previewSizeValueString = parameters.get("preview-size-value");
		}

		Point previewResolution = null;

		if (previewSizeValueString != null) {
			XLog.d( "preview-size-values parameter: " + previewSizeValueString);
			previewResolution = findBestSizeValueKeepingAspectRatio(previewSizeValueString, targetResultion);
		}

		if (previewResolution == null) {
			// Ensure that the camera resolution is a multiple of 8, as the
			// screen may not be.
			previewResolution = new Point((screenResolution.x >> 3) << 3, (screenResolution.y >> 3) << 3);
		}

		return previewResolution;
	}

	private static Point getCameraResolution(Camera.Parameters parameters, Point viewfinderResolution, int factor) {

		String pictureSizeValueString = parameters.get("picture-size-values");
		// saw this on Xperia
		if (pictureSizeValueString == null) {
			pictureSizeValueString = parameters.get("picture-size-value");
		}

		Point cameraResolution = null;

		if (pictureSizeValueString != null) {
			XLog.d( "picture-size-values parameter: " + pictureSizeValueString);
			cameraResolution = findBestSizeValueKeepingAspectRatio(pictureSizeValueString, viewfinderResolution);
		}

		if (cameraResolution == null) {
			// Ensure that the camera resolution is a multiple of 8, as the
			// screen may not be.
			cameraResolution = new Point((viewfinderResolution.x >> 3) << 3, (viewfinderResolution.y >> 3) << 3);
		}

		return cameraResolution;
	}
	

	private static Point findBestSizeValueKeepingAspectRatio(CharSequence sizeValueString, Point refsize) {
		int refWidth = refsize.x;
		int refHeight = refsize.y;

		// Swap here if we are in portrait since preview sizes are only reported
		// for landscape and we will ask driver to rotate later
		if (refHeight > refWidth) {
			int temp = refWidth;
			refWidth = refHeight;
			refHeight = temp;
		}

		//Collect acceptable view finder sizes
		List<Point> viewFinderSizes= new Vector<Point>(10);
		
		for (String size : COMMA_PATTERN.split(sizeValueString)) {
			size = size.trim();
			int dimPosition = size.indexOf('x');
			if (dimPosition < 0) {
				XLog.w( "Bad preview-size: " + size);
				continue;
			}
			int newX;
			int newY;
			try {
				newX = Integer.parseInt(size.substring(0, dimPosition));
				newY = Integer.parseInt(size.substring(dimPosition + 1));
			} catch (NumberFormatException nfe) {
				XLog.w( "Bad preview-size: " + size);
				continue;
			} catch (Exception e) {
				continue;
			}
			viewFinderSizes.add(new Point(newX,newY));
		}
		
		//Sort them with smallest first
		Collections.sort(viewFinderSizes, new ResolutionPointComparator());
		
		//Find best match
		int bestX = 0;
		int bestY = 0;
		double bestDiff = 10000*10000;
		
		for (Point p : viewFinderSizes) {
			int newX = p.x;
			int newY = p.y;
			double newDiff = Math.pow(newX - refWidth, 2) + Math.pow(newY - refHeight, 2);
			if (newDiff * 0.98 < bestDiff) {
				// Grab the closest match
				if (newX*newY >= bestX * bestY) {
					bestDiff = newDiff;
					bestX = newX;
					bestY = newY;
				}
			}
		}

		if (bestX > 0 && bestY > 0) {
			return new Point(bestX, bestY);
		}
		return null;
	}

	private void setFlash(Camera.Parameters parameters) {
		// This is the standard setting to turn the flash off that all devices
		// should honor.
		parameters.set("flash-mode", "off");
	}
}

class ResolutionPointComparator implements Comparator<Point>, Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public ResolutionPointComparator() {
		
	}
	public int compare(Point p1, Point p2) {
		int area1 = p1.x * p1.y;
		int area2 = p2.x * p2.y;
		if (area1 == area2) {
			return 0;
		}
		else if (area1 < area2) {
			return -1;
		}
		else {
			return +1;
		}
	}
}
