/* SPDX-License-Identifier: BSD-3-Clause */

#include <jni.h>
#include <stdint.h>
#include "oc2_slirp.h"

JNIEXPORT jlong JNICALL Java_li_cil_oc2_common_network_external_LibslirpNative_createNative(JNIEnv *env, jclass type) {
    (void) env; (void) type;
    return (jlong) (intptr_t) oc2_slirp_create();
}

JNIEXPORT void JNICALL Java_li_cil_oc2_common_network_external_LibslirpNative_destroyNative(JNIEnv *env, jclass type, jlong handle) {
    (void) env; (void) type;
    oc2_slirp_destroy((oc2_slirp *) (intptr_t) handle);
}

JNIEXPORT void JNICALL Java_li_cil_oc2_common_network_external_LibslirpNative_inputNative(JNIEnv *env, jclass type, jlong handle, jbyteArray frame) {
    (void) type;
    const jsize length = (*env)->GetArrayLength(env, frame);
    jbyte *data = (*env)->GetByteArrayElements(env, frame, NULL);
    if (data) {
        oc2_slirp_input((oc2_slirp *) (intptr_t) handle, (const uint8_t *) data, (size_t) length);
        (*env)->ReleaseByteArrayElements(env, frame, data, JNI_ABORT);
    }
}

JNIEXPORT void JNICALL Java_li_cil_oc2_common_network_external_LibslirpNative_pollNative(JNIEnv *env, jclass type, jlong handle, jint timeout) {
    (void) env; (void) type;
    oc2_slirp_poll((oc2_slirp *) (intptr_t) handle, timeout);
}

JNIEXPORT jint JNICALL Java_li_cil_oc2_common_network_external_LibslirpNative_nextFrameNative(JNIEnv *env, jclass type, jlong handle, jbyteArray destination) {
    (void) type;
    const jsize capacity = (*env)->GetArrayLength(env, destination);
    jbyte *data = (*env)->GetByteArrayElements(env, destination, NULL);
    if (!data) return 0;
    const size_t length = oc2_slirp_next_frame((oc2_slirp *) (intptr_t) handle, (uint8_t *) data, (size_t) capacity);
    (*env)->ReleaseByteArrayElements(env, destination, data, 0);
    return (jint) length;
}
