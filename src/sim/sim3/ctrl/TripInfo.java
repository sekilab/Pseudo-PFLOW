package sim.sim3.ctrl;

import sim.sim3.res.Agent;
import sim.sim3.res.ENetwork;

public class TripInfo {
	private Agent mAgent;
	private ENetwork mNetworkType;
	private long mStartTime;
	private int mLinkId;
	private boolean mReverse;
	
	public TripInfo(ENetwork mNetworkType, Agent mAgent, long mStartTime, int mLinkId,  boolean mReverse) {
		super();
		this.mNetworkType = mNetworkType;
		this.mLinkId = mLinkId;
		this.mAgent = mAgent;
		this.mStartTime = mStartTime;
		this.mReverse = mReverse;
	}
	
	public TripInfo(ENetwork mNetworkType, Agent mAgent, int mLinkId, boolean mReverse) {
		this(mNetworkType, mAgent, 0, mLinkId, mReverse);
	}
	
	public TripInfo(Agent mAgent, long mStartTime) {
		this(ENetwork.CENTROID, mAgent, mStartTime, 0, false);
	}

	public int getLinkId(){
		return mLinkId;
	}
	
	public Agent getAgent(){
		return mAgent;
	}
	
	public ENetwork getNetworkType(){
		return mNetworkType;
	}
	
	public long getStartTime(){
		return mStartTime;
	}
	
	public boolean isReverse(){
		return mReverse;
	}
}
