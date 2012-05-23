package se.birkler.opencvcalibrate.camera;

import java.util.Date;

import android.hardware.SensorManager;


public class CaptureData {

	public String name;
	public String deviceId;
	public java.util.Date captureTime;
	public float[] orientation;
	public float accel[];
	public float magnetic[];
	public String deviceSerialNum;
	public String cameraStats;
	public int timeOfDay; //Hour of the day in local time (
	public String projectId;
	public String sequenceId;
	public String deviceBrand;
	public String deviceBoard;
	public String deviceModelName;
	public String deviceDisplayName;
	public String deviceManufacturer;
	public String deviceVersionRelease;
	public String deviceDevice;
	public String accelSensorName;
	public String magneticSensorName;
	
	protected byte[] mData;

	public CaptureData() {
		accel = new float[3];
		magnetic = new float[3];
		orientation = new float[9];
		deviceBrand = android.os.Build.BRAND;
		deviceBoard = android.os.Build.BOARD;
		deviceDevice = android.os.Build.DEVICE;
		deviceModelName = android.os.Build.MODEL;
		name = (deviceBrand + ":" + deviceModelName).replace(' ','_');
		deviceDisplayName = android.os.Build.DISPLAY;
		deviceManufacturer = android.os.Build.MANUFACTURER;
		deviceVersionRelease = android.os.Build.VERSION.RELEASE;
	}
	
	void setCaptureTime() { 
		captureTime = new Date(System.currentTimeMillis());
		timeOfDay = (int) System.currentTimeMillis();
	}
	
	void setGravitySensorData(float[] values) {
		if (values != null) {
			accel = values.clone();
			updateOrientationMatrix();
		}
	}
	void setMagneticFieldSensorData(float[] values) {
		if (values != null) {
			magnetic = values.clone();
			updateOrientationMatrix();
		}
	}	
	
	private void updateOrientationMatrix() {
		if (magnetic != null && accel != null) {
			float matrixI[] = new float[9];
			SensorManager.getRotationMatrix(orientation, matrixI, accel, magnetic);
		}
	}
	
	void setPictureData(byte[] data) {
		mData = data;
	}
}
