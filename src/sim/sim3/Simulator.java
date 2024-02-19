package sim.sim3;

import java.util.Calendar;
import java.util.Date;

import org.apache.commons.lang.time.DateUtils;

import sim.sim3.ctrl.TrafficController;

/**
 * Simulator Body
 * @author SekimotoLab@IIS. UT.
 * @since 2014/07/31
 */
public class Simulator{
	/** Traffic Controller	*/	private TrafficController controller;
	/** End Time			*/	private long endTime;
	/** Number of LoopStep	*/	private int numLoopStep;	
	/** Base Time			*/	private long baseTime = DateUtils.truncate(new Date(),  Calendar.DAY_OF_MONTH).getTime();
	
	/**
	 * Constractor
	 * @param mController	Traffic Controller
	 * @param mEndTime EndTime
	 * @param mNumLoopStep Number of LoopStep
	 */
	public Simulator(TrafficController controller, long endTime, int numLoopStep) {
		this.controller = controller;
		this.endTime = endTime;
		this.numLoopStep = numLoopStep;
	}
			
	/**
	 * On Step forward
	 * @return result
	 */
	public boolean next(){
		int count = 0;
		while(controller.getTime() < endTime && count < numLoopStep){
			System.out.println("**Time : " + new Date(baseTime + controller.getTime()));
			long t0 = System.nanoTime();
			controller.next();
			long t1 = System.nanoTime();
			System.out.println("proc time: " + ((t1-t0)/1000000000.0) + "sec");
			count++;
		}
		return (controller.getTime() < endTime);
	}
}
