/**
 * Copyright 2012 Jorgen Birkler
 * jorgen@birkler.se
 *
 * Camera calibration and open cv demonstration applications
 *
 */

package se.birkler.opencvcalibrate.service;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import se.birkler.opencvcalibrate.util.XLog;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * This service uploads all files in the private directory /data/data/appname/upload_queue/*
 * When uploaded it is moved to the /data/data/appname/uploaded/* directory
 * 
 * @author birkler
 */
public class UploaderService extends IntentService {

	public static final String UPLOAD_QUEUE_DIR = "upload_queue";
	public static final String UPLOADED_STORE_DIR = "uploaded";
	public static final String SERVICE_NAME = "Calibration data uploader";
	private File dirQueue;
	private File dirUploaded;
	public UploaderService() {
		super(SERVICE_NAME);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		dirQueue = getDir(UPLOAD_QUEUE_DIR,Context.MODE_PRIVATE);
		dirUploaded = getDir(UPLOADED_STORE_DIR,Context.MODE_PRIVATE);
	}
	
	@Override
	public void onHandleIntent (Intent intent) {
		File next = null;
		do {
			next = getNextFileToUpload();
			if (next != null) {
				String messageStringUp = getString(se.birkler.opencvcalibrate.R.string.msg_toast_uploading,next.getName());
				Toast toastUploading = Toast.makeText(this,messageStringUp,Toast.LENGTH_SHORT);
				toastUploading.show();
				if (uploadFile(next)) {
					String messageString = getString(se.birkler.opencvcalibrate.R.string.msg_toast_calibration_uploaded,next.getName());
					Toast toast = Toast.makeText(this,messageString,Toast.LENGTH_SHORT);
					toast.show();
					unqueueFile(next);
				}
				toastUploading.cancel();
			}
		} while (next != null);
		
	}
	
	private void unqueueFile(File file) {
		File moveTo = new File(dirUploaded,file.getName());
		file.renameTo(moveTo);
		XLog.d(String.format("Unqueueing file %s", file.getName()));
	}

	protected File getNextFileToUpload() {
		String[] list = dirQueue.list();
		
		for (int i=0;i < list.length;i++) {
			File firstFile = new File(dirQueue, list[i]);
			if (firstFile.isFile()) {
				return firstFile;
			}
		}
		return null;
	}

	protected boolean uploadFile(File file) {
		XLog.d(String.format("Starting upload of file %s", file));
		HttpURLConnection connection = null;
		DataOutputStream outputStream = null;
		
		String urlServer = "http://calib.birkler.se/add.php";
		String lineEnd = "\r\n";
		String twoHyphens = "--";
		String boundary =  "*****";
	
		int bytesRead, bytesAvailable, bufferSize;
		byte[] buffer;
		final int maxBufferSize = 1*1024*1024;
	
		try
		{
			FileInputStream fileInputStream = new FileInputStream(file);
		
			URL url = new URL(urlServer);
			connection = (HttpURLConnection) url.openConnection();
			
		
			// Allow Inputs & Outputs
			connection.setDoInput(true);
			connection.setDoOutput(true);
			connection.setUseCaches(false);
		
			// Enable POST method
			connection.setRequestMethod("POST");
		
			connection.setRequestProperty("Connection", "Keep-Alive");
			connection.setRequestProperty("Content-Type", "application/json");
			outputStream = new DataOutputStream( connection.getOutputStream() );
			outputStream.writeBytes(lineEnd);
		
			bytesAvailable = fileInputStream.available();
			bufferSize = Math.min(bytesAvailable, maxBufferSize);
			buffer = new byte[bufferSize];
		
			// Read file
			bytesRead = fileInputStream.read(buffer, 0, bufferSize);
		
			while (bytesRead > 0)
			{
				outputStream.write(buffer, 0, bufferSize);
				bytesAvailable = fileInputStream.available();
				bufferSize = Math.min(bytesAvailable, maxBufferSize);
				bytesRead = fileInputStream.read(buffer, 0, bufferSize);
			}
		
			outputStream.writeBytes(lineEnd);
		
			// Responses from the server (code and message)
			int serverResponseCode = connection.getResponseCode();
			String serverResponseMessage = connection.getResponseMessage();
		
			fileInputStream.close();
			outputStream.flush();
			outputStream.close();
			return true;
		}
		catch (Exception ex)
		{
			XLog.e("Something went wrong at posting calibration data", ex);
		}
		return false;
	}
}
