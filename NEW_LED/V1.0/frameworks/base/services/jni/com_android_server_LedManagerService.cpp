#define TAG "LedManagerService"

#include "jni.h"
#include "JNIHelp.h"
#include "android_runtime/AndroidRuntime.h"

#include <utils/misc.h>
#include <utils/Log.h>
#include <hardware/hardware.h>
#include <hardware/lights.h>

#include <stdio.h>

#define DEBUG 1

namespace android
{

struct Device {
    oem_light_device_t* light;
};

static oem_light_device_t* get_device(hw_module_t* module, char const* name)
{
    int err;
    hw_device_t* device;
    err = module->methods->open(module, name, &device);
    if (err == 0) {
        return (oem_light_device_t*)device;
    } else {
        return NULL;
    }
}

static jint init_native(JNIEnv *env, jobject clazz)
{
    int err;
    hw_module_t* module;
    Device* device;

    device = (Device*)malloc(sizeof(Device));

    err = hw_get_module(LIGHTS_HARDWARE_MODULE_ID, (hw_module_t const**)&module);
    if (err == 0) {
        device->light = get_device(module, LIGHT_ID_OEM_LED);

    } else {
        memset(device, 0, sizeof(Device));
    }

    return (jint)device;
}

static void setLed_native(JNIEnv *env, jobject clazz, int ptr,
        int led1, int onMs, int offMs, int option)
{
    Device* device = (Device*)ptr;
    oem_light_state_t state;

    if (device->light == NULL) {
        if(DEBUG) __android_log_print(ANDROID_LOG_ERROR, TAG, "setLed_native: light is null");
        return;
    }

    memset(&state, 0, sizeof(oem_light_state_t));

    /* undefined yet */
    state.led1 = led1;
    state.flashOnMS = onMs;
    state.flashOffMS = offMs;
    state.option = option;

    if(DEBUG) __android_log_print(ANDROID_LOG_DEBUG, TAG, "setLed_native: led1 : 0x%08x, onMs: %d, offMs: %d, option: %d", led1, onMs, offMs, option);
    device->light->oem_set_light(device->light, &state);
}


static JNINativeMethod method_table[] = {
    { "init_native", "()I", (void*)init_native },
    { "setLed_native", "(IIIII)V", (void*)setLed_native },
};

int register_android_server_LedManagerService(JNIEnv *env)
{
    return jniRegisterNativeMethods(env, "com/android/server/LedManagerService",
            method_table, NELEM(method_table));
}

};
