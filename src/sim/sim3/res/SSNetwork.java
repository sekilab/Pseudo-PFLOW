package sim.sim3.res;

import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Node;
import sim.sim3.support.INetworkVisitor;

/**
 * SubNetwork
 * @author SekimotoLab@IIS. UT.
 * @since 2014/07/31
 */
public class SSNetwork extends Network implements INetworkAcceptor{
	/**Transport Type				*/	private ENetwork type;
	
	/**
	 * Initialization
	 * @param mType Transport Type
	 */
	public SSNetwork(ENetwork type) {
		super();
		this.type = type;
	}
	
	public ENetwork getType(){
		return type;
	}
	
	public SLink getSLink(Node srcNode, Node dstNode) {
		return (SLink)getLink(srcNode, dstNode);
	}
	
	public Node getNode(int nodeId) {
		return getNode(String.valueOf(nodeId));
	}
	
	public boolean hasNode(int nodeId) {
		return hasNode(String.valueOf(nodeId));
	}
	
	/**
	 * Insert link to network
	 * @return result
	 */
	public int addLink2(SLink slink){
		slink.setParent(this);
		this.addLink(slink);
		return 0;
	}

	@Override
	public void accept(INetworkVisitor networkVisitor) {
		networkVisitor.visit(this);
	}
}
