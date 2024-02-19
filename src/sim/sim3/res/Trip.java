package sim.sim3.res;


/**
 * Trip
 * @author SekimotoLab@IIS. UT.
 * @since 2014/07/31
 */
public class Trip {
	/**	ID							*/	protected long id;
	/**	StaygingTime(msec)			*/	protected long stayTime;
	/**	DepTime(msec)				*/	protected long depTime;
	/**	Departure Node				*/	protected int depId;
	/**	Arrival Node				*/	protected int arrId;
	/**	Transport Type				*/	protected ETransport transport;
	/**	Magnification factor(PCU)	*/	protected double mfactor1;
	/**	Magnification factor(PCU)	*/	protected double mfactor2;
		
	/**
	 * Initialization
	 * @param mId Trip id
	 * @param mTransport Transport Type
	 * @param mStayTime Staying Time
	 * @param mMfactor Magnification Factor(PCU)
	 * @param mDepNode	Departure Node
	 * @param mArrNode	Arrival Node
	 */

	public Trip(long id, ETransport transport, long stayTime,
			double mfactor1, double mfactor2, int depId, int arrId ,long depTime) {
		super();
		this.id = id;
		this.transport = transport;
		this.mfactor1 = mfactor1;
		this.mfactor2 = mfactor2;
		this.stayTime = stayTime;
		this.depId = depId;
		this.arrId = arrId;
		this.depTime = depTime;
	}
	
	public Trip(long id, ETransport transport, long stayTime,
			double mfactor1, double mfactor2, int depId, int arrId) {
		this(id,transport,stayTime,mfactor1,mfactor2,depId,arrId, 0);
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public long getStayTime() {
		return stayTime;
	}

	public void setStayTime(long stayTime) {
		this.stayTime = stayTime;
	}

	public long getDepTime() {
		return depTime;
	}

	public void setDepTime(long depTime) {
		this.depTime = depTime;
	}

	public int getDepId() {
		return depId;
	}

	public void setDepId(int depId) {
		this.depId = depId;
	}

	public int getArrId() {
		return arrId;
	}

	public void setArrId(int arrId) {
		this.arrId = arrId;
	}

	public ETransport getTransport() {
		return transport;
	}

	public void setTransport(ETransport transport) {
		this.transport = transport;
	}

	public double getMfactor1() {
		return mfactor1;
	}

	public void setMfactor1(double mfactor1) {
		this.mfactor1 = mfactor1;
	}

	public double getMfactor2() {
		return mfactor2;
	}

	public void setMfactor2(double mfactor2) {
		this.mfactor2 = mfactor2;
	}

}
