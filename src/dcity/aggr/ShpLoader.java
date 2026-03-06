package dcity.aggr;

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

public class ShpLoader {

	public static List<SimpleFeature> load(String filename) throws Exception {
		return load(filename, null, null, null);
	}

	/**
	 * Load features from shapefile with optional attribute-based filtering.
	 * When filter parameters are provided, only features whose attribute value
	 * matches exactValues or starts with a prefix in prefixValues are loaded
	 * and CRS-transformed. Non-matching features are skipped entirely.
	 *
	 * @param filename      Path to .shp file
	 * @param filterAttr    Attribute name to filter on (e.g. "N03_007"), or null for no filter
	 * @param exactValues   Set of exact attribute values to include, or null
	 * @param prefixValues  Set of 2-char prefixes to include (for wildcard patterns), or null
	 * @return List of matching features with CRS-transformed geometries
	 */
	public static List<SimpleFeature> load(String filename, String filterAttr,
			Set<String> exactValues, Set<String> prefixValues) throws Exception {
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
			System.err.println("[ShpLoader] Warning: CRS transformation issue - " + e.getMessage());
			e.printStackTrace();
		}

		boolean useFilter = (filterAttr != null &&
				((exactValues != null && !exactValues.isEmpty()) ||
				 (prefixValues != null && !prefixValues.isEmpty())));
		int skipped = 0;

		List<SimpleFeature> features = new ArrayList<>();
		try(SimpleFeatureIterator iterator = collection.features();) {
			 while (iterator.hasNext()) {
				 SimpleFeature feature = iterator.next();

				 // Pre-filter by attribute before expensive CRS transform
				 if (useFilter) {
					 Object attrObj = feature.getAttribute(filterAttr);
					 if (attrObj == null) { skipped++; continue; }
					 String attrVal = attrObj.toString().trim();
					 if (attrVal.isEmpty()) { skipped++; continue; }
					 boolean match = (exactValues != null && exactValues.contains(attrVal));
					 if (!match && prefixValues != null) {
						 for (String prefix : prefixValues) {
							 if (attrVal.startsWith(prefix)) { match = true; break; }
						 }
					 }
					 if (!match) { skipped++; continue; }
				 }

				 Geometry geom = (Geometry)feature.getDefaultGeometry();

				 // Apply transformation if available (may be null or identity transform)
				 if (transform != null && !transform.isIdentity()) {
					 geom = JTS.transform(geom, transform);
				 }

				 feature.setDefaultGeometry(geom);
				 features.add(feature);
			 }
		}catch (Exception e) {
			e.printStackTrace();
		}
		dataStore.dispose();

		if (useFilter && skipped > 0) {
			System.out.println("[ShpLoader] Filtered: loaded " + features.size() +
				" features, skipped " + skipped + " non-matching features");
		}

		return features;
	}
}
