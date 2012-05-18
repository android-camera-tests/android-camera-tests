package se.birkler.opencvcalibrate.camera;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.zip.GZIPOutputStream;
import org.codehaus.jackson.map.ObjectMapper;

import se.birkler.opencvcalibrate.util.XLog;
import android.content.Context;
import android.hardware.SensorManager;


public class PictureCaptureData {
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
	private byte[] mData;

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
	
	void setAccelerationSensorData(float[] values) {
		if (values != null) {
			mMetaData.accel = values.clone();
		}
	}
	void setOrientationSensorData(float[] values) {
		if (values != null) {
			mMetaData.magnetic = values.clone();
		}
	}		
	
	void setPictureData(byte[] data) {
		mData = data;
	}
	
	void writeFile(Context context) {
		float[] matrixI = new float[9];
		
		SensorManager.getRotationMatrix(mMetaData.orientation, matrixI, mMetaData.accel, mMetaData.magnetic);
		
		CharSequence timeString = String.format("%020d",System.currentTimeMillis());
		String name = String.format("st%s.jpg",timeString);
		File file = new File(context.getExternalFilesDir("pictures"), name);
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
			os.write(mData);
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
}
