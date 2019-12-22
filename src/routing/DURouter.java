package routing;
import java.util.ArrayList;
import java.util.Random;

import core.Connection;
import core.DTNHost;
import core.Settings;

/*
 * 需要更改参数：到来量是均匀分布 random*expectRate
 */
public class DURouter extends ActiveRouter  {

	public static final String DURouter_NS = "DURouter";
	
	//输入参数----------------------------------------------------------------------------------------------
	public int privIndex = 0;
	public Random privSeed = new Random();
	
	// 数据到来期望
	public static final String DataArrivalRatePool = "DataArrivalRatePool";
	public double[] expectedDataArrivalRatePool;
	public double expectedDataArrival;
	 
	//队列初始长度
	public static final String Init_muQueueLen = "Init_muQueueLen";
	public double mu_QueueLength = 0;  	
	//new queue------------------------------4
	public double hat_mu_QueueLength = 0;	
	public double gamma_mu_QueueLength = 0;
	public double zeta = 0;
	public static final String HATEPSILON = "HatEpsilon";
	public double hat_epsilon = 0;
	//------------------------------------------
	
	//SGD更新步长
	public static final String EPSILON = "Epsilon";
	public double epsilon = 0;
	//------------------------------------------------------------------------------------------------------------
	
	public CURouter nearest_CURouter = null;
	boolean flag = true;
	
	//***********************************DU初始化*************************************************
	public DURouter(Settings s) throws Exception {
		super(s);
		//设置随机种子
		this.privSeed.setSeed(++this.privIndex);
		
		Settings DataArrivalSettings = new Settings(DURouter_NS);
		//初始化 DU的平均到达率
		if(DataArrivalSettings.contains(DataArrivalRatePool)) {
			expectedDataArrivalRatePool = DataArrivalSettings.getCsvDoubles(DataArrivalRatePool);
		}
		//随机选取数据到来量
		this.expectedDataArrival = this.expectedDataArrivalRatePool[this.privSeed
				.nextInt(this.expectedDataArrivalRatePool.length)];
		//epsilon 更新步长
		if(DataArrivalSettings.contains(EPSILON)) {
			epsilon = DataArrivalSettings.getDouble(EPSILON);
		}
		//初始mu队列长度
		if(DataArrivalSettings.contains(Init_muQueueLen)) {
			mu_QueueLength = DataArrivalSettings.getDouble(Init_muQueueLen);
		}
		//new-------------------------------------------------------------
		this.hat_mu_QueueLength = this.mu_QueueLength;
		this.gamma_mu_QueueLength = this.mu_QueueLength;
		this.zeta = Math.sqrt(this.epsilon)*Math.pow(Math.log(this.epsilon), 2);
		if(DataArrivalSettings.contains(HATEPSILON)) 
			this.hat_epsilon = DataArrivalSettings.getDouble(HATEPSILON);
		//-----------------------------------------------------------------------
	}
	// ***********************************构造函数************************************
	protected DURouter(DURouter r) {
		super(r);
		this.privIndex = ++r.privIndex;
		this.privSeed.setSeed(this.privIndex);
		this.flag = true;
		
		this.mu_QueueLength = r.mu_QueueLength;
		//new quequ----------------------------------------------4
		this.hat_mu_QueueLength = r.mu_QueueLength;
		this.gamma_mu_QueueLength = r.gamma_mu_QueueLength;
		this.hat_epsilon = r.hat_epsilon;
		this.zeta = r.zeta;
		//-------------------------------------------
		
		this.epsilon = r.epsilon;
		this.expectedDataArrivalRatePool = r.expectedDataArrivalRatePool;
		this.expectedDataArrival = this.expectedDataArrivalRatePool[this.privSeed
				.nextInt(this.expectedDataArrivalRatePool.length)];
	}
	
	@Override
	public MessageRouter replicate() {
		DURouter r = new DURouter(this);
		return r;
	}

	// -------------------------------------队列更新--加 数据到来量=epsilon *到来量---------------------------------
	@Override
	public void update() {
		super.update();
		if(this.flag) 
		{
			getNearestCU(); 
			flag = false;
		}
		mu_QueueLength += this.epsilon * this.privSeed.nextDouble() * this.expectedDataArrival * 2;
	}
	
	// -------------------------------------队列更新--减 传去CU的量-----------------------------------------------------
	public void updateByCU(double size){
		mu_QueueLength = Math.max(mu_QueueLength - this.epsilon * size, 0);
	}
	public void Hat_updateByCU(double size){
		hat_mu_QueueLength = Math.max(hat_mu_QueueLength - this.hat_epsilon * size, 0);
	}
	
	public void getNearestCU()
	{
		DTNHost host = this.getHost();
		ArrayList<Connection> conns = (ArrayList<Connection>) host.getConnections();
		// 遍历所有连接
		double dis = Double.MAX_VALUE;
		
		for (Connection c : conns) {
			DTNHost otherHost = c.getOtherNode(host);
			if (otherHost.toString().contains("C")) // ---CU
			{
				double tmp = host.getLocation().distance(otherHost.getLocation());
				if (tmp < dis) {
					dis = tmp;
					this.nearest_CURouter = (CURouter) otherHost.getRouter();
				}
			}
		}
	}
	
}
