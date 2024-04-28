package sim.sim3.res;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sim.sim3.support.INetworkVisitor;

/**
 * Network
 * @author SekimotoLab@IIS. UT.
 * @since 2014/07/31
 */
public class SNetwork implements INetworkAcceptor{
	/** Map of SubNetwork		*/	protected HashMap<ENetwork, SSNetwork> mapNetworks;
	/** NodeMap					*/	protected HashMap<Integer, SNode> nodeMap;
	
	/**
	 * Initialization
	 */
	public SNetwork() {
		super();
		this.mapNetworks = new HashMap<ENetwork, SSNetwork>();
		this.nodeMap = new HashMap<Integer, SNode>();
	}

	/**
	 * Return list of SNodes
	 * @return result
	 */
	public List<SNode> listSNode(){
		return new ArrayList<SNode>(nodeMap.values());
	}
	
	/**
	 * Return list of SubNetwork
	 * @return result 
	 */
	public List<SSNetwork> listSNetwork(){
		return new ArrayList<SSNetwork>(mapNetworks.values());
	}
	
	/**
	 * Return map of subnetworks
	 * @return result 
	 */
	public Map<ENetwork, SSNetwork> mapNetworks(){
		return mapNetworks;
	}
	
	/**
	 * Get SNode from node id
	 * @return result
	 */
	public SNode getSNode(int nodeId){
		return nodeMap.get(nodeId);
	}
	
	/**
	 * Get SNode from node id
	 * @return result
	 */
	public SNode getSNode(String nodeId){
		return getSNode(Integer.valueOf(nodeId));
	}
	
	/**
	 * Add SNode to network
	 */
	public void addNode(SNode node){
		nodeMap.put(Integer.valueOf(node.getNodeID()), node);
	}
	
	/**
	 * Check node existence
	 * @param id Node id
	 * @return result
	 */
	public boolean hasNode(int id){
		return nodeMap.containsKey(id);
	}
	
	/**
	 * Check node existence
	 * @param id Node id
	 * @return result
	 */
	public boolean hasNode(String id){
		return hasNode(Integer.valueOf(id));
	}
	
	/**
	 * Add SubNetwork to network
	 */
	public void addNetwork(SSNetwork network){
		mapNetworks.put(network.getType(), network);
	}
	
	/**
	 * @param transport
	 * @return
	 */
	public SSNetwork getNetwork(ENetwork type){
		return mapNetworks.get(type);
	}
	
	/**
	 * Get SLink from link id
	 * @return result
	 */
	public SLink getSLink(String linkId){
		for (Map.Entry<ENetwork, SSNetwork> entry : mapNetworks.entrySet()){
			SLink link = (SLink)entry.getValue().getLink(linkId);
			if (link != null){
				return link;
			}
		}
		return null;
	}
	
	@Override
	public void accept(INetworkVisitor networkVisitor) {
		networkVisitor.visit(this);
	}
}
