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

import com.sizetool.samplecapturer.util.XLog;

import android.content.Context;
import android.graphics.Point;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

final class PreviewCallback implements Camera.PreviewCallback {
	private Handler previewHandler;
	private int previewMessageId;
	private CameraManager mCameraManager;
	private Context mContext;

	PreviewCallback(Context context, CameraManager manager) {
		this.mContext = context;
		mCameraManager = manager;
	}

	void setHandler(Handler previewHandler, int previewMessageId) {
		this.previewHandler = previewHandler;
		this.previewMessageId = previewMessageId;
	}

	public void onPreviewFrame(byte[] data, Camera camera) {
		if (data == null) {
			XLog.d("Got preview callback, but data[] was null!");
		}
		Point cameraResolution = mCameraManager.getCameraPreviewSize();
		if (previewHandler != null) {
			RenderableLuminanceSource source = null;
			try {
				source = mCameraManager.buildLuminanceSource(data, cameraResolution.x, cameraResolution.y);
			} catch (IllegalArgumentException e) {
			}
			previewHandler = null;
		} else {
			XLog.d("Got preview callback, but no handler for it");
		}
	}
}
