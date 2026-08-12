MARBLENG_PATH := $(call my-dir)
HEV_PATH := $(MARBLENG_PATH)/hev
include $(HEV_PATH)/Android.mk
LOCAL_PATH := $(MARBLENG_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := marbleng
LOCAL_SRC_FILES := marbleng_jni.c
LOCAL_SHARED_LIBRARIES := hev-socks5-tunnel
LOCAL_LDLIBS := -llog
LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
include $(BUILD_SHARED_LIBRARY)
