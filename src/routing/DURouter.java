package routing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Random;

import core.Settings;

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
	
	//SGD更新步长
	public static final String EPSILON = "Epsilon";
	public double epsilon = 0;
	//------------------------------------------------------------------------------------------------------------
	
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
		
	}
	// ***********************************构造函数************************************
	protected DURouter(DURouter r) {
		super(r);
		this.privIndex = ++r.privIndex;
		this.privSeed.setSeed(this.privIndex);
		
		this.mu_QueueLength = r.mu_QueueLength;
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
		mu_QueueLength += this.epsilon * this.privSeed.nextDouble() * this.expectedDataArrival * 2;
	}
	
	// -------------------------------------队列更新--减 传去CU的量-----------------------------------------------------
	public void updateByCU(double size){
		mu_QueueLength = Math.max(mu_QueueLength - this.epsilon * size, 0);
	}
}
