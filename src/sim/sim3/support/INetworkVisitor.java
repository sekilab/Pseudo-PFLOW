package sim.sim3.support;

import sim.sim3.res.SDirection;
import sim.sim3.res.SLink;
import sim.sim3.res.SNetwork;
import sim.sim3.res.SNode;
import sim.sim3.res.SSNetwork;

/**
 * Visitor for Network
 * @author SekimotoLab@IIS. UT.
 * @since 2014/07/31
 */
public interface INetworkVisitor {
	public void visit(SNetwork accept);
	public void visit(SSNetwork accept);	
	public void visit(SLink accept);
	public void visit(SDirection accept);
	public void visit(SNode accept);
}
