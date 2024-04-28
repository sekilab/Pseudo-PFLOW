package sim.sim4.ctr;

import java.util.Date;

/**
 * Simulator Body
 * @author SekimotoLab@IIS. UT.
 * @since 2014/07/31
 */
public class Simulator {
	/** Current Time		*/	private long curTime;
	/** End Time			*/	private long endTime;
	/** Number of LoopStep	*/	private long stepTime;
								private Controller<?> controller;
	
	/**
	 * Constractor
	 * @param mController	Traffic Controller
	 * @param mEndTime EndTime
	 * @param mNumLoopStep Number of LoopStep
	 */
	public Simulator(Controller<?> controller, long startTime, long endTime, long stepTime) {
		this.curTime = startTime;
		this.endTime = endTime;
		this.stepTime = stepTime;
		this.controller = controller;
	}
			
	/**
	 * On Step forward
	 * @return result
	 */
	public int run(){
		this.controller.initialize();
		while(curTime < endTime){
			System.out.println("**Time : " + new Date(curTime));
			long t0 = System.nanoTime();
			controller.next(curTime);
			long t1 = System.nanoTime();
			System.out.println("proc time: " + ((t1-t0)/1000000000.0) + "sec");
			curTime += stepTime;
		}
		return 0;
	}
}
