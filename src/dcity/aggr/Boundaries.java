package dcity.aggr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.SpatialIndex;
import org.locationtech.jts.index.strtree.STRtree;
import org.opengis.feature.simple.SimpleFeature;

public class Boundaries {
	private SpatialIndex     geometryIndex;
	private Map<Geometry, String> geometries;

	
	
	public Boundaries() {
		super();
		geometryIndex = new STRtree();
	}

	public int load(String filename) {
		geometries = new HashMap<>();
		try {
			List<SimpleFeature> features = ShpLoader.load(filename);
			for (SimpleFeature feature : features) {
				//String id = (String) feature.getAttribute("KEY_CODE");
				String id = (String) feature.getAttribute("area");
				Geometry geom = (Geometry)feature.getDefaultGeometry();
				geometries.put(geom, id);
				
				PreparedGeometry prepgeom   = PreparedGeometryFactory.prepare(geom);
				Envelope         envelope   = prepgeom.getGeometry().getEnvelopeInternal();
				geometryIndex.insert(envelope,geom);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 0;
	}
	
	@SuppressWarnings("unchecked")
	public List<Geometry> query(double x0,double y0,double x1,double y1) {
		Envelope   search = new Envelope(x0,x1,y0,y1);	// CAUTION: parameter order
		List<Geometry> geoms  = geometryIndex.query(search);
		return geoms;
	}
	
	public static Boundaries create(String filename) {
		Boundaries e = new Boundaries();
		e.load(filename);
		return e;
	}

	public String getGeometry(Geometry geom) {
		return geometries.get(geom);
	}
	
	public List<String> getNames(){
		return new ArrayList<>(geometries.values());
	}

	public int size() {
		return geometries.size();
	}
}

