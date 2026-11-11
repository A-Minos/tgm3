/* Original Android JNI bridge. No game code or resources. */
#include <jni.h>
#include <stdio.h>
#include "guest_pages.h"

JNIEXPORT jstring JNICALL
Java_org_example_portlab_MainActivity_nativeSmoke(JNIEnv *env, jclass clazz) {
    (void)clazz;
    char report[2048] = {0};
    char result[2112];
    int status = gpl_run_selftests(report, sizeof(report));
    report[sizeof(report) - 1] = '\0';
    snprintf(result, sizeof(result), "Native smoke: %s (code %d)\n%s",
             status == 0 ? "PASS" : "FAIL", status, report);
    return (*env)->NewStringUTF(env, result);
}
