/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.SimScenario;
import core.World;
import routing.CNRouter;
import routing.CNRouter_Adapt;
import routing.CNRouter_CUFullConn;
import routing.CNRouter_CUSelf;
import routing.CNRouter_DU_FIX;
import routing.CURouter;
import routing.DURouter;
import routing.MessageRouter;

/**
 * Report for generating different kind of total statistics about message
 * relaying performance. Messages that were created during the warm up period
 * are ignored.
 * <P><strong>Note:</strong> if some statistics could not be created (e.g.
 * overhead ratio if no messages were delivered) "NaN" is reported for
 * double values and zero for integer median(s).
 */
public class MessageStatsReport extends Report implements MessageListener {
	private Map<String, Double> creationTimes;
	private List<Double> latencies;
	private List<Integer> hopCounts;
	private List<Double> msgBufferTime;
	private List<Double> rtt; // round trip times

	private int nrofDropped;
	private int nrofRemoved;
	private int nrofStarted;
	private int nrofAborted;
	private int nrofRelayed;
	private int nrofCreated;
	private int nrofResponseReqCreated;
	private int nrofResponseDelivered;
	private int nrofDelivered;

	/**
	 * Constructor.
	 */
	public MessageStatsReport() {
		init();
	}

	@Override
	protected void init() {
		super.init();
		this.creationTimes = new HashMap<String, Double>();
		this.latencies = new ArrayList<Double>();
		this.msgBufferTime = new ArrayList<Double>();
		this.hopCounts = new ArrayList<Integer>();
		this.rtt = new ArrayList<Double>();

		this.nrofDropped = 0;
		this.nrofRemoved = 0;
		this.nrofStarted = 0;
		this.nrofAborted = 0;
		this.nrofRelayed = 0;
		this.nrofCreated = 0;
		this.nrofResponseReqCreated = 0;
		this.nrofResponseDelivered = 0;
		this.nrofDelivered = 0;
	}


	public void messageDeleted(Message m, DTNHost where, boolean dropped) {
		if (isWarmupID(m.getId())) {
			return;
		}

		if (dropped) {
			this.nrofDropped++;
		}
		else {
			this.nrofRemoved++;
		}

		this.msgBufferTime.add(getSimTime() - m.getReceiveTime());
	}


	public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {
		if (isWarmupID(m.getId())) {
			return;
		}

		this.nrofAborted++;
	}


	public void messageTransferred(Message m, DTNHost from, DTNHost to,
			boolean finalTarget) {
		if (isWarmupID(m.getId())) {
			return;
		}

		this.nrofRelayed++;
		if (finalTarget) {
			this.latencies.add(getSimTime() -
				this.creationTimes.get(m.getId()) );
			this.nrofDelivered++;
			this.hopCounts.add(m.getHops().size() - 1);

			if (m.isResponse()) {
				this.rtt.add(getSimTime() -	m.getRequest().getCreationTime());
				this.nrofResponseDelivered++;
			}
		}
	}


	public void newMessage(Message m) {
		if (isWarmup()) {
			addWarmupID(m.getId());
			return;
		}

		this.creationTimes.put(m.getId(), getSimTime());
		this.nrofCreated++;
		if (m.getResponseSize() > 0) {
			this.nrofResponseReqCreated++;
		}
	}


	public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
		if (isWarmupID(m.getId())) {
			return;
		}

		this.nrofStarted++;
	}


//	@Override
//	public void done() {
//		write("Message stats for scenario " + getScenarioName() +
//				"\nsim_time: " + format(getSimTime()));
//		double deliveryProb = 0; // delivery probability
//		double responseProb = 0; // request-response success probability
//		double overHead = Double.NaN;	// overhead ratio
//
//		if (this.nrofCreated > 0) {
//			deliveryProb = (1.0 * this.nrofDelivered) / this.nrofCreated;
//		}
//		if (this.nrofDelivered > 0) {
//			overHead = (1.0 * (this.nrofRelayed - this.nrofDelivered)) /
//				this.nrofDelivered;
//		}
//		if (this.nrofResponseReqCreated > 0) {
//			responseProb = (1.0* this.nrofResponseDelivered) /
//				this.nrofResponseReqCreated;
//		}
//
//		String statsText = "created: " + this.nrofCreated +
//			"\nstarted: " + this.nrofStarted +
//			"\nrelayed: " + this.nrofRelayed +
//			"\naborted: " + this.nrofAborted +
//			"\ndropped: " + this.nrofDropped +
//			"\nremoved: " + this.nrofRemoved +
//			"\ndelivered: " + this.nrofDelivered +
//			"\ndelivery_prob: " + format(deliveryProb) +
//			"\nresponse_prob: " + format(responseProb) +
//			"\noverhead_ratio: " + format(overHead) +
//			"\nlatency_avg: " + getAverage(this.latencies) +
//			"\nlatency_med: " + getMedian(this.latencies) +
//			"\nhopcount_avg: " + getIntAverage(this.hopCounts) +
//			"\nhopcount_med: " + getIntMedian(this.hopCounts) +
//			"\nbuffertime_avg: " + getAverage(this.msgBufferTime) +
//			"\nbuffertime_med: " + getMedian(this.msgBufferTime) +
//			"\nrtt_avg: " + getAverage(this.rtt) +
//			"\nrtt_med: " + getMedian(this.rtt)
//			;
//
//		write(statsText);
//		super.done();
//	}
	
	//存储输入参数
	private int log_cuNum = 0;
	private int log_duNum = 0;
	private double log_epsilon = 0;
	private double log_Omega = 0;
	private double log_rho = 0;
	//最大计算能力
	private HashMap<CURouter,Double> log_MaxCap  = new HashMap<>();
	//目标函数值
	private ArrayList<Double> log_objValue = new ArrayList<>();
	//每个CU对应DU数据的计算量
	private HashMap<CURouter,HashMap<DURouter, ArrayList<String>>> log_CUX = new HashMap<>();
	private HashMap<CURouter,HashMap<DURouter, ArrayList<String>>> log_CUXSelf = new HashMap<>();
	//自己，来自别人cu的du的多少
	private HashMap<CURouter,HashMap<CURouter,HashMap<DURouter, ArrayList<String>>>> log_CUY = new HashMap<>();
	//总的，这个CU算了多少DU的东西
	private HashMap<CURouter,HashMap<DURouter, Double>> log_CUXY = new HashMap<>();
	private HashMap<DURouter,Double> log_delta = new HashMap<>();
	//队列
	private HashMap<DURouter,ArrayList<Double>> log_muQueue = new HashMap<>();
	private HashMap<CURouter,HashMap<DURouter,ArrayList<Double>>> log_etaQueue = new HashMap<>();
	private HashMap<CURouter,HashMap<DURouter,ArrayList<Double>>> log_lambdaQueue = new HashMap<>();
	private HashMap<CURouter,HashMap<DURouter,ArrayList<Double>>> log_piQueue = new HashMap<>();
	
	private HashMap<DURouter,ArrayList<Double>> log_gammamuQueue = new HashMap<>();
	private HashMap<CURouter,HashMap<DURouter,ArrayList<Double>>> log_gammaetaQueue = new HashMap<>();
	private HashMap<CURouter,HashMap<DURouter,ArrayList<Double>>> log_gammalambdaQueue = new HashMap<>();
	private HashMap<CURouter,HashMap<DURouter,ArrayList<Double>>> log_gammapiQueue = new HashMap<>();
	
	private HashMap<DURouter,ArrayList<Double>> log_hatmuQueue = new HashMap<>();
	private HashMap<CURouter,HashMap<DURouter,ArrayList<Double>>> log_hatetaQueue = new HashMap<>();
	private HashMap<CURouter,HashMap<DURouter,ArrayList<Double>>> log_hatlambdaQueue = new HashMap<>();
	private HashMap<CURouter,HashMap<DURouter,ArrayList<Double>>> log_hatpiQueue = new HashMap<>();
	
	//每个DU数据到来量期望
	private HashMap<DURouter, Double> log_expectedDataRate = new HashMap<>();
	//每个DU的瞬时速度
	private HashMap<CURouter, HashMap<DURouter, ArrayList<Double>>> log_DUCurRate = new HashMap<>();
	private HashMap<CURouter, HashMap<CURouter, ArrayList<Double>>> log_CUCurRate = new HashMap<>();
	//每个CU的瞬时Cap
	private HashMap<CURouter, ArrayList<Double>> log_CUCurCalCap = new HashMap<>();
	//每个DU上传多少
	private HashMap<DURouter,HashMap<CURouter,ArrayList<String>>> log_du2cuData = new HashMap<>();
	//private HashMap<DURouter, Double> log_delta = new HashMap<>();
	//记录价格
	private HashMap<CURouter, HashMap<DURouter, Double>> log_du2cuTransCost = new HashMap<>();
	private HashMap<CURouter, HashMap<CURouter, Double>> log_cu2cuTransCost = new HashMap<>();
	private HashMap<CURouter, Double> log_cuCalCost = new HashMap<>();
	
	@Override
	public void done() {
//		World ww = SimScenario.getInstance().getWorld();
//		ArrayList<DTNHost> host_list = (ArrayList<DTNHost>) ww.getHosts();
//		
		String statsText = "";
		statsText += "cuNum: ,"+Integer.toString(this.log_cuNum);
		statsText += "\nduNum: ,"+Integer.toString(this.log_duNum);
		statsText += "\nepsilon: ,"+format(log_epsilon);
		statsText += "\nOmega: ,"+format(log_Omega);
		statsText += "\nrho: ,"+format(log_rho);
		//MaxCalCapcity
		statsText += "\nMaxCapacity: ,\n";
		for(CURouter cuR : this.log_MaxCap.keySet()) {
			int cuID = cuR.getHost().getAddress();
			statsText += "CU:  " + Integer.toString(cuID)+"  ," + format(this.log_MaxCap.get(cuR))+"  ,";
		}
		//obj
		statsText += "\nObj: ,";
		for(Double objv:this.log_objValue)
			statsText += format(objv) + ", ";
		//cal_y
		statsText += "\nCalFromOthers CU-DU-Size: ,";
		for(CURouter cuR : this.log_CUY.keySet()) {
			int cuID = cuR.getHost().getAddress();
			statsText += "\nCU:  " + Integer.toString(cuID)+"  ,";
			for(CURouter othercuR:this.log_CUY.get(cuR).keySet()) {
				cuID = othercuR.getHost().getAddress();
				statsText += "\nGetFromotherCU:  " + Integer.toString(cuID)+"  ,";
				for (DURouter duR: this.log_CUY.get(cuR).get(othercuR).keySet()) {
					int duID = duR.getHost().getAddress();
					statsText += "\nDU:  " + Integer.toString(duID)+"  ,";
					for(String value:this.log_CUY.get(cuR).get(othercuR).get(duR))
					{
						String[] ss = value.split(" ");
						statsText += ss[0] + "t, "+ format(Double.valueOf(ss[1]))+", ";
					}
				}
			}		
		}
		//cal_x
		statsText += "\nCalBySelfAndOther CU-DU-Size: ,";
		for(CURouter cuR : this.log_CUX.keySet()) {
			int cuID = cuR.getHost().getAddress();
			statsText += "\nCU:  " + Integer.toString(cuID)+"  ,";
			for (DURouter duR: this.log_CUX.get(cuR).keySet()) {
				int duID = duR.getHost().getAddress();
				statsText += "\nDU:  " + Integer.toString(duID)+"  ,";
				for(String value:this.log_CUX.get(cuR).get(duR))
				{
					String[] ss = value.split(" ");
					statsText += ss[0] + "t, "+ format(Double.valueOf(ss[1]))+", ";
				}
			}
		}
		//cal_xSelf
		statsText += "\nCalBySelf CU-DU-Size: ,";
		for(CURouter cuR : this.log_CUXSelf.keySet()) {
			int cuID = cuR.getHost().getAddress();
			statsText += "\nCU:  " + Integer.toString(cuID)+"  ,";
			for (DURouter duR: this.log_CUXSelf.get(cuR).keySet()) {
				int duID = duR.getHost().getAddress();
				statsText += "\nDU:  " + Integer.toString(duID)+"  ,";
				for(String value:this.log_CUXSelf.get(cuR).get(duR))
				{
					String[] ss = value.split(" ");
					statsText += ss[0] + "t, "+ format(Double.valueOf(ss[1]))+", ";
				}
			}
		}
		//RealDelta
		statsText += "\nCU-DURealDelta: ,";
		for(CURouter cuR : this.log_CUXY.keySet()) {
			int cuID = cuR.getHost().getAddress();
			statsText += "\nCU:  " + Integer.toString(cuID)+"  ,\n";
			double tmp_sum = 0;
			for (DURouter duR: this.log_CUXY.get(cuR).keySet())
				tmp_sum += this.log_CUXY.get(cuR).get(duR);
			for (DURouter duR: this.log_CUXY.get(cuR).keySet()) {
				int duID = duR.getHost().getAddress();
				statsText += "DU:  " + Integer.toString(duID)+" ,"+format(this.log_CUXY.get(cuR).get(duR)/tmp_sum)+", ";
			}
		}
		//FixDelta
		statsText += "\nDUFixDelta: ,";
		for(DURouter duR: this.log_delta.keySet()) {
			int duID = duR.getHost().getAddress();
			statsText += "DU:  " + Integer.toString(duID)+" ,"+format(this.log_delta.get(duR))+", ";
		}
		//mu-queue
		statsText += "\nmu_Queue: ,";
		for (DURouter duR: this.log_muQueue.keySet()) {
			int duID = duR.getHost().getAddress();
			statsText += "\nDU: " + Integer.toString(duID)+"  ,";
			for(Double value:this.log_muQueue.get(duR))
				statsText += format(value)+", ";
		}
		//eta-queue
		statsText += "\neta_Queue: ,";
		for(CURouter cuR : this.log_etaQueue.keySet()) {
			int cuID = cuR.getHost().getAddress();
			statsText += "\nCU: " + Integer.toString(cuID)+"  ,";
			for (DURouter duR: this.log_etaQueue.get(cuR).keySet()) {
				int duID = duR.getHost().getAddress();
				statsText += "\nDU: " + Integer.toString(duID)+"  ,";
				for(Double value:this.log_etaQueue.get(cuR).get(duR))
					statsText += format(value)+", ";
			}
		}
		//lambda-queue
		statsText += "\nlambda_Queue: ,";
		for(CURouter cuR : this.log_lambdaQueue.keySet()) {
			int cuID = cuR.getHost().getAddress();
			statsText += "\nCU: " + Integer.toString(cuID)+"  ,";
			for (DURouter duR: this.log_lambdaQueue.get(cuR).keySet()) {
				int duID = duR.getHost().getAddress();
				statsText += "\nDU: " + Integer.toString(duID)+"  ,";
				for(Double value:this.log_lambdaQueue.get(cuR).get(duR))
					statsText += format(value)+", ";
			}
		}
		//pi-queue
		statsText += "\npi_Queue: ,";
		for(CURouter cuR : this.log_piQueue.keySet()) {
			int cuID = cuR.getHost().getAddress();
			statsText += "\nCU: " + Integer.toString(cuID)+"  ,";
			for (DURouter duR: this.log_piQueue.get(cuR).keySet()) {
				int duID = duR.getHost().getAddress();
				statsText += "\nDU: " + Integer.toString(duID)+"  ,";
				for(Double value:this.log_piQueue.get(cuR).get(duR))
					statsText += format(value)+", ";
			}
		}
		//expectedDataRate
		statsText += "\nexpectedDataRate: ,\n";
		for(DURouter duR:this.log_expectedDataRate.keySet()) {
			int duID = duR.getHost().getAddress();
			statsText += "DU: " + Integer.toString(duID)+"  ,"+Double.toString(this.log_expectedDataRate.get(duR))+", ";
		}
		//DUCurRate
		statsText += "\nCU-DUCurRate: ,";
		for(CURouter cuR : this.log_DUCurRate.keySet()) {
			int cuID = cuR.getHost().getAddress();
			statsText += "\nCU: " + Integer.toString(cuID)+"  ,";
			for (DURouter duR: this.log_DUCurRate.get(cuR).keySet()) {
				int duID = duR.getHost().getAddress();
				statsText += "\nDU: " + Integer.toString(duID)+"  ,";
				for(Double value:this.log_DUCurRate.get(cuR).get(duR))
					statsText += format(value)+", ";
			}
		}
		//DUCurRate
		statsText += "\nCU-CUCurRate: ,";
		for(CURouter cuR : this.log_CUCurRate.keySet()) {
			int cuID = cuR.getHost().getAddress();
			statsText += "\nCU: " + Integer.toString(cuID)+"  ,";
			for (CURouter cuR1: this.log_CUCurRate.get(cuR).keySet()) {
				int cuID1 = cuR.getHost().getAddress();
				statsText += "\notherCU: " + Integer.toString(cuID1)+"  ,";
				for(Double value:this.log_CUCurRate.get(cuR).get(cuR1))
					statsText += format(value)+", ";
			}
		}
		//CUCurCapcity
		statsText += "\nCUCurCapcity: ,";
		for(CURouter cuR:this.log_CUCurCalCap.keySet()) {
			int cuID = cuR.getHost().getAddress();
			statsText += "\nCU: " + Integer.toString(cuID)+"  ," ;
			for(Double dd:this.log_CUCurCalCap.get(cuR)) {
				statsText += format(dd)+", ";
			}
		}
		//DU2CUSize
		statsText += "\nDU2CUSize: ,";
		for(DURouter duR:this.log_du2cuData.keySet()) {
			int duID = duR.getHost().getAddress();
			statsText += "\nDU: " + Integer.toString(duID)+"  ,";
			for(CURouter cuR: this.log_du2cuData.get(duR).keySet()) {
				int cuID = cuR.getHost().getAddress();
				statsText += "\nCU: " + Integer.toString(cuID)+"  ,";
				for(String size:this.log_du2cuData.get(duR).get(cuR))
				{
						String[] ss = size.split(" ");
						statsText += ss[0] + "t, "+ format(Double.valueOf(ss[1]))+", ";
				}						
		 }
		}
		//CU_Cal_Cost
		statsText += "\nCU_Cal_Cost: ,\n";
		for(CURouter cuR:this.log_cuCalCost.keySet()) {
			int cuID = cuR.getHost().getAddress();
			statsText += "CU: " + Integer.toString(cuID)+"  ,"+Double.toString(this.log_cuCalCost.get(cuR))+", ";
		}
		//DU2CU_Cost
		statsText += "\nDU2CU_Cost: ,";
		for(CURouter cuR:this.log_du2cuTransCost.keySet()) {
			int cuID = cuR.getHost().getAddress();
			statsText += "\nCU: " + Integer.toString(cuID)+"  ,\n";
			for(DURouter duR:this.log_du2cuTransCost.get(cuR).keySet()) {
				int duID = duR.getHost().getAddress();
				statsText += "DU: " + Integer.toString(duID)+"  ,"+Double.toString(this.log_du2cuTransCost.get(cuR).get(duR))+", ";			
			}			
		}
		//DU2CU_Cost
		statsText += "\nCU2CU_Cost: ,";
		for(CURouter cuR:this.log_cu2cuTransCost.keySet()) {
			int cuID = cuR.getHost().getAddress();
			statsText += "\nCU: " + Integer.toString(cuID)+"  ,\n";
			for(CURouter cuR1:this.log_cu2cuTransCost.get(cuR).keySet()) {
				int cuID1 = cuR1.getHost().getAddress();
				statsText += "otherCU: " + Integer.toString(cuID1)+"  ,"+Double.toString(this.log_cu2cuTransCost.get(cuR).get(cuR1))+", ";			
			}			
		}
		//---------------------gamma
		//mu-queue
			statsText += "\n\nGamma_mu_Queue: ,";
			for (DURouter duR: this.log_gammamuQueue.keySet()) {
				int duID = duR.getHost().getAddress();
				statsText += "\nDU: " + Integer.toString(duID)+"  ,";
				for(Double value:this.log_gammamuQueue.get(duR))
					statsText += format(value)+", ";
			}
			//eta-queue
			statsText += "\nGamma_eta_Queue: ,";
			for(CURouter cuR : this.log_gammaetaQueue.keySet()) {
				int cuID = cuR.getHost().getAddress();
				statsText += "\nCU: " + Integer.toString(cuID)+"  ,";
				for (DURouter duR: this.log_gammaetaQueue.get(cuR).keySet()) {
					int duID = duR.getHost().getAddress();
					statsText += "\nDU: " + Integer.toString(duID)+"  ,";
					for(Double value:this.log_gammaetaQueue.get(cuR).get(duR))
						statsText += format(value)+", ";
				}
			}
			//lambda-queue
			statsText += "\nGamma_lambda_Queue: ,";
			for(CURouter cuR : this.log_gammalambdaQueue.keySet()) {
				int cuID = cuR.getHost().getAddress();
				statsText += "\nCU: " + Integer.toString(cuID)+"  ,";
				for (DURouter duR: this.log_gammalambdaQueue.get(cuR).keySet()) {
					int duID = duR.getHost().getAddress();
					statsText += "\nDU: " + Integer.toString(duID)+"  ,";
					for(Double value:this.log_gammalambdaQueue.get(cuR).get(duR))
						statsText += format(value)+", ";
				}
			}
			//pi-queue
			statsText += "\nGamma_pi_Queue: ,";
			for(CURouter cuR : this.log_gammapiQueue.keySet()) {
				int cuID = cuR.getHost().getAddress();
				statsText += "\nCU: " + Integer.toString(cuID)+"  ,";
				for (DURouter duR: this.log_gammapiQueue.get(cuR).keySet()) {
					int duID = duR.getHost().getAddress();
					statsText += "\nDU: " + Integer.toString(duID)+"  ,";
					for(Double value:this.log_gammapiQueue.get(cuR).get(duR))
						statsText += format(value)+", ";
				}
			}
			//---------------------hat
			//mu-queue
			statsText += "\nHat_mu_Queue: ,";
			for (DURouter duR: this.log_hatmuQueue.keySet()) {
				int duID = duR.getHost().getAddress();
				statsText += "\nDU: " + Integer.toString(duID)+"  ,";
				for(Double value:this.log_hatmuQueue.get(duR))
					statsText += format(value)+", ";
			}
			//eta-queue
			statsText += "\nHat_eta_Queue: ,";
			for(CURouter cuR : this.log_hatetaQueue.keySet()) {
				int cuID = cuR.getHost().getAddress();
				statsText += "\nCU: " + Integer.toString(cuID)+"  ,";
				for (DURouter duR: this.log_hatetaQueue.get(cuR).keySet()) {
					int duID = duR.getHost().getAddress();
					statsText += "\nDU: " + Integer.toString(duID)+"  ,";
					for(Double value:this.log_hatetaQueue.get(cuR).get(duR))
						statsText += format(value)+", ";
				}
			}
			//lambda-queue
			statsText += "\nHat_lambda_Queue: ,";
			for(CURouter cuR : this.log_hatlambdaQueue.keySet()) {
				int cuID = cuR.getHost().getAddress();
				statsText += "\nCU: " + Integer.toString(cuID)+"  ,";
				for (DURouter duR: this.log_hatlambdaQueue.get(cuR).keySet()) {
					int duID = duR.getHost().getAddress();
					statsText += "\nDU: " + Integer.toString(duID)+"  ,";
					for(Double value:this.log_hatlambdaQueue.get(cuR).get(duR))
						statsText += format(value)+", ";
				}
			}
			//pi-queue
			statsText += "\nHat_pi_Queue: ,";
			for(CURouter cuR : this.log_hatpiQueue.keySet()) {
				int cuID = cuR.getHost().getAddress();
				statsText += "\nCU: " + Integer.toString(cuID)+"  ,";
				for (DURouter duR: this.log_hatpiQueue.get(cuR).keySet()) {
					int duID = duR.getHost().getAddress();
					statsText += "\nDU: " + Integer.toString(duID)+"  ,";
					for(Double value:this.log_hatpiQueue.get(cuR).get(duR))
						statsText += format(value)+", ";
				}
			}
		write(statsText);
		super.done();
	}

	public void update() {
		World ww = SimScenario.getInstance().getWorld();	
		ArrayList<DTNHost> host_list = (ArrayList<DTNHost>) ww.getHosts();
		for(DTNHost host : host_list) {
			if(host.toString().contains("D")) {
				//记录mu队列
				DURouter du_router = (DURouter) host.getRouter();
				if(this.log_muQueue.containsKey(du_router)) 
					this.log_muQueue.get(du_router).add(du_router.mu_QueueLength);	
				else {
					ArrayList<Double> tmp = new ArrayList<>();
					this.log_muQueue.put(du_router,tmp);
					this.log_muQueue.get(du_router).add(du_router.mu_QueueLength);					
				}
				if(this.log_hatmuQueue.containsKey(du_router)) 
					this.log_hatmuQueue.get(du_router).add(du_router.hat_mu_QueueLength);
				else {
					ArrayList<Double> tmp1 = new ArrayList<>();
					this.log_hatmuQueue.put(du_router,tmp1);
					this.log_hatmuQueue.get(du_router).add(du_router.hat_mu_QueueLength);
				}
				if(this.log_gammamuQueue.containsKey(du_router)) 
					this.log_gammamuQueue.get(du_router).add(du_router.gamma_mu_QueueLength);
				else {
					ArrayList<Double> tmp2 = new ArrayList<>();
					this.log_gammamuQueue.put(du_router,tmp2);
					this.log_gammamuQueue.get(du_router).add(du_router.gamma_mu_QueueLength);
				}
				//expectedDatarate
				if(!this.log_expectedDataRate.containsKey(du_router)) {
					this.log_expectedDataRate.put(du_router, du_router.expectedDataArrival);
				}
			}else if(host.toString().contains("C")) {
				CURouter cu_router = (CURouter) host.getRouter();
				//du Delta
				if(this.log_delta.size()==0) {
					for(DTNHost duH:cu_router.delta.keySet()) {
						this.log_delta.put((DURouter) duH.getRouter(), cu_router.delta.get(duH));
					}
				}
				//MaxCapcity
				if(!this.log_MaxCap.containsKey(cu_router))
					this.log_MaxCap.put(cu_router, cu_router.maxCapacity);
				//DUCurRate
				if(this.log_DUCurRate.containsKey(cu_router)) {
					for(DTNHost duHost:cu_router.DU_Rates.keySet()) //du				
						this.log_DUCurRate.get(cu_router).get(duHost.getRouter()).add(cu_router.DU_Rates.get(duHost));				
				}else {
					HashMap<DURouter,ArrayList<Double>> tmp_duEta = new HashMap<>();
					this.log_DUCurRate.put(cu_router, tmp_duEta);
					for(DTNHost duHost:cu_router.DU_Rates.keySet()) {//du					
						ArrayList<Double> tmp = new ArrayList<>();
						this.log_DUCurRate.get(cu_router).put((DURouter) duHost.getRouter(),tmp);
						this.log_DUCurRate.get(cu_router).get(duHost.getRouter()).add(cu_router.DU_Rates.get(duHost));						
					}
				}
				//CUCurRate
				if(this.log_CUCurRate.containsKey(cu_router)) {
					for(DTNHost duHost:cu_router.CU_Rates.keySet()) //du				
						this.log_CUCurRate.get(cu_router).get(duHost.getRouter()).add(cu_router.CU_Rates.get(duHost));				
				}else {
					HashMap<CURouter,ArrayList<Double>> tmp_duEta = new HashMap<>();
					this.log_CUCurRate.put(cu_router, tmp_duEta);
					for(DTNHost duHost:cu_router.CU_Rates.keySet()) {//du					
						ArrayList<Double> tmp = new ArrayList<>();
						this.log_CUCurRate.get(cu_router).put((CURouter) duHost.getRouter(),tmp);
						this.log_CUCurRate.get(cu_router).get(duHost.getRouter()).add(cu_router.CU_Rates.get(duHost));						
					}
				}
				//CUCurCapcity
				if(this.log_CUCurCalCap.containsKey(cu_router)) {
					this.log_CUCurCalCap.get(cu_router).add(cu_router.curCapacity);
				}else {
					ArrayList<Double> tmp = new ArrayList<>();
					this.log_CUCurCalCap.put(cu_router, tmp);
					this.log_CUCurCalCap.get(cu_router).add(cu_router.curCapacity);
				}
				//epsilon, Omega
				this.log_epsilon = cu_router.epsilon;
				this.log_Omega  = cu_router.Omega;
				//eta
				if(this.log_etaQueue.containsKey(cu_router)) {
					for(DTNHost duHost:cu_router.eta_QueueLength.keySet()) //du				
						this.log_etaQueue.get(cu_router).get(duHost.getRouter()).add(cu_router.eta_QueueLength.get(duHost));				
				}else {
					HashMap<DURouter,ArrayList<Double>> tmp_duEta = new HashMap<>();
					this.log_etaQueue.put(cu_router, tmp_duEta);
					for(DTNHost duHost:cu_router.eta_QueueLength.keySet()) {//du					
						ArrayList<Double> tmp = new ArrayList<>();
						this.log_etaQueue.get(cu_router).put((DURouter) duHost.getRouter(),tmp);
						this.log_etaQueue.get(cu_router).get(duHost.getRouter()).add(cu_router.eta_QueueLength.get(duHost));						
					}
				}
				//gamma eta
				if(this.log_gammaetaQueue.containsKey(cu_router)) {
					for(DTNHost duHost:cu_router.gamma_eta_QueueLength.keySet()) //du				
						this.log_gammaetaQueue.get(cu_router).get(duHost.getRouter()).add(cu_router.gamma_eta_QueueLength.get(duHost));				
				}else {
					HashMap<DURouter,ArrayList<Double>> tmp_duEta = new HashMap<>();
					this.log_gammaetaQueue.put(cu_router, tmp_duEta);
					for(DTNHost duHost:cu_router.gamma_eta_QueueLength.keySet()) {//du					
						ArrayList<Double> tmp = new ArrayList<>();
						this.log_gammaetaQueue.get(cu_router).put((DURouter) duHost.getRouter(),tmp);
						this.log_gammaetaQueue.get(cu_router).get(duHost.getRouter()).add(cu_router.gamma_eta_QueueLength.get(duHost));						
					}
				}
				// hat eta
				if(this.log_hatetaQueue.containsKey(cu_router)) {
					for(DTNHost duHost:cu_router.hat_eta_QueueLength.keySet()) //du				
						this.log_hatetaQueue.get(cu_router).get(duHost.getRouter()).add(cu_router.hat_eta_QueueLength.get(duHost));				
				}else {
					HashMap<DURouter,ArrayList<Double>> tmp_duEta = new HashMap<>();
					this.log_hatetaQueue.put(cu_router, tmp_duEta);
					for(DTNHost duHost:cu_router.hat_eta_QueueLength.keySet()) {//du					
						ArrayList<Double> tmp = new ArrayList<>();
						this.log_hatetaQueue.get(cu_router).put((DURouter) duHost.getRouter(),tmp);
						this.log_hatetaQueue.get(cu_router).get(duHost.getRouter()).add(cu_router.hat_eta_QueueLength.get(duHost));						
					}
				}
				//lambda
				if(this.log_lambdaQueue.containsKey(cu_router)) {
					for(DTNHost duHost:cu_router.lambda_QueueLength.keySet())//du				
						this.log_lambdaQueue.get(cu_router).get(duHost.getRouter()).add(cu_router.lambda_QueueLength.get(duHost));									
				}else {
					HashMap<DURouter,ArrayList<Double>> tmp_duEta = new HashMap<>();
					this.log_lambdaQueue.put(cu_router, tmp_duEta);
					for(DTNHost duHost:cu_router.lambda_QueueLength.keySet()) {//du					
						ArrayList<Double> tmp = new ArrayList<>();
						this.log_lambdaQueue.get(cu_router).put((DURouter) duHost.getRouter(),tmp);
						this.log_lambdaQueue.get(cu_router).get(duHost.getRouter()).add(cu_router.lambda_QueueLength.get(duHost));						
					}				
				}
				//gamma lambda
				if(this.log_gammalambdaQueue.containsKey(cu_router)) {
					for(DTNHost duHost:cu_router.gamma_lambda_QueueLength.keySet())//du				
						this.log_gammalambdaQueue.get(cu_router).get(duHost.getRouter()).add(cu_router.gamma_lambda_QueueLength.get(duHost));									
				}else {
					HashMap<DURouter,ArrayList<Double>> tmp_duEta = new HashMap<>();
					this.log_gammalambdaQueue.put(cu_router, tmp_duEta);
					for(DTNHost duHost:cu_router.gamma_lambda_QueueLength.keySet()) {//du					
						ArrayList<Double> tmp = new ArrayList<>();
						this.log_gammalambdaQueue.get(cu_router).put((DURouter) duHost.getRouter(),tmp);
						this.log_gammalambdaQueue.get(cu_router).get(duHost.getRouter()).add(cu_router.gamma_lambda_QueueLength.get(duHost));						
					}				
				}
				//hat lambda
				if(this.log_hatlambdaQueue.containsKey(cu_router)) {
					for(DTNHost duHost:cu_router.hat_lambda_QueueLength.keySet())//du				
						this.log_hatlambdaQueue.get(cu_router).get(duHost.getRouter()).add(cu_router.hat_lambda_QueueLength.get(duHost));									
				}else {
					HashMap<DURouter,ArrayList<Double>> tmp_duEta = new HashMap<>();
					this.log_hatlambdaQueue.put(cu_router, tmp_duEta);
					for(DTNHost duHost:cu_router.hat_lambda_QueueLength.keySet()) {//du					
						ArrayList<Double> tmp = new ArrayList<>();
						this.log_hatlambdaQueue.get(cu_router).put((DURouter) duHost.getRouter(),tmp);
						this.log_hatlambdaQueue.get(cu_router).get(duHost.getRouter()).add(cu_router.hat_lambda_QueueLength.get(duHost));						
					}				
				}
				//pi
				if(this.log_piQueue.containsKey(cu_router)) {
					for(DTNHost duHost:cu_router.pi_QueueLength.keySet()) //du				
						this.log_piQueue.get(cu_router).get(duHost.getRouter()).add(cu_router.pi_QueueLength.get(duHost));												
				}else {
					HashMap<DURouter,ArrayList<Double>> tmp_duEta = new HashMap<>();
					this.log_piQueue.put(cu_router, tmp_duEta);
					for(DTNHost duHost:cu_router.pi_QueueLength.keySet()) {//du					
						ArrayList<Double> tmp = new ArrayList<>();
						this.log_piQueue.get(cu_router).put((DURouter) duHost.getRouter(),tmp);
						this.log_piQueue.get(cu_router).get(duHost.getRouter()).add(cu_router.pi_QueueLength.get(duHost));						
					}
				}
				//gamma pi
				if(this.log_gammapiQueue.containsKey(cu_router)) {
					for(DTNHost duHost:cu_router.gamma_pi_QueueLength.keySet()) //du				
						this.log_gammapiQueue.get(cu_router).get(duHost.getRouter()).add(cu_router.gamma_pi_QueueLength.get(duHost));												
				}else {
					HashMap<DURouter,ArrayList<Double>> tmp_duEta = new HashMap<>();
					this.log_gammapiQueue.put(cu_router, tmp_duEta);
					for(DTNHost duHost:cu_router.gamma_pi_QueueLength.keySet()) {//du					
						ArrayList<Double> tmp = new ArrayList<>();
						this.log_gammapiQueue.get(cu_router).put((DURouter) duHost.getRouter(),tmp);
						this.log_gammapiQueue.get(cu_router).get(duHost.getRouter()).add(cu_router.gamma_pi_QueueLength.get(duHost));						
					}
				}
				//hat pi
				if(this.log_hatpiQueue.containsKey(cu_router)) {
					for(DTNHost duHost:cu_router.hat_pi_QueueLength.keySet()) //du				
						this.log_hatpiQueue.get(cu_router).get(duHost.getRouter()).add(cu_router.hat_pi_QueueLength.get(duHost));												
				}else {
					HashMap<DURouter,ArrayList<Double>> tmp_duEta = new HashMap<>();
					this.log_hatpiQueue.put(cu_router, tmp_duEta);
					for(DTNHost duHost:cu_router.hat_pi_QueueLength.keySet()) {//du					
						ArrayList<Double> tmp = new ArrayList<>();
						this.log_hatpiQueue.get(cu_router).put((DURouter) duHost.getRouter(),tmp);
						this.log_hatpiQueue.get(cu_router).get(duHost.getRouter()).add(cu_router.hat_pi_QueueLength.get(duHost));						
					}
				}
				//cu_cal_cost
				if(!this.log_cuCalCost.containsKey(cu_router)) {
					//HashMap<DURouter,Double> tmp_cost = new HashMap<>();
					this.log_cuCalCost.put(cu_router, cu_router.CU_Cal_Cost);
				}
				//du2cuTransCost
				if(!this.log_du2cuTransCost.containsKey(cu_router)) {
					HashMap<DURouter,Double> tmp_cost = new HashMap<>();
					this.log_du2cuTransCost.put(cu_router, tmp_cost);
					for(DTNHost duHost:cu_router.DU_CU_Trans_Cost.keySet()) 
						this.log_du2cuTransCost.get(cu_router).put((DURouter) duHost.getRouter(), cu_router.DU_CU_Trans_Cost.get(duHost));						
				}
				//cu2cuTransCost
				if(!this.log_cu2cuTransCost.containsKey(cu_router)) {
					HashMap<CURouter,Double> tmp_cost = new HashMap<>();
					this.log_cu2cuTransCost.put(cu_router, tmp_cost);
					for(DTNHost cuHost:cu_router.CU_CU_Trans_Cost.keySet()) 
						this.log_cu2cuTransCost.get(cu_router).put((CURouter) cuHost.getRouter(), cu_router.CU_CU_Trans_Cost.get(cuHost));						
				}
				
				//if(this.log_delta.containsKey(cu_router.delta.))
			}else {//CN
				//CNRouter cn_router = (CNRouter) host.getRouter();
				CNRouter_Adapt  cn_router= (CNRouter_Adapt)host.getRouter();
				this.log_objValue.add(cn_router.obj_function);
				this.log_rho = cn_router.rho;
				this.log_cuNum = cn_router.cu_num;
				this.log_duNum = cn_router.du_num;				
			}
		}		
	}
	
	public void update_du2cuData(CURouter cu_router, DURouter du_router, double size) {
		World ww = SimScenario.getInstance().getWorld();
		if(this.log_du2cuData.containsKey(du_router)) {
			if(this.log_du2cuData.get(du_router).containsKey(cu_router)) {
				this.log_du2cuData.get(du_router).get(cu_router).add(ww.runUntil + " " + size);
			}else {
				ArrayList<String> tmp = new ArrayList<>();
				this.log_du2cuData.get(du_router).put(cu_router, tmp);
				this.log_du2cuData.get(du_router).get(cu_router).add(ww.runUntil + " " + size);
			}
		}else {
			HashMap<CURouter,ArrayList<String>> tmp_c = new HashMap<>();
			this.log_du2cuData.put(du_router, tmp_c);
			ArrayList<String> tmp = new ArrayList<>();
			this.log_du2cuData.get(du_router).put(cu_router, tmp);
			this.log_du2cuData.get(du_router).get(cu_router).add(ww.runUntil + " " + size);					
		}
	}
	public void update_xy(CURouter cu_router,DURouter du_router, double calcu_size) {
		if(this.log_CUXY.containsKey(cu_router)) {
			if(this.log_CUXY.get(cu_router).containsKey(du_router)) {
				double tmp = this.log_CUXY.get(cu_router).get(du_router);
				this.log_CUXY.get(cu_router).put(du_router, tmp+calcu_size);
			}else {
				this.log_CUXY.get(cu_router).put(du_router, calcu_size);
			}
		}else {
			HashMap<DURouter, Double> tmp = new HashMap<>();
			this.log_CUXY.put(cu_router, tmp);
			this.log_CUXY.get(cu_router).put(du_router, calcu_size);
		}
	}
	//CU_router计算来自othercuR的du的多少量
	public void update_y(CURouter cu_router, CURouter othercuR, DURouter du_router, double calcu_size) {
		World ww = SimScenario.getInstance().getWorld();
		if(this.log_CUY.containsKey(cu_router)) {
			if(this.log_CUY.get(cu_router).containsKey(othercuR)) {
				if(this.log_CUY.get(cu_router).get(othercuR).containsKey(du_router)) {
					this.log_CUY.get(cu_router).get(othercuR).get(du_router).add(ww.runUntil +" " + calcu_size);
				}else {
					ArrayList<String> tmp = new ArrayList<>();
					this.log_CUY.get(cu_router).get(othercuR).put(du_router, tmp);
					this.log_CUY.get(cu_router).get(othercuR).get(du_router).add(ww.runUntil +" " + calcu_size);
				}
			}else
			{
				//HashMap<CURouter,HashMap<DURouter,ArrayList<String>>> tmp_xy = new HashMap<>();
				//this.log_CUY.put(cu_router, tmp_xy);
				HashMap<DURouter,ArrayList<String>> tmp_y = new HashMap<>();			
				this.log_CUY.get(cu_router).put(othercuR, tmp_y);
				ArrayList<String> tmp = new ArrayList<>();
				this.log_CUY.get(cu_router).get(othercuR).put(du_router, tmp);
				this.log_CUY.get(cu_router).get(othercuR).get(du_router).add(ww.runUntil +" " + calcu_size);
			}
		}else {
			HashMap<CURouter,HashMap<DURouter,ArrayList<String>>> tmp_xy = new HashMap<>();
			this.log_CUY.put(cu_router, tmp_xy);
			HashMap<DURouter,ArrayList<String>> tmp_y = new HashMap<>();			
			this.log_CUY.get(cu_router).put(othercuR, tmp_y);
			ArrayList<String> tmp = new ArrayList<>();
			this.log_CUY.get(cu_router).get(othercuR).put(du_router, tmp);
			this.log_CUY.get(cu_router).get(othercuR).get(du_router).add(ww.runUntil +" " + calcu_size);
		}		
	}
	
	public void update_x(CURouter cu_router, DURouter du_router, double calcu_size) {
		World ww = SimScenario.getInstance().getWorld();
		if(this.log_CUX.containsKey(cu_router)) {
			if(this.log_CUX.get(cu_router).containsKey(du_router)) {
				this.log_CUX.get(cu_router).get(du_router).add(ww.runUntil +" " + calcu_size);
			}else {
				ArrayList<String> tmp = new ArrayList<>();
				this.log_CUX.get(cu_router).put(du_router, tmp);
				this.log_CUX.get(cu_router).get(du_router).add(ww.runUntil +" " + calcu_size);
			}
		}else {
			HashMap<DURouter,ArrayList<String>> tmp_xy = new HashMap<>();
			this.log_CUX.put(cu_router, tmp_xy);
			ArrayList<String> tmp = new ArrayList<>();
			this.log_CUX.get(cu_router).put(du_router, tmp);
			this.log_CUX.get(cu_router).get(du_router).add(ww.runUntil +" " + calcu_size);
		}	
	}

	public void update_xSelf(CURouter cu_router, DURouter du_router, double calcu_size) {
		World ww = SimScenario.getInstance().getWorld();
		if(this.log_CUXSelf.containsKey(cu_router)) {
			if(this.log_CUXSelf.get(cu_router).containsKey(du_router)) {
				this.log_CUXSelf.get(cu_router).get(du_router).add(ww.runUntil +" " + calcu_size);
			}else {
				ArrayList<String> tmp = new ArrayList<>();
				this.log_CUXSelf.get(cu_router).put(du_router, tmp);
				this.log_CUXSelf.get(cu_router).get(du_router).add(ww.runUntil +" " + calcu_size);
			}
		}else {
			HashMap<DURouter,ArrayList<String>> tmp_xy = new HashMap<>();
			this.log_CUXSelf.put(cu_router, tmp_xy);
			ArrayList<String> tmp = new ArrayList<>();
			this.log_CUXSelf.get(cu_router).put(du_router, tmp);
			this.log_CUXSelf.get(cu_router).get(du_router).add(ww.runUntil +" " + calcu_size);
		}	
	}
}
