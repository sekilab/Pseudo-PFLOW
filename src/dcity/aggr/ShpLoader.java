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
			String code = CRS.lookupIdentifier(sourceCRS, false);
			sourceCRS = CRS.decode(code, true);
			CoordinateReferenceSystem targetCRS = CRS.decode("EPSG:4326", true);
			transform = CRS.findMathTransform(sourceCRS, targetCRS);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		List<SimpleFeature> features = new ArrayList<>();
		try(SimpleFeatureIterator iterator = collection.features();) {
			 while (iterator.hasNext()) {
				 SimpleFeature feature = iterator.next();
				 Geometry geom = JTS.transform((Geometry)feature.getDefaultGeometry(), transform);
				 feature.setDefaultGeometry(geom);
				 features.add(feature);
			 }
		}catch (Exception e) {
			e.printStackTrace();
		}
		dataStore.dispose();

		return features;		
	}
}
