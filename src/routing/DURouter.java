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
	
	//�������----------------------------------------------------------------------------------------------
	public int privIndex = 0;
	public Random privSeed = new Random();
	
	// ���ݵ�������
	public static final String DataArrivalRatePool = "DataArrivalRatePool";
	public double[] expectedDataArrivalRatePool;
	public double expectedDataArrival;
	 
	//���г�ʼ����
	public static final String Init_muQueueLen = "Init_muQueueLen";
	public double mu_QueueLength = 0;  
	
	//SGD���²���
	public static final String EPSILON = "Epsilon";
	public double epsilon = 0;
	//------------------------------------------------------------------------------------------------------------
	
	//***********************************DU��ʼ��*************************************************
	public DURouter(Settings s) throws Exception {
		super(s);
		//�����������
		this.privSeed.setSeed(++this.privIndex);
		
		Settings DataArrivalSettings = new Settings(DURouter_NS);
		//��ʼ�� DU��ƽ��������
		if(DataArrivalSettings.contains(DataArrivalRatePool)) {
			expectedDataArrivalRatePool = DataArrivalSettings.getCsvDoubles(DataArrivalRatePool);
		}
		//���ѡȡ���ݵ�����
		this.expectedDataArrival = this.expectedDataArrivalRatePool[this.privSeed
				.nextInt(this.expectedDataArrivalRatePool.length)];
		//epsilon ���²���
		if(DataArrivalSettings.contains(EPSILON)) {
			epsilon = DataArrivalSettings.getDouble(EPSILON);
		}
		//��ʼmu���г���
		if(DataArrivalSettings.contains(Init_muQueueLen)) {
			mu_QueueLength = DataArrivalSettings.getDouble(Init_muQueueLen);
		}
		
	}
	// ***********************************���캯��************************************
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

	// -------------------------------------���и���--�� ���ݵ�����=epsilon *������---------------------------------
	@Override
	public void update() {
		super.update();
		mu_QueueLength += this.epsilon * this.privSeed.nextDouble() * this.expectedDataArrival * 2;
	}
	
	// -------------------------------------���и���--�� ��ȥCU����-----------------------------------------------------
	public void updateByCU(double size){
		mu_QueueLength = Math.max(mu_QueueLength - this.epsilon * size, 0);
	}
}
