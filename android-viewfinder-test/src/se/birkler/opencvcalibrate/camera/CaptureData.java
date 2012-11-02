package se.birkler.opencvcalibrate.camera;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Date;

import org.xmlpull.v1.XmlSerializer;

import android.hardware.SensorManager;
import android.util.Xml;


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
	
	private void writeMatrixArray(XmlSerializer serializer,String name, int rows,int cols, float[] data) 
			throws IllegalArgumentException, IllegalStateException, IOException
	{
        serializer.startTag("", name);
        serializer.attribute("", "type_id", "opencv-matrix");
        serializer.startTag("", "rows");
        serializer.text(Integer.toString(rows));
        serializer.endTag("", "rows");
        serializer.startTag("", "cols");
        serializer.text(Integer.toString(rows));
        serializer.endTag("", "cols");
        serializer.startTag("", "dt");
        serializer.text("d");
        serializer.endTag("", "dt");
        serializer.startTag("", "data");
        for (int i=0;i < data.length;i++) {
            serializer.text(Double.toString(data[i]));
        }
        serializer.endTag("", "data");
        serializer.endTag("", name);
	}
	
	private void writeString(XmlSerializer serializer,String name, String data) throws IllegalArgumentException, IllegalStateException, IOException
	{
        serializer.startTag("", name);
        serializer.text("\"");
        serializer.text(data);
        serializer.text("\"");
        serializer.endTag("", name);
	}
	
	protected String writeXml() {
	    XmlSerializer serializer = Xml.newSerializer();
	    StringWriter writer = new StringWriter();
	    try {
	        serializer.setOutput(writer);
	        serializer.startDocument("UTF-8", true);
	        serializer.startTag("", "opencv_storage");
	        serializer.startTag("", "capture_data");
	        {
	        	writeString(serializer,"name",name);
	        	writeString(serializer,"deviceId",deviceId);
	        	writeString(serializer,"deviceBoard",deviceBoard);
	        	writeString(serializer,"deviceBrand",deviceBrand);
	        	writeString(serializer,"deviceDisplayName",deviceDisplayName);
	        	writeString(serializer,"deviceManufacturer",deviceManufacturer);
	        	writeString(serializer,"deviceModelName",this.deviceModelName);
	        	writeString(serializer,"magneticSensorName",this.magneticSensorName);
	        	writeString(serializer,"projectId",this.projectId);
	        	writeString(serializer,"cameraStats",this.cameraStats);
	        	writeString(serializer,"captureTime",this.captureTime.toString());
	        	writeString(serializer,"sequenceId",this.sequenceId);
	        	writeMatrixArray(serializer,"gravity",3,1,this.accel);
	        	writeMatrixArray(serializer,"magnetic",3,1,this.magnetic);
	        	writeMatrixArray(serializer,"orientation",3,3,this.orientation);
	        }
	        serializer.endTag("", "capture_data");
	        serializer.endTag("", "opencv_storage");
	        serializer.endDocument();
	        return writer.toString();
	    } catch (Exception e) {
	        throw new RuntimeException(e);
	    } 
	}
	
}
