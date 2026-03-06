package truck.sim.spatial;

import java.util.*;
import java.io.*;

import org.geotools.data.*;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.Filter;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

/**
 * Safe shapefile loader that handles null geometries.
 * Based on dcity.aggr.ShpLoader but with null-safety for network shapefiles.
 */
public class SafeShpLoader {

    public static List<SimpleFeature> load(String filename) throws Exception {
        File file = new File(filename);
        Map<String, Object> map = new HashMap<>();
        map.put("url", file.toURI().toURL());
        DataStore dataStore = DataStoreFinder.getDataStore(map);
        String typeName = dataStore.getTypeNames()[0];

        SimpleFeatureSource source = dataStore.getFeatureSource(typeName);
        Filter filter = Filter.INCLUDE;
        SimpleFeatureCollection collection = source.getFeatures(filter);

        File prj = new File(filename.replaceFirst(".shp", ".prj"));
        MathTransform transform = null;
        String wkt;
        try (BufferedReader br = new BufferedReader(new FileReader(prj))) {
            wkt = br.readLine();
            CoordinateReferenceSystem sourceCRS = CRS.parseWKT(wkt);
            CoordinateReferenceSystem targetCRS = CRS.decode("EPSG:4326", true);

            // Try to look up EPSG code, but if it fails, use the parsed CRS directly
            String code = CRS.lookupIdentifier(sourceCRS, false);
            if (code != null) {
                sourceCRS = CRS.decode(code, true);
            }
            // If shapefile is already in geographic coordinates (lat/lon), transform may be identity
            transform = CRS.findMathTransform(sourceCRS, targetCRS, true); // lenient=true
        } catch (Exception e) {
            System.err.println("[SafeShpLoader] Warning: CRS transformation issue - " + e.getMessage());
        }

        List<SimpleFeature> features = new ArrayList<>();
        int nullGeomCount = 0;

        try(SimpleFeatureIterator iterator = collection.features();) {
             while (iterator.hasNext()) {
                 SimpleFeature feature = iterator.next();
                 Geometry geom = (Geometry)feature.getDefaultGeometry();

                 // SAFETY CHECK: Skip null geometries to prevent NPE during transformation
                 if (geom == null) {
                     nullGeomCount++;
                     continue;
                 }

                 // Apply transformation if available (may be null or identity transform)
                 if (transform != null && !transform.isIdentity()) {
                     geom = JTS.transform(geom, transform);
                 }

                 feature.setDefaultGeometry(geom);
                 features.add(feature);
             }
        }catch (Exception e) {
            System.err.println("[SafeShpLoader] Error loading features: " + e.getMessage());
        }

        if (nullGeomCount > 0) {
            System.out.println("[SafeShpLoader] Skipped " + nullGeomCount + " features with null geometries");
        }

        dataStore.dispose();

        return features;
    }
}
