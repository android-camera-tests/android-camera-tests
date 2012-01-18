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

import com.sizetool.samplecapturer.R;
import com.sizetool.samplecapturer.util.XLog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The barcode reader activity itself. This is loosely based on the
 * CameraPreview example included in the Android SDK.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */
public final class SampleCatcherActivity extends Activity implements SurfaceHolder.Callback {

	private InitCameraHandler initHandler;
	private ViewfinderView viewfinderView;
	private View statusRootView;
	private TextView statusView;
	private View resultView;
	private boolean hasSurface;
	private SurfaceView surfaceView;
	private CroppedFrameView aspectRatioView;



	@Override
	public void onCreate(Bundle icicle) {
		CameraManager.get(getApplication());
		super.onCreate(icicle);
		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.sampler);
		viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
		statusRootView = (View) findViewById(R.id.status_rootview);
		surfaceView = (SurfaceView) findViewById(R.id.preview_view);
		aspectRatioView = (CroppedFrameView) findViewById(R.id.aspectratio_view);
		hasSurface = false;
		initHandler = new InitCameraHandler();
	}
	@Override
	protected void onResume() {
		super.onResume();
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (hasSurface) {
			// The activity was paused but not stopped, so the surface still
			// exists. Therefore
			// surfaceCreated() won't be called, so i the camera here.
			postInitCamera();

		} else {
			// Install the callback and wait for surfaceCreated() to init the
			// camera.
			surfaceHolder.addCallback(this);
			surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}
	}

	private void postInitCamera() {
		Message msg = Message.obtain();
		msg.what = R.id.init_camera;
		initHandler.removeMessages(msg.what);
		initHandler.sendMessageDelayed(msg, 500);
	}

	@Override
	protected void onPause() {
		super.onPause();
		initHandler.removeMessages(R.id.init_camera);
		CameraManager.get(this).closeDriver();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	/*
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
		} else if (keyCode == KeyEvent.KEYCODE_FOCUS || keyCode == KeyEvent.KEYCODE_CAMERA) {
			// Handle these events so they don't launch the Camera app
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP){
			return true;
			
		} else if(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN){
			return true;
			
		}
		return super.onKeyDown(keyCode, event);
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		//Silences the keyup beep when using the volume up/down keys to zoom.
		if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN){
			return true;
		}
		
		return super.onKeyDown(keyCode, event);		
	}
	*/

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		return true;
	}

	// Don't display the share menu item if the result overlay is showing.
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		boolean mockScannerInstalled = false;
		try {
			getPackageManager().getApplicationInfo("com.vscreens.client.android.mockscanner", 0);
			mockScannerInstalled = true;
		} catch (NameNotFoundException e) {
		}
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
//		switch (item.getItemId()) {
//		case R.id.capture_menu_item_mock_scan:
//			return true;
//		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
	}

	public void surfaceCreated(SurfaceHolder holder) {
		if (!hasSurface) {
			hasSurface = true;
			postInitCamera();
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;
		CameraManager.get(this).stopPreview();
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		hasSurface = true;
		postInitCamera();
	}

	
	public void cancelCaptureActivity() {
		Intent intent = new Intent(getIntent().getAction());
		setResult(Activity.RESULT_CANCELED, intent);
		finish();
	}


	public void initCamera(SurfaceHolder surfaceHolder) {
		try {
			CameraManager.get(this).openDriver(this, surfaceHolder);
			CameraManager.get(this).startPreview();
			viewfinderView.setFramingRect(CameraManager.get(this).getFramingRect());
			viewfinderView.setResultPointConversionMatrix(CameraManager.get(this).getResultPointConversionMatrix());
		} catch (IOException ioe) {
			XLog.w("Camera init failed",ioe);
			ioe.printStackTrace();
			displayFrameworkBugMessageAndExit();
			return;
		} catch (RuntimeException e) {
			// Barcode Scanner has seen crashes in the wild of this variety:
			// java.?lang.?RuntimeException: Fail to connect to camera service
			XLog.w("Unexpected error initializating camera", e);
			displayFrameworkBugMessageAndExit();
			return;
		}
		// Adjust the size of the surface view to match the preview size so
		// aspect ratio is kept.
		if (aspectRatioView != null) {
			Point previewSize = CameraManager.get(this).getCameraPreviewSize();
			aspectRatioView.setAspectRatio(false, previewSize.x, previewSize.y);
		}
	}

	private void displayFrameworkBugMessageAndExit() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.app_name));
		builder.setMessage(getString(R.string.msg_camera_framework_bug));
		builder.setPositiveButton(R.string.button_ok, new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				cancelCaptureActivity();
			}
		});
		builder.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				cancelCaptureActivity();
			}
		});
		builder.show();
	}

	public void drawViewfinder() {
		viewfinderView.drawViewfinder();
	}

	private void showResultToast(String text, Bitmap bitmap) {
		LayoutInflater inflater = getLayoutInflater();
//		View layout = inflater.inflate(R.layout.result_toast, (ViewGroup) findViewById(R.id.result_toast));
//		if (text != null) {
//			TextView resultText = (TextView) layout.findViewById(R.id.result_text);
//			resultText.setText(R.string.scan_ok);
//		}
		Toast toast = new Toast(getApplicationContext());
		toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 0, 0);
		toast.setDuration(Toast.LENGTH_SHORT);
		//toast.setView(layout);
		toast.show();
	}

	class InitCameraHandler extends Handler {
		public InitCameraHandler() {
		}

		@Override
		public void handleMessage(Message message) {
			switch (message.what) {
			case R.id.init_camera: {
				SurfaceHolder surfaceHolder = surfaceView.getHolder();
				initCamera(surfaceHolder);
			}
				break;
			}
		}
	}

	@Override
	public void finish() {
		super.finish();
		//overridePendingTransition(0, 0);
	}
}
