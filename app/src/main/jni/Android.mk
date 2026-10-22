MARBLENG_PATH := $(call my-dir)
HEV_PATH := $(MARBLENG_PATH)/hev
include $(HEV_PATH)/Android.mk
LOCAL_PATH := $(MARBLENG_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := marbleng
LOCAL_SRC_FILES := marbleng_jni.c tun2socks_jni.c
LOCAL_SHARED_LIBRARIES := hev-socks5-tunnel
# MARBLE_TUN2SOCKS_CORE_V222 — -ldl resolves the Go c-shared library that Kotlin loads with
# System.loadLibrary("tun2socks"). It is a separate library on purpose: the wrapper can be
# rebuilt from a new upstream tag without relinking HEV.
LOCAL_LDLIBS := -llog -ldl
LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
include $(BUILD_SHARED_LIBRARY)
