#include <jni.h>
#include "kdtree.h"

/* Support up to 4 named tree slots (full, highway, etc.) */
#define MAX_TREES 4
static KDTree *g_trees[MAX_TREES] = {NULL, NULL, NULL, NULL};

/*
 * Class:     traj_NativeNearestNode
 * Method:    buildIndex
 * Signature: (I[D[DI)V
 */
JNIEXPORT void JNICALL Java_traj_NativeNearestNode_buildIndex
  (JNIEnv *env, jclass cls, jint slot, jdoubleArray jlons, jdoubleArray jlats, jint count) {

    if (slot < 0 || slot >= MAX_TREES) return;

    /* Free previous tree in this slot */
    if (g_trees[slot]) {
        kdtree_free(g_trees[slot]);
        g_trees[slot] = NULL;
    }

    jdouble *lons = (*env)->GetDoubleArrayElements(env, jlons, NULL);
    jdouble *lats = (*env)->GetDoubleArrayElements(env, jlats, NULL);

    if (!lons || !lats) {
        if (lons) (*env)->ReleaseDoubleArrayElements(env, jlons, lons, JNI_ABORT);
        if (lats) (*env)->ReleaseDoubleArrayElements(env, jlats, lats, JNI_ABORT);
        return;
    }

    g_trees[slot] = kdtree_build(lons, lats, (int32_t)count);

    (*env)->ReleaseDoubleArrayElements(env, jlons, lons, JNI_ABORT);
    (*env)->ReleaseDoubleArrayElements(env, jlats, lats, JNI_ABORT);
}

/*
 * Class:     traj_NativeNearestNode
 * Method:    findNearest
 * Signature: (IDD)I
 */
JNIEXPORT jint JNICALL Java_traj_NativeNearestNode_findNearest
  (JNIEnv *env, jclass cls, jint slot, jdouble lon, jdouble lat) {

    if (slot < 0 || slot >= MAX_TREES || !g_trees[slot]) return -1;
    return (jint)kdtree_find_nearest(g_trees[slot], lon, lat);
}

/*
 * Class:     traj_NativeNearestNode
 * Method:    dispose
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_traj_NativeNearestNode_dispose
  (JNIEnv *env, jclass cls, jint slot) {

    if (slot < 0 || slot >= MAX_TREES) return;
    if (g_trees[slot]) {
        kdtree_free(g_trees[slot]);
        g_trees[slot] = NULL;
    }
}
