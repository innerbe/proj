/*
 * Copyright (C) 2008 The Android Open Source Project
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


// #define LOG_NDEBUG 0
#define LOG_TAG "lights"

#include <cutils/log.h>

#include <stdint.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>

#include <sys/ioctl.h>
#include <sys/types.h>

#include <hardware/lights.h>

//+US1-CF1
#define FW_VENDOR_OEM_LED
#define DEBUG 0
//-US1-CF1

/******************************************************************************/

static pthread_once_t g_init = PTHREAD_ONCE_INIT;
static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
static struct light_state_t g_notification;
static struct light_state_t g_battery;
static int g_attention = 0;

#ifdef FW_VENDOR_OEM_LED
int g_notificationOn = 0;
int g_batteryOn = 0;
unsigned int g_batteryColor = 0;
#endif
char const*const RED_LED_FILE
        = "/sys/class/leds/red/brightness";

char const*const GREEN_LED_FILE
        = "/sys/class/leds/green/brightness";

char const*const BLUE_LED_FILE
        = "/sys/class/leds/blue/brightness";

char const*const LCD_FILE
        = "/sys/class/leds/lcd-backlight/brightness";

/**
 * device methods
 */

void init_globals(void)
{
    // init the mutex
    pthread_mutex_init(&g_lock, NULL);
}

static int
write_int(char const* path, int value)
{
    int fd;
    static int already_warned = 0;

    fd = open(path, O_RDWR);
#ifdef FW_VENDOR_OEM_LED
    if(DEBUG && (strstr(path, "backlight") == NULL))
        __android_log_print(ANDROID_LOG_INFO, "OEMLED", "write_int() path:%s, value:0x%02x, fd:%d", path, value, fd); 
#endif
    if (fd >= 0) {
        char buffer[20];
        int bytes = sprintf(buffer, "%d\n", value);
        int amt = write(fd, buffer, bytes);
        close(fd);
        return amt == -1 ? -errno : 0;
    } else {
        if (already_warned == 0) {
            ALOGE("write_int failed to open %s\n", path);
            already_warned = 1;
        }
        return -errno;
    }
}

static int
is_lit(struct light_state_t const* state)
{
    return state->color & 0x00ffffff;
}

static int
rgb_to_brightness(struct light_state_t const* state)
{
    int color = state->color & 0x00ffffff;
    return ((77*((color>>16)&0x00ff))
            + (150*((color>>8)&0x00ff)) + (29*(color&0x00ff))) >> 8;
}

static int
set_light_backlight(struct light_device_t* dev,
        struct light_state_t const* state)
{
    int err = 0;
    int brightness = rgb_to_brightness(state);
    pthread_mutex_lock(&g_lock);
    err = write_int(LCD_FILE, brightness);
    pthread_mutex_unlock(&g_lock);
    return err;
}

static int
set_speaker_light_locked(struct light_device_t* dev,
        struct light_state_t const* state)
{
    int len;
    int alpha, red, green, blue;
    int blink, freq, pwm;
    int onMS, offMS;
    unsigned int colorRGB;

    switch (state->flashMode) {
        case LIGHT_FLASH_TIMED:
            onMS = state->flashOnMS;
            offMS = state->flashOffMS;
            break;
        case LIGHT_FLASH_NONE:
        default:
            onMS = 0;
            offMS = 0;
            break;
    }

    colorRGB = state->color;

#if 0
    ALOGD("set_speaker_light_locked colorRGB=%08X, onMS=%d, offMS=%d\n",
            colorRGB, onMS, offMS);
#endif

    red = (colorRGB >> 16) & 0xFF;
    green = (colorRGB >> 8) & 0xFF;
    blue = colorRGB & 0xFF;

    // R, G, B value is among 0, 1, 2
    if (red > 128)  red = 2;
    else if (red <= 128 && red > 0) red = 1;
    if (green > 128)  green = 2;
    else if (green <= 128 && green > 0) green = 1;
    if (blue > 128)  blue = 2;
    else if (blue <= 128 && blue > 0) red = 1;

    write_int(RED_LED_FILE, red);
    write_int(GREEN_LED_FILE, green);
    write_int(BLUE_LED_FILE, blue);

    // TODO
    if (onMS > 0 && offMS > 0) {
        int totalMS = onMS + offMS;

        // the LED appears to blink about once per second if freq is 20
        // 1000ms / 20 = 50
        freq = totalMS / 50;
        // pwm specifies the ratio of ON versus OFF
        // pwm = 0 -> always off
        // pwm = 255 => always on
        pwm = (onMS * 255) / totalMS;

        // the low 4 bits are ignored, so round up if necessary
        if (pwm > 0 && pwm < 16)
            pwm = 16;

        blink = 1;
    } else {
        blink = 0;
        freq = 0;
        pwm = 0;
    }

    if (blink) {
        write_int(RED_LED_FILE, freq);
    }

    return 0;
}

static void
handle_speaker_battery_locked(struct light_device_t* dev)
{
    if (is_lit(&g_battery)) {
        set_speaker_light_locked(dev, &g_battery);
    } else {
        set_speaker_light_locked(dev, &g_notification);
    }
}

static int
set_light_battery(struct light_device_t* dev,
        struct light_state_t const* state)
{
    pthread_mutex_lock(&g_lock);
#ifdef FW_VENDOR_OEM_LED
    int red, green, blue;

    g_batteryColor = state->color;
    g_batteryOn = (g_batteryColor & 0x00FFFFFF) ? 1 : 0;
    if(DEBUG)
        __android_log_print(ANDROID_LOG_INFO, "OEMLED", "batteryOn : %d", g_batteryOn);
    if(!g_notificationOn) {
        // set LED
        red = (g_batteryColor >> 16) & 0xFF;
        green = (g_batteryColor >> 8) & 0xFF;
        blue = g_batteryColor & 0xFF;

        write_int(RED_LED_FILE, red);
        write_int(GREEN_LED_FILE, green);
        write_int(BLUE_LED_FILE, blue);
    }
#else
    handle_speaker_battery_locked(dev);
#endif
    pthread_mutex_unlock(&g_lock);
    return 0;
}

static int
set_light_notifications(struct light_device_t* dev,
        struct light_state_t const* state)
{
    pthread_mutex_lock(&g_lock);
#ifdef FW_VENDOR_OEM_LED
    // do nothing
#else
    g_notification = *state;
    handle_speaker_battery_locked(dev);
#endif
    pthread_mutex_unlock(&g_lock);
    return 0;
}

static int
set_light_attention(struct light_device_t* dev,
        struct light_state_t const* state)
{
    pthread_mutex_lock(&g_lock);
#ifdef FW_VENDOR_OEM_LED
    // do nothing
#else
    if (state->flashMode == LIGHT_FLASH_HARDWARE) {
        g_attention = state->flashOnMS;
    } else if (state->flashMode == LIGHT_FLASH_NONE) {
        g_attention = 0;
    }
    handle_speaker_battery_locked(dev);
#endif
    pthread_mutex_unlock(&g_lock);
    return 0;
}

#ifdef FW_VENDOR_OEM_LED
static int
set_light_oem_led(struct oem_light_device_t* dev,
                           struct oem_light_state_t const* state)
{
    if(DEBUG)
        __android_log_print(ANDROID_LOG_INFO, "OEMLED", "------------begin set_light_oem_led()-----------\n");
    pthread_mutex_lock(&g_lock);

    __android_log_print(ANDROID_LOG_ERROR, "OEMLED", "set_light_oem_led: led1 : 0x%08x, led2 : 0x%08x, led3 : 0x%08x, led4 : 0x%08x, led5 : 0x%08x, led6 : 0x%08x, led7 : 0x%08x, onMs: %d, offMs: %d, option: %d", state->led1, state->led2, state->led3, state->led4, state->led5, state->led6, state->led7, state->flashOnMS, state->flashOffMS, state->option);

    /*
    write_int(RED_LED_FILE, red);
    write_int(GREEN_LED_FILE, green);
    write_int(BLUE_LED_FILE, blue);
    */

    if(DEBUG)
        __android_log_print(ANDROID_LOG_INFO, "OEMLED", "------------end. set_light_oem_led()-----------\n");
    pthread_mutex_unlock(&g_lock);
    return 0;
}
#endif

/** Close the lights device */
static int
close_lights(struct light_device_t *dev)
{
    if (dev) {
        free(dev);
    }
    return 0;
}

static int
oem_close_lights(struct oem_light_device_t *dev)
{
    if (dev) {
        free(dev);
    }
    return 0;
}



/******************************************************************************/

/**
 * module methods
 */

/** Open a new instance of a lights device using name */
static int open_lights(const struct hw_module_t* module, char const* name,
        struct hw_device_t** device)
{
    int (*set_light)(struct light_device_t* dev,
            struct light_state_t const* state);

//+US1-CF1
#ifdef FW_VENDOR_OEM_LED
    int (*oem_set_light)(struct oem_light_device_t* dev,
            struct oem_light_state_t const* state);
#endif
//-US1-CF1

    if (0 == strcmp(LIGHT_ID_BACKLIGHT, name))
        set_light = set_light_backlight;
    /*
    else if (0 == strcmp(LIGHT_ID_BATTERY, name))
        set_light = set_light_battery;
    else if (0 == strcmp(LIGHT_ID_NOTIFICATIONS, name))
        set_light = set_light_notifications;
    else if (0 == strcmp(LIGHT_ID_ATTENTION, name))
        set_light = set_light_attention;
        */
//+US1-CF1
#ifdef FW_VENDOR_OEM_LED
    else if (0 == strcmp(LIGHT_ID_OEM_LED, name)) {
        if(DEBUG)
            __android_log_print(ANDROID_LOG_INFO, "OEMLED", "lights.c open_lights(). name:%s", name);
        oem_set_light = set_light_oem_led;

        pthread_once(&g_init, init_globals);

        struct oem_light_device_t *dev = malloc(sizeof(struct oem_light_device_t));
        memset(dev, 0, sizeof(*dev));

        dev->common.tag = HARDWARE_DEVICE_TAG;
        dev->common.version = 0;
        dev->common.module = (struct hw_module_t*)module;
        dev->common.close = (int (*)(struct hw_device_t*))oem_close_lights;
        dev->oem_set_light = oem_set_light;

        *device = (struct hw_device_t*)dev;
        return 0;
    }
#endif
//-US1-CF1
    else
        return -EINVAL;

    pthread_once(&g_init, init_globals);

    struct light_device_t *dev = malloc(sizeof(struct light_device_t));
    memset(dev, 0, sizeof(*dev));

    dev->common.tag = HARDWARE_DEVICE_TAG;
    dev->common.version = 0;
    dev->common.module = (struct hw_module_t*)module;
    dev->common.close = (int (*)(struct hw_device_t*))close_lights;
    dev->set_light = set_light;

    *device = (struct hw_device_t*)dev;
    return 0;
}

static struct hw_module_methods_t lights_module_methods = {
    .open =  open_lights,
};

/*
 * The lights Module
 */
struct hw_module_t HAL_MODULE_INFO_SYM = {
    .tag = HARDWARE_MODULE_TAG,
    .version_major = 1,
    .version_minor = 0,
    .id = LIGHTS_HARDWARE_MODULE_ID,
    .name = "lights Module",
    .author = "Google, Inc.",
    .methods = &lights_module_methods,
};
