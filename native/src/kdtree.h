#ifndef PFLOW_KDTREE_H
#define PFLOW_KDTREE_H

#include <stdint.h>

/**
 * 2D KD-tree for nearest-neighbor lookups on geographic coordinates.
 * Array-based implicit binary tree layout for cache-friendly access.
 * Thread-safe queries (read-only tree after build).
 */

typedef struct {
    double *lons;       // longitude array in tree order
    double *lats;       // latitude array in tree order
    int32_t *orig_idx;  // maps tree position -> original array index
    int32_t size;
} KDTree;

/**
 * Build a KD-tree from coordinate arrays.
 * @param lons   longitude array (copied internally)
 * @param lats   latitude array (copied internally)
 * @param count  number of points
 * @return pointer to built tree, or NULL on error
 */
KDTree* kdtree_build(const double *lons, const double *lats, int32_t count);

/**
 * Find the nearest point to (query_lon, query_lat).
 * Thread-safe — no mutable state accessed during query.
 * @return index into the original (pre-build) array, or -1 if tree is empty
 */
int32_t kdtree_find_nearest(const KDTree *tree, double query_lon, double query_lat);

/**
 * Free all memory associated with the tree.
 */
void kdtree_free(KDTree *tree);

#endif
