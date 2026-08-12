#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stddef.h>
extern int hev_socks5_tunnel_main_from_str(const unsigned char *config_str, unsigned int config_len, int tun_fd);
extern void hev_socks5_tunnel_quit(void);
extern void hev_socks5_tunnel_stats(size_t *tx_packets, size_t *tx_bytes, size_t *rx_packets, size_t *rx_bytes);
JNIEXPORT jint JNICALL Java_com_marbleng_app_nativebridge_HevTunnel_run(JNIEnv *env,jclass c,jstring config,jint fd){
    const char *s=(*env)->GetStringUTFChars(env,config,0); if(!s)return -1; unsigned int n=(unsigned int)strlen(s); int r=hev_socks5_tunnel_main_from_str((const unsigned char*)s,n,(int)fd);(*env)->ReleaseStringUTFChars(env,config,s);return r;
}
JNIEXPORT void JNICALL Java_com_marbleng_app_nativebridge_HevTunnel_quit(JNIEnv *env,jclass c){hev_socks5_tunnel_quit();}
JNIEXPORT jlongArray JNICALL Java_com_marbleng_app_nativebridge_HevTunnel_stats(JNIEnv *env,jclass c){size_t tp=0,tb=0,rp=0,rb=0;hev_socks5_tunnel_stats(&tp,&tb,&rp,&rb);jlong v[4]={(jlong)tp,(jlong)tb,(jlong)rp,(jlong)rb};jlongArray a=(*env)->NewLongArray(env,4);(*env)->SetLongArrayRegion(env,a,0,4,v);return a;}
