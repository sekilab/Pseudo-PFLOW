#include "kdtree.h"
#include <stdlib.h>
#include <float.h>
#include <string.h>

/* ── Working arrays for construction (freed after build) ── */
static double *w_lons, *w_lats;
static int32_t *w_idx;

static void swap(int32_t i, int32_t j) {
    double tl = w_lons[i]; w_lons[i] = w_lons[j]; w_lons[j] = tl;
    double ta = w_lats[i]; w_lats[i] = w_lats[j]; w_lats[j] = ta;
    int32_t ti = w_idx[i]; w_idx[i] = w_idx[j]; w_idx[j] = ti;
}

/* Quickselect: partition so element at k is the median */
static void nth_element(int32_t lo, int32_t hi, int32_t k, int split_on_lon) {
    while (lo < hi) {
        int32_t pivot = lo + (hi - lo) / 2;
        double pval = split_on_lon ? w_lons[pivot] : w_lats[pivot];
        swap(pivot, hi);

        int32_t store = lo;
        for (int32_t i = lo; i < hi; i++) {
            double val = split_on_lon ? w_lons[i] : w_lats[i];
            if (val < pval) {
                swap(i, store);
                store++;
            }
        }
        swap(store, hi);

        if (store == k) return;
        else if (store < k) lo = store + 1;
        else hi = store - 1;
    }
}

/* Recursive tree build into implicit array layout */
static void build_tree(KDTree *t, int32_t lo, int32_t hi, int32_t tree_idx, int depth) {
    if (lo > hi || tree_idx >= t->size) return;

    if (lo == hi) {
        t->lons[tree_idx] = w_lons[lo];
        t->lats[tree_idx] = w_lats[lo];
        t->orig_idx[tree_idx] = w_idx[lo];
        return;
    }

    int32_t mid = lo + (hi - lo) / 2;
    int split_on_lon = (depth % 2 == 0);

    nth_element(lo, hi, mid, split_on_lon);

    t->lons[tree_idx] = w_lons[mid];
    t->lats[tree_idx] = w_lats[mid];
    t->orig_idx[tree_idx] = w_idx[mid];

    build_tree(t, lo, mid - 1, 2 * tree_idx + 1, depth + 1);
    build_tree(t, mid + 1, hi, 2 * tree_idx + 2, depth + 1);
}

KDTree* kdtree_build(const double *lons, const double *lats, int32_t count) {
    if (count <= 0 || !lons || !lats) return NULL;

    KDTree *t = (KDTree *)malloc(sizeof(KDTree));
    if (!t) return NULL;

    t->size = count;
    t->lons = (double *)malloc(count * sizeof(double));
    t->lats = (double *)malloc(count * sizeof(double));
    t->orig_idx = (int32_t *)malloc(count * sizeof(int32_t));

    if (!t->lons || !t->lats || !t->orig_idx) {
        kdtree_free(t);
        return NULL;
    }

    /* Allocate working copies */
    w_lons = (double *)malloc(count * sizeof(double));
    w_lats = (double *)malloc(count * sizeof(double));
    w_idx = (int32_t *)malloc(count * sizeof(int32_t));

    memcpy(w_lons, lons, count * sizeof(double));
    memcpy(w_lats, lats, count * sizeof(double));
    for (int32_t i = 0; i < count; i++) w_idx[i] = i;

    build_tree(t, 0, count - 1, 0, 0);

    /* Free working copies */
    free(w_lons); w_lons = NULL;
    free(w_lats); w_lats = NULL;
    free(w_idx);  w_idx = NULL;

    return t;
}

/* ── Nearest-neighbor query (thread-safe, no mutable state) ── */

static void search_nearest(const KDTree *t, int32_t tree_idx,
                           double q_lon, double q_lat, int depth,
                           int32_t *best_idx, double *best_dist_sq) {
    if (tree_idx >= t->size) return;

    double d_lon = q_lon - t->lons[tree_idx];
    double d_lat = q_lat - t->lats[tree_idx];
    double dist_sq = d_lon * d_lon + d_lat * d_lat;

    if (dist_sq < *best_dist_sq) {
        *best_dist_sq = dist_sq;
        *best_idx = t->orig_idx[tree_idx];
    }

    int split_on_lon = (depth % 2 == 0);
    double diff = split_on_lon ? d_lon : d_lat;

    int32_t near = (diff < 0) ? 2 * tree_idx + 1 : 2 * tree_idx + 2;
    int32_t far  = (diff < 0) ? 2 * tree_idx + 2 : 2 * tree_idx + 1;

    search_nearest(t, near, q_lon, q_lat, depth + 1, best_idx, best_dist_sq);

    if (diff * diff < *best_dist_sq) {
        search_nearest(t, far, q_lon, q_lat, depth + 1, best_idx, best_dist_sq);
    }
}

int32_t kdtree_find_nearest(const KDTree *tree, double query_lon, double query_lat) {
    if (!tree || tree->size == 0) return -1;

    int32_t best_idx = -1;
    double best_dist_sq = DBL_MAX;
    search_nearest(tree, 0, query_lon, query_lat, 0, &best_idx, &best_dist_sq);
    return best_idx;
}

void kdtree_free(KDTree *tree) {
    if (!tree) return;
    free(tree->lons);
    free(tree->lats);
    free(tree->orig_idx);
    free(tree);
}
