package sim.sim3.res;

import jp.ac.ut.csis.pflow.routing4.res.Node;
import sim.sim3.support.INetworkVisitor;

/**
 * Network Node
 * @author SekimotoLab@IIS. UT.
 * @since 2014/07/31
 */
@SuppressWarnings("serial")
public class SNode extends Node implements INetworkAcceptor{
	/**
	 * Initialization
	 * @param id Node id
	 * @param lon Longitude
	 * @param lat Latitude
	 */
	public SNode(String id, double lon, double lat) {
		super(id, lon, lat);
	}
	
	@Override
	public void accept(INetworkVisitor networkVisitor) {
		networkVisitor.visit(this);
	}
}
