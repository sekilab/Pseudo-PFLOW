package sim.sim3.res;

import sim.sim3.support.INetworkVisitor;

/**
 * Network Lane
 * @author SekimotoLab@IIS. UT.
 * @since 2014/07/31
 */
public class SDirection implements  INetworkAcceptor{
	/** Link			*/	protected SLink parent;
	/** Reverse Flag	*/	protected boolean reverse;
	/** Closed flag		*/	protected boolean closed;
	
	/**
	 * Initialization
	 * @param parent Link
	 * @param mReverse Reverse flag
	 * @param mClosed Closed flag
	 */
	public SDirection(SLink parent, boolean reverse, boolean closed) {
		this.parent = parent;
		this.reverse = reverse;
		this.closed = closed;
	}

	
	
	public void setParent(SLink parent){
		this.parent = parent;
	}
	
	public boolean isReverse() {
		return reverse;
	}
	
	public boolean isClosed() {
		return closed;
	}
	
	public void close() {
		closed = true;
	}
	
	public void open(boolean close) {
		closed = false;
	}

	public SLink getParent() {
		return parent;
	}
	
	public long getDrivingTime(){
		return parent.getDrivingTime();
	}	

	@Override
	public void accept(INetworkVisitor networkVisitor) {
		networkVisitor.visit(this);
	}
}
