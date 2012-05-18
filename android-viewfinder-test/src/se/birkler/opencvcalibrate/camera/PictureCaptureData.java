package se.birkler.opencvcalibrate.camera;

import java.util.Date;
import android.content.Context;


public abstract class PictureCaptureData {
	int mAction;
	public class MetaData {
		public MetaData() {
			accel = new float[3];
			magnetic = new float[3];
			orientation = new float[9];
		}
		String user;
		String deviceSerialNum;
		String accelSensorName;
		String magneticSensorName;
		String cameraStats;
		float accel[];
		float magnetic[];
		float[] orientation;
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
	};
	MetaData mMetaData = new MetaData();
	protected byte[] mData;

	public PictureCaptureData() {
		mMetaData.deviceBrand = android.os.Build.BRAND;
		mMetaData.deviceBoard = android.os.Build.BOARD;
		mMetaData.deviceName = android.os.Build.DEVICE;
		mMetaData.deviceDisplayName = android.os.Build.DISPLAY;
		mMetaData.deviceManufacturer = android.os.Build.MANUFACTURER;
		mMetaData.deviceVersionRelease = android.os.Build.VERSION.RELEASE;
	}
	
	void setCaptureTime() { 
		mMetaData.captureTime = new Date(System.currentTimeMillis());
		mMetaData.timeOfDay = (int) System.currentTimeMillis();
	}
	
	void setGravitySensorData(float[] values) {
		if (values != null) {
			mMetaData.accel = values.clone();
		}
	}
	void setMagneticFieldSensorData(float[] values) {
		if (values != null) {
			mMetaData.magnetic = values.clone();
		}
	}	
	
	void setOrientationMatrix(float[] values) {
		if (values != null) {
			mMetaData.orientation = values.clone();
		}
	}	
	
	
	void setPictureData(byte[] data) {
		mData = data;
	}
	
	abstract boolean execute(Context context);
}
