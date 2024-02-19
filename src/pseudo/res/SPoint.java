package pseudo.res;

import java.util.Date;

import jp.ac.ut.csis.pflow.geom2.LonLatTime;

@SuppressWarnings("serial")
public class SPoint extends LonLatTime{
	private ETransport mode;
	private EPurpose purpose;
	private String link;

	public SPoint(double lon, double lat, Date timeStamp, ETransport mode, EPurpose purpose) {
		super(lon, lat, timeStamp);
		this.mode = mode;
		this.link = null;
		this.purpose = purpose;
	}

	public ETransport getTransport() {
		return mode;
	}

	public String getLink() {
		return link;
	}

	public void setLink(String link) {
		this.link = link;
	}	
	
	public EPurpose getPurpose() {
		return this.purpose;
	}
}
