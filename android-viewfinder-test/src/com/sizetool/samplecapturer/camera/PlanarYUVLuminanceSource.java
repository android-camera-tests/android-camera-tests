/*
 * Copyright 2009 ZXing authors
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

import android.graphics.Bitmap;


/**
 * This object extends LuminanceSource around an array of YUV data returned from the camera driver,
 * with the option to crop to a rectangle within the full data. This can be used to exclude
 * superfluous pixels around the perimeter and speed up decoding.
 *
 * It works for any pixel format where the Y channel is planar and appears first, including
 * YCbCr_420_SP and YCbCr_422_SP.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class PlanarYUVLuminanceSource extends RenderableLuminanceSource {

  private final byte[] yuvData;
  private final int dataWidth;
  private final int dataHeight;
  private final int left;
  private final int top;

  public PlanarYUVLuminanceSource(byte[] yuvData,
                                  int dataWidth,
                                  int dataHeight,
                                  int left,
                                  int top,
                                  int width,
                                  int height,
                                  boolean reverseHorizontal) {
    super(width, height);

    if (left + width > dataWidth || top + height > dataHeight) {
      throw new IllegalArgumentException("Crop rectangle does not fit within image data.");
    }

    this.yuvData = yuvData;
    this.dataWidth = dataWidth;
    this.dataHeight = dataHeight;
    this.left = left;
    this.top = top;
    if (reverseHorizontal) {
      reverseHorizontal(width, height);
    }
  }
  
  public byte[] getOrigData(){
	  return yuvData;
  }
  
  public int getOrigDataWidth(){
	  return dataWidth;
  }
  
  public int getOrigDataHeight(){
	  return dataHeight;
  }
  

  public int getDataWidth() {
    return dataWidth;
  }

  public int getDataHeight() {
    return dataHeight;
  }

  
  // http://www.fourcc.org/yuv.php
  /*
   *    int r1, g1, b1;
   int c = y-16, d = u - 128, e = v - 128;       

   r1 = (298 * c           + 409 * e + 128) >> 8;
   g1 = (298 * c - 100 * d - 208 * e + 128) >> 8;
   b1 = (298 * c + 516 * d           + 128) >> 8;

   // Even with proper conversion, some values still need clipping.

   if (r1 > 255) r1 = 255;
   if (g1 > 255) g1 = 255;
   if (b1 > 255) b1 = 255;
   if (r1 < 0) r1 = 0;
   if (g1 < 0) g1 = 0;
   if (b1 < 0) b1 = 0;

    *r = r1 ;
   *g = g1 ;
   *b = b1 ;
   */
  
//  Actually creates an RGB image. 
  public Bitmap renderCroppedGreyscaleBitmap() {
    int width = getWidth();
    int height = getHeight();
    int[] pixels = new int[width * height];
    byte[] yuv = yuvData;
    int inputOffset = top * dataWidth + left;
    int inputOffsetUV = dataWidth*dataHeight + top/2 * dataWidth + left;

    for (int y = 0; y < height; y++) {
      int outputOffset = y * width;
      for (int x = 0; x < width; x++) {
         int offsetUV = (inputOffsetUV + x) & 0xFFFFFFFE;
    	   int c = yuv[inputOffset + x] & 0xff-16;
    	   int d =  yuv[offsetUV] & 0xff - 128; 
    	   int e = yuv[offsetUV+1] & 0xff - 128;       

    	   int r1 = (298 * c           + 409 * e + 128) >> 8;
    	   int g1 = (298 * c - 100 * d - 208 * e + 128) >> 8;
    	   int b1 = (298 * c + 516 * d           + 128) >> 8;

    	   if (r1 > 255) r1 = 255;
    	   if (g1 > 255) g1 = 255;
    	   if (b1 > 255) b1 = 255;
    	   if (r1 < 0) r1 = 0;
    	   if (g1 < 0) g1 = 0;
    	   if (b1 < 0) b1 = 0;
    	   
    	   //pixels[outputOffset + x] = 0xFF000000 | (y_val * 0x00010101);
    	   pixels[outputOffset + x] = 0xFF000000 | r1<<16 | b1<<8 | g1;
      }
      inputOffset += dataWidth;
      if (((top+y) & 0x1) == 0x1) {
    	  inputOffsetUV += dataWidth;
      }
    }

    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    return bitmap;
  }

  private int getHeight() {
	// TODO Auto-generated method stub
	return 0;
}

private int getWidth() {
	// TODO Auto-generated method stub
	return 0;
}

private void reverseHorizontal(int width, int height) {
    byte[] yuvData = this.yuvData;
    for (int y = 0, rowStart = top * dataWidth + left; y < height; y++, rowStart += dataWidth) {
      int middle = rowStart + width / 2;
      for (int x1 = rowStart, x2 = rowStart + width - 1; x1 < middle; x1++, x2--) {
        byte temp = yuvData[x1];
        yuvData[x1] = yuvData[x2];
        yuvData[x2] = temp;
      }
    }
  }
}
