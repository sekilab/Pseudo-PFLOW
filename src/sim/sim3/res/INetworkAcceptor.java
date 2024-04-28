package sim.sim3.res;

import sim.sim3.support.INetworkVisitor;

/**
 * Acceptor Interface
 * @author SekimotoLab@IIS. UT.
 * @since 2014/07/31
 */
public interface INetworkAcceptor {

	/**
	 * Acceptor
	 * @param networkVisitor Network visitor
	 */
	public void accept(INetworkVisitor networkVisitor);
}
