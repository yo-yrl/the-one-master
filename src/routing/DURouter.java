package routing;
import java.util.ArrayList;
import java.util.Random;

import core.Connection;
import core.DTNHost;
import core.Settings;

/*
 * ��Ҫ���Ĳ������������Ǿ��ȷֲ� random*expectRate
 */
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
	//new queue------------------------------4
	public double hat_mu_QueueLength = 0;	
	public double gamma_mu_QueueLength = 0;
	public double zeta = 0;
	public static final String HATEPSILON = "HatEpsilon";
	public double hat_epsilon = 0;
	//------------------------------------------
	
	//SGD���²���
	public static final String EPSILON = "Epsilon";
	public double epsilon = 0;
	//------------------------------------------------------------------------------------------------------------
	
	public CURouter nearest_CURouter = null;
	boolean flag = true;
	
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
		//new-------------------------------------------------------------
		this.hat_mu_QueueLength = this.mu_QueueLength;
		this.gamma_mu_QueueLength = this.mu_QueueLength;
		this.zeta = Math.sqrt(this.epsilon)*Math.pow(Math.log(this.epsilon), 2);
		if(DataArrivalSettings.contains(HATEPSILON)) 
			this.hat_epsilon = DataArrivalSettings.getDouble(HATEPSILON);
		//-----------------------------------------------------------------------
	}
	// ***********************************���캯��************************************
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

	// -------------------------------------���и���--�� ���ݵ�����=epsilon *������---------------------------------
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
	
	// -------------------------------------���и���--�� ��ȥCU����-----------------------------------------------------
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
		// ������������
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
