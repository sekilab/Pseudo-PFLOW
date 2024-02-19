package pseudo.res;

import jp.ac.ut.csis.pflow.geom2.ILonLat;
import jp.ac.ut.csis.pflow.geom2.LonLat;

@SuppressWarnings("serial")
public class GLonLat extends LonLat{
	private String gcode;

	public GLonLat(ILonLat ll, String gcode) {
		this(ll.getLon(), ll.getLat(), gcode);
	}

	public GLonLat(double lon, double lat, String gcode) {
		super(lon, lat);
		this.gcode = gcode;
	}

	public String getGcode() {
		return gcode;
	}
}
