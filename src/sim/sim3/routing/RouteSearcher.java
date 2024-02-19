package sim.sim3.routing;

import java.util.List;
import jp.ac.ut.csis.pflow.geom2.LonLat;
import jp.ac.ut.csis.pflow.routing4.logic.Dijkstra;
import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Node;
import jp.ac.ut.csis.pflow.routing4.res.Route;
import sim.sim3.res.SSNetwork;

/**
 * Search Route
 * @author SekimotoLab@IIS. UT.
 * @since 2014/07/31
 */
public class RouteSearcher{
	
	private static final double COST_TOLERANCE = 6 * 3600 * 1000;
	
	private static Node searchNode(Dijkstra dijkstra, LonLat lonlat, Network network){
		Node ret = null;
		double[] mindists = {3000, 5000, 10000, 20000, 50000};
		for(double mindist : mindists){
			ret = dijkstra.getNearestNode(network, lonlat.getLon(), lonlat.getLat(), mindist);
			if (ret != null){
				return ret;
			}
		}
		return ret;
	}
	/**
	 * Search Route
	 * @param srcNode　Source node
	 * @param trgNode Target node
	 * @param network　network
	 * @return　List of Nodes
	 */
	public static List<Node> search(int source, int target, LonLat srcLL, LonLat trgLL, SSNetwork network) {
		Node srcNode = network.getNode(source);
		Node trgNode = network.getNode(target);
		
		Dijkstra dijkstra = new Dijkstra();
		
		if (srcNode == null && srcLL != null){
			srcNode = searchNode(dijkstra, srcLL, network);
		}
		if (trgNode == null && trgLL != null){
			trgNode = searchNode(dijkstra, trgLL, network);
		}

		if (srcNode != null && trgNode != null){
			Route route = null;
			route = dijkstra.getRoute(network, srcNode, trgNode);
			if (route == null){
				System.out.println("routing error1");
			}else{
			}
			return (route != null && route.getCost() <= COST_TOLERANCE) ? route.listNodes() : null;
		}else{
			System.out.println("routing error0");
		}
		return null;
	}
}