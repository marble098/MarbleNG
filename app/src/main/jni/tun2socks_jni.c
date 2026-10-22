/*
 * MARBLE_TUN2SOCKS_CORE_V222 — the JNI surface of the tun2socks datapath.
 * MARBLE_TUN2SOCKS_SHUTDOWN_V223 — stop with a budget, and never leak a JNI exception.
 *
 * libtun2socks.so is a Go c-shared library built by scripts/prepare-native.sh
 * from the pinned upstream tag (see native/tun2socks/main.go). It is loaded by
 * Tun2SocksTunnel before any of these methods can run, then resolved with
 * dlopen/dlsym so a build without the library (or a device whose ABI the APK
 * does not ship) fails a connection with a reason instead of failing class
 * initialisation with UnsatisfiedLinkError.
 *
 * The whole point of this file is the descriptor argument: Android's VpnService
 * creates the TUN device in this process, so the Go stack has to read it here.
 *
 * Ownership rule (V223): the descriptor belongs to the VpnService. The Go core
 * reads it and stops reading it, and it never closes it — MarbleVpnService
 * closes it once, after nativeStop() has reported that the datapath is finished.
 * So a build that still closes it on this side (or on the Go side) double-closes
 * a number the process may already have recycled, which is what a SIGABRT during
 * disconnect looks like from the outside.
 */
#include <jni.h>
#include <dlfcn.h>
#include <pthread.h>
#include <stddef.h>

#define T2S_UNAVAILABLE (-1000)
#define T2S_NO_SYMBOL (-1001)
#define T2S_JNI (-1002)

typedef int (*t2s_run_fn)(const char *proxy, int mtu, int fd, int udp_timeout_sec,
                          long long tcp_send_buffer, long long tcp_receive_buffer,
                          int moderate_receive_buffer,
                          const char *log_level, const char *log_path);
typedef void (*t2s_quit_fn)(void);
/* Optional: an older libtun2socks.so has no marble_t2s_stop. */
typedef int (*t2s_stop_fn)(int wait_ms);
typedef void (*t2s_stats_fn)(long long *tx_packets, long long *tx_bytes,
                             long long *rx_packets, long long *rx_bytes);
typedef const char *(*t2s_error_fn)(void);
typedef const char *(*t2s_version_fn)(void);

static pthread_mutex_t t2s_lock = PTHREAD_MUTEX_INITIALIZER;
static void *t2s_handle = NULL;
static t2s_run_fn t2s_run = NULL;
static t2s_quit_fn t2s_quit = NULL;
static t2s_stop_fn t2s_stop = NULL;
static t2s_stats_fn t2s_stats = NULL;
static t2s_error_fn t2s_error = NULL;
static t2s_version_fn t2s_version = NULL;

static int t2s_resolve(void) {
    pthread_mutex_lock(&t2s_lock);
    if (t2s_run != NULL) {
        pthread_mutex_unlock(&t2s_lock);
        return 0;
    }
    if (t2s_handle == NULL) {
        t2s_handle = dlopen("libtun2socks.so", RTLD_NOW | RTLD_LOCAL);
    }
    if (t2s_handle == NULL) {
        pthread_mutex_unlock(&t2s_lock);
        return T2S_UNAVAILABLE;
    }
    t2s_run = (t2s_run_fn)dlsym(t2s_handle, "marble_t2s_run");
    t2s_quit = (t2s_quit_fn)dlsym(t2s_handle, "marble_t2s_quit");
    t2s_stop = (t2s_stop_fn)dlsym(t2s_handle, "marble_t2s_stop");
    t2s_stats = (t2s_stats_fn)dlsym(t2s_handle, "marble_t2s_stats");
    t2s_error = (t2s_error_fn)dlsym(t2s_handle, "marble_t2s_error");
    t2s_version = (t2s_version_fn)dlsym(t2s_handle, "marble_t2s_version");
    int result = t2s_run != NULL && t2s_quit != NULL && t2s_stats != NULL ? 0 : T2S_NO_SYMBOL;
    pthread_mutex_unlock(&t2s_lock);
    return result;
}

/*
 * A failed GetStringUTFChars leaves an OutOfMemoryError pending. Returning to
 * Java with that exception still pending turns a readable "the datapath refused
 * this descriptor" into a thrown error in the service's cleanup path, so the
 * exception is cleared here and reported as a code instead.
 */
static void t2s_clear_exception(JNIEnv *env) {
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
    }
}

JNIEXPORT jint JNICALL
Java_com_marbleng_app_nativebridge_Tun2SocksTunnel_nativeRun(
        JNIEnv *env, jobject thiz, jstring proxy, jint mtu, jint fd, jint udp_timeout,
        jlong tcp_send_buffer, jlong tcp_receive_buffer, jboolean moderate_receive_buffer,
        jstring log_level, jstring log_path) {
    (void)thiz;
    int status = t2s_resolve();
    if (status != 0) return status;

    const char *proxy_chars = (*env)->GetStringUTFChars(env, proxy, NULL);
    if (proxy_chars == NULL) {
        t2s_clear_exception(env);
        return T2S_JNI;
    }
    const char *level_chars = (*env)->GetStringUTFChars(env, log_level, NULL);
    const char *path_chars = (*env)->GetStringUTFChars(env, log_path, NULL);

    int code = T2S_JNI;
    if (level_chars != NULL && path_chars != NULL) {
        code = t2s_run(proxy_chars, (int)mtu, (int)fd, (int)udp_timeout,
                       (long long)tcp_send_buffer, (long long)tcp_receive_buffer,
                       moderate_receive_buffer ? 1 : 0, level_chars, path_chars);
    } else {
        t2s_clear_exception(env);
    }

    if (path_chars != NULL) (*env)->ReleaseStringUTFChars(env, log_path, path_chars);
    if (level_chars != NULL) (*env)->ReleaseStringUTFChars(env, log_level, level_chars);
    (*env)->ReleaseStringUTFChars(env, proxy, proxy_chars);
    return (jint)code;
}

JNIEXPORT void JNICALL
Java_com_marbleng_app_nativebridge_Tun2SocksTunnel_nativeQuit(JNIEnv *env, jobject thiz) {
    (void)env;
    (void)thiz;
    if (t2s_resolve() == 0 && t2s_quit != NULL) t2s_quit();
}

/*
 * Signals the datapath and waits up to wait_ms for it to finish tearing down.
 * Returns 1 when nothing is running any more — which is the moment the caller may
 * close the TUN descriptor — and 0 when the budget expired.
 *
 * Reached through Tun2SocksTunnel.stopAndWait(): the service calls it before
 * closeHevFd() so the descriptor is never closed under a dispatcher that is still
 * polling it.
 */
JNIEXPORT jint JNICALL
Java_com_marbleng_app_nativebridge_Tun2SocksTunnel_nativeStop(JNIEnv *env, jobject thiz, jint wait_ms) {
    (void)env;
    (void)thiz;
    if (t2s_resolve() != 0) return 1;
    if (t2s_stop != NULL) return (jint)t2s_stop((int)wait_ms);
    /* Older library without marble_t2s_stop: signal and report unknown. */
    if (t2s_quit != NULL) t2s_quit();
    return 0;
}

JNIEXPORT jlongArray JNICALL
Java_com_marbleng_app_nativebridge_Tun2SocksTunnel_nativeStats(JNIEnv *env, jobject thiz) {
    (void)thiz;
    jlong values[4] = {0, 0, 0, 0};
    if (t2s_resolve() == 0 && t2s_stats != NULL) {
        long long tx_packets = 0, tx_bytes = 0, rx_packets = 0, rx_bytes = 0;
        t2s_stats(&tx_packets, &tx_bytes, &rx_packets, &rx_bytes);
        values[0] = (jlong)tx_packets;
        values[1] = (jlong)tx_bytes;
        values[2] = (jlong)rx_packets;
        values[3] = (jlong)rx_bytes;
    }
    jlongArray array = (*env)->NewLongArray(env, 4);
    if (array == NULL) {
        t2s_clear_exception(env);
        return NULL;
    }
    (*env)->SetLongArrayRegion(env, array, 0, 4, values);
    if ((*env)->ExceptionCheck(env)) {
        t2s_clear_exception(env);
        return NULL;
    }
    return array;
}

JNIEXPORT jstring JNICALL
Java_com_marbleng_app_nativebridge_Tun2SocksTunnel_nativeError(JNIEnv *env, jobject thiz) {
    (void)thiz;
    if (t2s_resolve() != 0 || t2s_error == NULL) return NULL;
    const char *message = t2s_error();
    if (message == NULL) return NULL;
    return (*env)->NewStringUTF(env, message);
}

JNIEXPORT jstring JNICALL
Java_com_marbleng_app_nativebridge_Tun2SocksTunnel_nativeVersion(JNIEnv *env, jobject thiz) {
    (void)thiz;
    if (t2s_resolve() != 0 || t2s_version == NULL) return NULL;
    const char *version = t2s_version();
    if (version == NULL) return NULL;
    return (*env)->NewStringUTF(env, version);
}
