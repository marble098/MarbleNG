/*
 * MarbleNG JNI bridge for hev-socks5-tunnel (the default datapath).
 *
 * MARBLE_FDSAN_DOUBLE_CLOSE_V224 — the descriptor argument is borrowed, and
 * borrowed descriptors are never handed to code that might close them.
 *
 * `tunFd` is the VpnService's duplicated TUN descriptor (`hevFd` in
 * MarbleVpnService). The service closes that number exactly once, after this
 * run returns, with ParcelFileDescriptor.adoptFd(fd).close() — a close that
 * carries an fdsan owner tag, so if the number were already closed at kernel
 * level bionic would turn the EBADF into "fdsan: double-close of file
 * descriptor %d detected" and abort the process. That is exactly how the
 * tun2socks datapath died (its gVisor stack closed the borrowed number from
 * inside Stack.Wait(); see native/tun2socks/main.go).
 *
 * HEV itself never closes an externally provided descriptor
 * (tunnel_fini() returns early when !tun_fd_local), so passing the borrowed
 * number through has been safe so far. But the HEV pin is auto-bumped by
 * update-core-lock, and "safe because the current upstream happens not to
 * close it" is how V224 happened on the other datapath. So this bridge now
 * applies the same rule as the Go wrapper: duplicate the borrowed descriptor
 * with F_DUPFD_CLOEXEC, give HEV the dup this bridge owns, and close only
 * that dup when the blocking run returns — the exact moment the service used
 * to close the original. Two numbers for one TUN device, each with one owner
 * and one close. The close below is a plain libc close() of a descriptor this
 * file created via fcntl (fdsan-unowned), so even a future HEV that closed
 * its dup would degrade this to a harmless EBADF, not an abort.
 */
#include <jni.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stddef.h>
#include <unistd.h>
extern int hev_socks5_tunnel_main_from_str(const unsigned char *config_str, unsigned int config_len, int tun_fd);
extern void hev_socks5_tunnel_quit(void);
extern void hev_socks5_tunnel_stats(size_t *tx_packets, size_t *tx_bytes, size_t *rx_packets, size_t *rx_bytes);
JNIEXPORT jint JNICALL Java_com_marbleng_app_nativebridge_HevTunnel_run(JNIEnv *env,jclass c,jstring config,jint fd){
    const char *s=(*env)->GetStringUTFChars(env,config,0); if(!s)return -1; unsigned int n=(unsigned int)strlen(s);
    int owned_fd=fcntl((int)fd,F_DUPFD_CLOEXEC,0); if(owned_fd<0){(*env)->ReleaseStringUTFChars(env,config,s);return -1;}
    int r=hev_socks5_tunnel_main_from_str((const unsigned char*)s,n,owned_fd);
    close(owned_fd);
    (*env)->ReleaseStringUTFChars(env,config,s);return r;
}
JNIEXPORT void JNICALL Java_com_marbleng_app_nativebridge_HevTunnel_quit(JNIEnv *env,jclass c){hev_socks5_tunnel_quit();}
JNIEXPORT jlongArray JNICALL Java_com_marbleng_app_nativebridge_HevTunnel_stats(JNIEnv *env,jclass c){size_t tp=0,tb=0,rp=0,rb=0;hev_socks5_tunnel_stats(&tp,&tb,&rp,&rb);jlong v[4]={(jlong)tp,(jlong)tb,(jlong)rp,(jlong)rb};jlongArray a=(*env)->NewLongArray(env,4);(*env)->SetLongArrayRegion(env,a,0,4,v);return a;}
