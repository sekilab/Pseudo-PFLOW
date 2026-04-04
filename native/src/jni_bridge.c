#include <jni.h>
#include "kdtree.h"

/* Global tree instance — built once, queried from multiple threads */
static KDTree *g_tree = NULL;

/*
 * Class:     traj_NativeNearestNode
 * Method:    buildIndex
 * Signature: ([D[DI)V
 */
JNIEXPORT void JNICALL Java_traj_NativeNearestNode_buildIndex
  (JNIEnv *env, jclass cls, jdoubleArray jlons, jdoubleArray jlats, jint count) {

    /* Free previous tree if any */
    if (g_tree) {
        kdtree_free(g_tree);
        g_tree = NULL;
    }

    jdouble *lons = (*env)->GetDoubleArrayElements(env, jlons, NULL);
    jdouble *lats = (*env)->GetDoubleArrayElements(env, jlats, NULL);

    if (!lons || !lats) {
        if (lons) (*env)->ReleaseDoubleArrayElements(env, jlons, lons, JNI_ABORT);
        if (lats) (*env)->ReleaseDoubleArrayElements(env, jlats, lats, JNI_ABORT);
        return;
    }

    g_tree = kdtree_build(lons, lats, (int32_t)count);

    (*env)->ReleaseDoubleArrayElements(env, jlons, lons, JNI_ABORT);
    (*env)->ReleaseDoubleArrayElements(env, jlats, lats, JNI_ABORT);
}

/*
 * Class:     traj_NativeNearestNode
 * Method:    findNearest
 * Signature: (DD)I
 */
JNIEXPORT jint JNICALL Java_traj_NativeNearestNode_findNearest
  (JNIEnv *env, jclass cls, jdouble lon, jdouble lat) {

    if (!g_tree) return -1;
    return (jint)kdtree_find_nearest(g_tree, lon, lat);
}

/*
 * Class:     traj_NativeNearestNode
 * Method:    dispose
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_traj_NativeNearestNode_dispose
  (JNIEnv *env, jclass cls) {

    if (g_tree) {
        kdtree_free(g_tree);
        g_tree = NULL;
    }
}
