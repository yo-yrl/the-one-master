package routing;

import java.util.Random;

import core.Settings;

public class DURouter extends ActiveRouter  {

	public static final String DURouter_NS = "DURouter";
	
	//���������
	public int privIndex = 0;
	public Random privSeed = new Random();
	// ���ݵ�������
	public static final String DataArrival = "DataArrival";
	public int expectedDataArrival = 0; //���ݵ����������������ȷֲ�
	 
	//���г�ʼ����
	public static final String InitQueueLen = "InitQueueLen";
	public double DU_QueueLength = 0;  //#####���г��ȳ�ʼ�����Բ�Ϊ0 
	
	//DU��ʼ��
	public DURouter(Settings s) {
		super(s);
		Settings DataArrivalSettings = new Settings(DURouter_NS);
		if(DataArrivalSettings.contains(DataArrival)) {
			expectedDataArrival = DataArrivalSettings.getInt(DataArrival);
		}else {
			expectedDataArrival = 10;
		}
			
		Settings QueueLenSettings = new Settings(DURouter_NS);
		if(QueueLenSettings.contains(InitQueueLen)) {
			DU_QueueLength = QueueLenSettings.getDouble(InitQueueLen);
		}else {
			DU_QueueLength = 0;
		}
		this.privSeed.setSeed(++this.privIndex);
	}
	
	protected DURouter(DURouter r) {
		super(r);
		this.privIndex = ++r.privIndex;
		this.privSeed.setSeed(this.privIndex);
		
		this.DU_QueueLength = r.DU_QueueLength;
		this.expectedDataArrival = r.expectedDataArrival;
		
	}
	
	@Override
	public MessageRouter replicate() {
		DURouter r = new DURouter(this);
		return r;
	}

	@Override
	public void update() {
		super.update();
		//Random r = new Random();
		//r.setSeed(this.getHost().getAddress());
		DU_QueueLength += this.privSeed.nextDouble() * this.expectedDataArrival * 2;
	}
	
	public void updateByCU(double size){
		DU_QueueLength = Math.max(DU_QueueLength - size, 0);
	}
}
