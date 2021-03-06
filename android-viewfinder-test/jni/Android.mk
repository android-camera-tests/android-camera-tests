LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)


# OpenCV
OPENCV_CAMERA_MODULES:=off
OPENCV_INSTALL_MODULES:=on


include ./$(LOCAL_PATH)/../includeOpenCV.mk
ifeq ("$(wildcard $(OPENCV_MK_PATH))","")
	#try to load OpenCV.mk from default install location
	include $(TOOLCHAIN_PREBUILT_ROOT)/user/share/OpenCV/OpenCV.mk
else
	include $(OPENCV_MK_PATH)
endif


LOCAL_MODULE    := opencvutil
LOCAL_SRC_FILES := opencvutil_jni.cpp
LOCAL_LDLIBS +=  -llog -ldl
LOCAL_LDFLAGS += -ljnigraphics

include $(BUILD_SHARED_LIBRARY)
