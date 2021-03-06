LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
    com_android_server_AlarmManagerService.cpp \
    com_android_server_BatteryService.cpp \
    com_android_server_input_InputApplicationHandle.cpp \
    com_android_server_input_InputManagerService.cpp \
    com_android_server_input_InputWindowHandle.cpp \
    com_android_server_LightsService.cpp \
    com_android_server_PowerManagerService.cpp \
    com_android_server_SerialService.cpp \
    com_android_server_SystemServer.cpp \
    com_android_server_UsbDeviceManager.cpp \
    com_android_server_UsbHostManager.cpp \
    com_android_server_VibratorService.cpp \
    com_android_server_location_GpsLocationProvider.cpp \
    com_android_server_connectivity_Vpn.cpp \
    com_android_server_DevicePolicyManagerService.cpp \
    onload.cpp

//+US1-CF1
//Feature : FW_VENDOR_OEM_LED
LOCAL_SRC_FILES += \
    com_android_server_LedManagerService.cpp
//-US1-CF1

LOCAL_C_INCLUDES += \
    $(JNI_H_INCLUDE) \
    frameworks/base/services \
    frameworks/base/core/jni \
    external/skia/include/core \
    libcore/include \
    libcore/include/libsuspend \
    $(call include-path-for, libhardware)/hardware \
    $(call include-path-for, libhardware_legacy)/hardware_legacy \

ifeq ($(BOARD_USES_ALSA_AUDIO),true)
    LOCAL_CFLAGS += -DALSA_HEADSET_DETECTION
    LOCAL_CFLAGS += -include $(TARGET_OUT_INTERMEDIATES)/KERNEL_OBJ/usr/include/linux/input.h
    LOCAL_ADDITIONAL_DEPENDENCIES := $(TARGET_OUT_INTERMEDIATES)/KERNEL_OBJ/usr
endif

LOCAL_SHARED_LIBRARIES := \
    libandroid_runtime \
    libandroidfw \
    libcutils \
    libhardware \
    libhardware_legacy \
    libnativehelper \
    libsystem_server \
    libutils \
    libui \
    libinput \
    libskia \
    libgui \
    libusbhost \
    libpam_proc \
		libdl \
    libsuspend

ifeq ($(WITH_MALLOC_LEAK_CHECK),true)
    LOCAL_CFLAGS += -DMALLOC_LEAK_CHECK
endif

LOCAL_MODULE:= libandroid_servers

include $(BUILD_SHARED_LIBRARY)
