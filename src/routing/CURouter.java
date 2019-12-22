package routing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import core.Connection;
import core.DTNHost;
import core.Settings;
import core.SimScenario;
import core.World;

/*
 * DU-CU���ʣ�CU-CU����
 * DU-CU������ۣ�CU-CU�������
 */
public class CURouter extends ActiveRouter {
	public static final String CURouter_NS = "CURouter";

	//---------------------------------�������-----------------------------------------	
	//��������
	//DU
	public static final String DU_TransRateDbound = "DU_TransRateDownBound";	
	public static final String DU_TransRateUbound = "DU_TransRateUpBound";	
	public int []DU_TransRateUpBound; 
	public int []DU_TransRateDownBound;
	public int DU_TransRateUpbound; 
	public int DU_TransRateDownbound;
	//CU
	public static final String CU_TransRate = "CU_TransRate";	
	public int CU_TransRateUpBound = 0;
	public int CU_TransRateDownBound = 0;
	//����
	public static final String DU2CUTransCost = "DU2CU_TransCost";	
	public double DU2CU_TransCost = 0;
	public static final String CU2CUTransCost = "CU2CU_TransCost";	
	public double CU2CU_TransCost = 0;
	public static final String CUCalCost = "CU_CalCost";	
	
	//���ü�������
	public static final String MaxCAPool = "MaxCapacityPool";
	public double[] MaxCapacityPool;
	public double maxCapacity;
	public double curCapacity;
	
	public int privIndex = 50;
	public Random privSeed = new Random();

	public HashMap<DTNHost, Double> DU_Rates = new HashMap<>(); // CU to DUs
	public HashMap<DTNHost, Double> CU_Rates = new HashMap<>(); // CU to CUs(others)

	//----DU_i������/sum(DU_i������)-----DU Host, ������---------
	public HashMap<DTNHost, Double> delta = new HashMap<>();
	public boolean init = true;//��ʾdeltaֻ��ʼ��һ��
	//----------------------------------------------------------------------------------------------

	// -----------------------------���Ʊ���-----------------------------------------------------
	//DU��˭����CU Host, 1 or 0
	public HashMap<DTNHost, Integer> DU_Alpha = new HashMap<>();
	//DU��·��ʱռ��: DU Host��ռ��
	public HashMap<DTNHost, Double> DU_Theta = new HashMap<>();
	//CU�Լ��������DU Host�� �����
	public HashMap<DTNHost, Double> CU_X = new HashMap<>();
	// ��һά�ǶԶ�CU, �ڶ�ά��DU: ��CU���ĸ�CU, ���ĸ�DU������
	public HashMap<DTNHost, HashMap<DTNHost, Double>> CU_Y = new HashMap<>();
	// ----------------------------------------------------------------------------------------------

	// ------------------------------P2����Ĳ���------------------------------------------------------
	HashMap<DTNHost, Double> X_Param = new HashMap<>();
	HashMap<DTNHost, HashMap<DTNHost, Double>> Y_Param = new HashMap<>();
	// ----------------------------------------------------------------------------------------------------

	// --------------------------------Cost��ϵ��------------------------------------------------
	public HashMap<DTNHost, Double> DU_CU_Trans_Cost = new HashMap<>(); // DU��CU�䴫�����
	public double CU_Cal_Cost; // CU�ļ������
	public HashMap<DTNHost, Double> CU_CU_Trans_Cost = new HashMap<>(); // CU��CU�䴫�����
	// ----------------------------------------------------------------------------------------------

	// --------------------------------SGD����---------------------------------------------------
	//���²��� = DU��epsilon
	public double epsilon = 0;	
	//��������ɽ���
	public static final String OMEGA = "Omega";	
	public double Omega = 0;
	//CU����
	public HashMap<DTNHost, Double> eta_QueueLength = new HashMap<>();
	public HashMap<DTNHost, Double> lambda_QueueLength = new HashMap<>();
	public HashMap<DTNHost, Double> pi_QueueLength = new HashMap<>();
	
	//new queue----------------------------------------------------------------------------------8
	public HashMap<DTNHost, Integer> hat_DU_Alpha = new HashMap<>();
	public double hat_epsilon = 0;
	public double zeta = 0;
	public HashMap<DTNHost, Double> hat_eta_QueueLength = new HashMap<>();
	public HashMap<DTNHost, Double> hat_lambda_QueueLength = new HashMap<>();
	public HashMap<DTNHost, Double> hat_pi_QueueLength = new HashMap<>();
	
	public HashMap<DTNHost, Double> gamma_eta_QueueLength = new HashMap<>();
	public HashMap<DTNHost, Double> gamma_lambda_QueueLength = new HashMap<>();
	public HashMap<DTNHost, Double> gamma_pi_QueueLength = new HashMap<>();
	// ----------------------------------------------------------------------------------------------	

	//**************************************���캯��***********************************************
	public CURouter(Settings s) {
		super(s);		
		// ��������
		this.privSeed.setSeed(++this.privIndex);		
		
		Settings CUSettings = new Settings(CURouter_NS);
		//adding ---
		if (CUSettings.contains(DU_TransRateDbound)) 
			this.DU_TransRateDownBound = CUSettings.getCsvInts(DU_TransRateDbound);		
		this.DU_TransRateDownbound = this.DU_TransRateDownBound[this.privIndex%this.DU_TransRateDownBound.length];
		
		if (CUSettings.contains(DU_TransRateUbound)) 
			this.DU_TransRateUpBound = CUSettings.getCsvInts(DU_TransRateUbound);		
		this.DU_TransRateUpbound = this.DU_TransRateUpBound[this.privIndex%this.DU_TransRateUpBound.length];
		if (CUSettings.contains(CU_TransRate)) {
			int[] tmp = CUSettings.getCsvInts(CU_TransRate);	
			this.CU_TransRateUpBound = tmp[1];
			this.CU_TransRateDownBound = tmp[0];
		}
		if (CUSettings.contains(DU2CUTransCost)) 
			this.DU2CU_TransCost = CUSettings.getDouble(DU2CUTransCost);	
			
		if (CUSettings.contains(CUCalCost)) 
			this.CU_Cal_Cost= CUSettings.getDouble(CUCalCost);	
		
		if (CUSettings.contains(CU2CUTransCost)) 
			this.CU2CU_TransCost= CUSettings.getDouble(DU2CUTransCost);	
		//adding end---
		if (CUSettings.contains(OMEGA)) 
			this.Omega = CUSettings.getDouble(OMEGA);
		
		if (CUSettings.contains(MaxCAPool)) 
			this.MaxCapacityPool = CUSettings.getCsvDoubles(MaxCAPool);		
		//this.maxCapacity = this.MaxCapacityPool[this.privSeed.nextInt(this.MaxCapacityPool.length)];
		this.maxCapacity = this.MaxCapacityPool[this.privIndex%this.MaxCapacityPool.length];
		this.curCapacity = 0;		
		
	}

	protected CURouter(CURouter r) {
		super(r);
		//�����������
		this.privIndex = ++r.privIndex;
		this.privSeed.setSeed(this.privIndex);
		
		this.MaxCapacityPool = r.MaxCapacityPool;
		//this.maxCapacity = this.MaxCapacityPool[this.privSeed.nextInt(this.MaxCapacityPool.length)];
		this.maxCapacity = this.MaxCapacityPool[this.privIndex%this.MaxCapacityPool.length];
		this.curCapacity = r.curCapacity;
		//adding ----Rate/Cost input params
		this.DU_TransRateUpBound = r.DU_TransRateUpBound;
		this.DU_TransRateUpbound = this.DU_TransRateUpBound[this.privIndex%this.DU_TransRateUpBound.length];
		this.DU_TransRateDownBound  = r.DU_TransRateDownBound;
		this.DU_TransRateDownbound = this.DU_TransRateDownBound[this.privIndex%this.DU_TransRateDownBound.length];		
		
		this.CU_TransRateUpBound = r.CU_TransRateUpBound;
		this.CU_TransRateDownBound  = r.CU_TransRateDownBound;
		
		this.DU2CU_TransCost = r.DU2CU_TransCost;
		this.CU2CU_TransCost = r.CU2CU_TransCost;
		this.CU_Cal_Cost = r.CU_Cal_Cost;
		//adding end ------
		this.epsilon = r.epsilon;
		this.Omega = r.Omega;
		
		this.delta = r.delta;
		this.init = true;
		
		this.DU_Rates = new HashMap<>();
		this.DU_Alpha = new HashMap<>();
		this.DU_Theta = new HashMap<>();

		this.CU_Rates = new HashMap<>();
		this.CU_X = new HashMap<>();		
		this.CU_Y = new HashMap<>();

		this.X_Param = new HashMap<>();
		this.Y_Param = new HashMap<>();

		this.CU_Cal_Cost = r.CU_Cal_Cost;
		this.DU_CU_Trans_Cost = new HashMap<>();
		this.CU_CU_Trans_Cost = new HashMap<>();
		
		this.eta_QueueLength = new HashMap<>();
		this.lambda_QueueLength = new HashMap<>();
		this.pi_QueueLength = new HashMap<>();
		
		//new Queue-------------------------------------------
		this.hat_eta_QueueLength = new HashMap<>();
		this.hat_lambda_QueueLength = new HashMap<>();
		this.hat_pi_QueueLength = new HashMap<>();
		this.gamma_eta_QueueLength = new HashMap<>();
		this.gamma_lambda_QueueLength = new HashMap<>();
		this.gamma_pi_QueueLength = new HashMap<>();
		this.zeta = r.zeta;
		this.hat_epsilon = r.epsilon;
		this.hat_DU_Alpha = new HashMap<>();
		//----------------------------------------------------------
	}

	@Override
	public MessageRouter replicate() {
		CURouter r = new CURouter(this);
		return r;
	}

	// **************************************���º���**************************************
	@Override
	public void update() {
		super.update();
		
		//---------------ֻ��ʼ��һ�εı�����delta, epsilon----------------
		if (this.init == true) {
			
			World w = SimScenario.getInstance().getWorld();
			//epsilon
			double sum = 0;
			for (DTNHost host : w.getHosts()) {
				if (host.toString().contains("D")) {
					DURouter du_router = (DURouter) host.getRouter();
					sum += du_router.expectedDataArrival;
					this.epsilon = du_router.epsilon;
					//-----------------------------------------2
					this.zeta = du_router.zeta;
					this.hat_epsilon = du_router.hat_epsilon;
					//-----------------------------------
				}
			}
			// delta
			for (DTNHost host : w.getHosts()) {
				if (host.toString().contains("D")) {
					DURouter du_router = (DURouter) host.getRouter();
					double tmp = du_router.expectedDataArrival;
					this.delta.put(host, tmp / sum);
				}
			}
			this.init = false;
		}
		// ----���µ�ǰ�������� -- 0.2--0.8-------------
		//***************+++++++++++++++++*****************������������������������
		this.curCapacity = this.maxCapacity * (0.2 + this.privSeed.nextDouble()*0.6);

		// ----���¸�CU���������----����DU_Rates and CU_Rates-----
		DTNHost host = this.getHost();
		ArrayList<Connection> conns = (ArrayList<Connection>) host.getConnections();
		// ������������
		for (Connection c : conns) {
			DTNHost otherHost = c.getOtherNode(host);
			
			if (otherHost.toString().contains("D")) // ---DU
			{
				if (!this.eta_QueueLength.containsKey(otherHost)) {
					
					this.eta_QueueLength.put(otherHost, 0.0);//eta_ij, cu ����
					this.lambda_QueueLength.put(otherHost, 0.0);//lambda_ij
					this.pi_QueueLength.put(otherHost, 0.0);//pi_ij
					//adding-----------------------------------------------------6
					this.hat_eta_QueueLength.put(otherHost, 0.0);//eta_ij, cu ����
					this.hat_lambda_QueueLength.put(otherHost, 0.0);//lambda_ij
					this.hat_pi_QueueLength.put(otherHost, 0.0);//pi_ij
					this.gamma_eta_QueueLength.put(otherHost, 0.0);//eta_ij, cu ����
					this.gamma_lambda_QueueLength.put(otherHost, 0.0);//lambda_ij
					this.gamma_pi_QueueLength.put(otherHost, 0.0);//pi_ij		
					this.hat_DU_Alpha.put(otherHost, 0);
					//-------------------------------------------------------------
					
					this.DU_Alpha.put(otherHost, 0);//alpha_ij ��˭
					this.DU_Theta.put(otherHost, 0.0);//theta_ij ��ʱ

					this.CU_X.put(otherHost, 0.0);//x_ij
					this.X_Param.put(otherHost, 0.0);
					//***************+++++++++++++++++*****************����������������������
					double distance = host.getLocation().distance(otherHost.getLocation());
					this.DU_CU_Trans_Cost.put(otherHost,  this.DU2CU_TransCost*(distance/100.0));//DU2CU�� ������� To do! ���������� [����ϵ�� * ����/100]
				}
				double rate = Get_DU_Rate(); // --To do!
				this.DU_Rates.put(otherHost, rate);
			}
			if (otherHost.toString().contains("C")) // ---CU
			{
				if (!this.CU_Y.containsKey(otherHost)) {
					HashMap<DTNHost, Double> tmpMap1 = new HashMap<>();
					HashMap<DTNHost, Double> tmpMap2 = new HashMap<>();
					for (DTNHost tmpduHost : this.eta_QueueLength.keySet()) {
						tmpMap1.put(tmpduHost, 0.0);
						tmpMap2.put(tmpduHost, 0.0);
					}
					this.CU_Y.put(otherHost, tmpMap1);//y_ijk
					this.Y_Param.put(otherHost, tmpMap2);			
					
					double distance = host.getLocation().distance(otherHost.getLocation());
					//***************+++++++++++++++++*****************����������������������
					this.CU_CU_Trans_Cost.put(otherHost, this.CU2CU_TransCost*(distance/1000.0));// CU2CU�Ĵ������ To do!
				}
				double rate = Get_CU_Rate(); // --To do!
				this.CU_Rates.put(otherHost, rate);
			}
		}
	}

	
	// **************************************���¶��к���**************************************
	
	//------��DU��ȡ: 1. ����eta_ij(+)  2.����mu_i(-)-------
	public void GetDataFromDU(DTNHost DU) // theta*d_ij
	{
		double size = this.DU_Theta.get(DU) * this.DU_Rates.get(DU);
		//����eta_ij
		double queueSize = this.eta_QueueLength.get(DU);		
		queueSize += this.epsilon * size;
		this.eta_QueueLength.put(DU, queueSize);		
		//����mu_i
		DURouter durouter = (DURouter) DU.getRouter();
		durouter.updateByCU(size);
	}
	public void Hat_GetDataFromDU(DTNHost DU, double theta) // theta*d_ij
	{
		double size = theta * this.DU_Rates.get(DU);
		//����eta_ij
		double queueSize = this.hat_eta_QueueLength.get(DU);		
		queueSize += this.hat_epsilon * size;
		this.hat_eta_QueueLength.put(DU, queueSize);		
		//����mu_i
		DURouter durouter = (DURouter) DU.getRouter();
		durouter.Hat_updateByCU(size);
	}
	
	//------x_ii�Լ���:  1. ����eta_ij(-)  2. lambda_ij   3. pi_ij------
	public void CalculateDUByItself(DTNHost DU) // xx
	{
		double eta_queueSize = this.eta_QueueLength.get(DU);
		eta_queueSize = Math.max(eta_queueSize - this.epsilon * this.CU_X.get(DU), 0);
		this.eta_QueueLength.put(DU, eta_queueSize);

		double lambda_queueSize = this.lambda_QueueLength.get(DU);
		lambda_queueSize = Math.max(lambda_queueSize + this.epsilon * (this.CU_X.get(DU) - GetSumX(DU, 1)), 0);
		this.lambda_QueueLength.put(DU, lambda_queueSize);

		double pi_queueSize = this.pi_QueueLength.get(DU);
		pi_queueSize = Math.max(pi_queueSize + this.epsilon * ((-1) * this.CU_X.get(DU) + GetSumX(DU, -1)), 0);
		this.pi_QueueLength.put(DU, pi_queueSize);
	}
	public void Hat_CalculateDUByItself(DTNHost DU) // xx
	{
		double eta_queueSize = this.hat_eta_QueueLength.get(DU);
		eta_queueSize = Math.max(eta_queueSize - this.hat_epsilon * this.CU_X.get(DU), 0);
		this.hat_eta_QueueLength.put(DU, eta_queueSize);

		double lambda_queueSize = this.hat_lambda_QueueLength.get(DU);
		lambda_queueSize = Math.max(lambda_queueSize + this.hat_epsilon * (this.CU_X.get(DU) - GetSumX(DU, 1)), 0);
		this.hat_lambda_QueueLength.put(DU, lambda_queueSize);

		double pi_queueSize = this.hat_pi_QueueLength.get(DU);
		pi_queueSize = Math.max(pi_queueSize + this.hat_epsilon * ((-1) * this.CU_X.get(DU) + GetSumX(DU, -1)), 0);
		this.hat_pi_QueueLength.put(DU, pi_queueSize);
	}
	
	//------��������CU����: 1. ����eta_ij(-)-------
	public void SendDataToCU(DTNHost CU, DTNHost DU) // y_ijk  (�̶�j�� ����k, i)
	{
		double sendAmount = this.CU_Y.get(CU).get(DU);
		double curLength = this.eta_QueueLength.get(DU);
		curLength = Math.max(curLength - this.epsilon * sendAmount, 0);
		this.eta_QueueLength.put(DU, curLength);
	}
	
	public void Hat_SendDataToCU(DTNHost CU, DTNHost DU) // y_ijk  (�̶�j�� ����k, i)
	{
		double sendAmount = this.CU_Y.get(CU).get(DU);
		double curLength = this.hat_eta_QueueLength.get(DU);
		curLength = Math.max(curLength - this.hat_epsilon * sendAmount, 0);
		this.hat_eta_QueueLength.put(DU, curLength);
	}
	
	//------��ȡ����CU����: 1. lambda_ij  2. pi_ij------
	public void RecvDataFromCU(DTNHost otherCU, DTNHost DU) {  //y_ikj
		CURouter other_router = (CURouter) otherCU.getRouter();
		HashMap<DTNHost, Double> other_map = other_router.CU_Y.get(this.getHost());
		double tmp_Yikj = other_map.get(DU);

		double lambda_queueSize = this.lambda_QueueLength.get(DU);
		lambda_queueSize = Math.max(lambda_queueSize + this.epsilon * (tmp_Yikj - GetSumY(other_map, DU, 1)), 0);
		this.lambda_QueueLength.put(DU, lambda_queueSize);

		double pi_queueSize = this.pi_QueueLength.get(DU);
		pi_queueSize = Math.max(pi_queueSize + this.epsilon * ((-1) * tmp_Yikj + GetSumY(other_map, DU, -1)), 0);
		this.pi_QueueLength.put(DU, pi_queueSize);
	}
	public void Hat_RecvDataFromCU(DTNHost otherCU, DTNHost DU) {  //y_ikj
		CURouter other_router = (CURouter) otherCU.getRouter();
		HashMap<DTNHost, Double> other_map = other_router.CU_Y.get(this.getHost());
		double tmp_Yikj = other_map.get(DU);

		double lambda_queueSize = this.hat_lambda_QueueLength.get(DU);
		lambda_queueSize = Math.max(lambda_queueSize + this.hat_epsilon * (tmp_Yikj - GetSumY(other_map, DU, 1)), 0);
		this.hat_lambda_QueueLength.put(DU, lambda_queueSize);

		double pi_queueSize = this.hat_pi_QueueLength.get(DU);
		pi_queueSize = Math.max(pi_queueSize + this.hat_epsilon * ((-1) * tmp_Yikj + GetSumY(other_map, DU, -1)), 0);
		this.hat_pi_QueueLength.put(DU, pi_queueSize);
	}
	
	//x_ij , �̶�j------��i���
		private double GetSumX(DTNHost DU, int factor) {
			double sum = 0;
			for (DTNHost host : this.CU_X.keySet())
				sum += this.CU_X.get(host);
			sum = (this.delta.get(DU) + factor * this.Omega) * sum;
			return sum;
		}

	//�̶�j, k  ��i��� ------y_ikj
	private double GetSumY(HashMap<DTNHost, Double> map, DTNHost DU, int factor) {
		double sum = 0;
		for (DTNHost host : map.keySet())
			sum += map.get(host);
		sum = (this.delta.get(DU) + factor * this.Omega) * sum;
		return sum;
	}
	public double GetSum_P2() {
		double sum = 0;
		for (DTNHost du_host : this.lambda_QueueLength.keySet())
		{
				sum += this.lambda_QueueLength.get(du_host)*(this.delta.get(du_host) + this.Omega);
				sum -= this.pi_QueueLength.get(du_host)*(this.delta.get(du_host) - this.Omega);
		}
		//if(sum <= 0) sum = 0;
		return sum;
	}
	
	public double GetSum_P2(String flagQ) {
		double sum = 0;
		for (DTNHost du_host : this.lambda_QueueLength.keySet())
		{
			if(flagQ.equals("Gamma")) {
				sum += this.gamma_lambda_QueueLength.get(du_host)*(this.delta.get(du_host) + this.Omega);
				sum -= this.gamma_pi_QueueLength.get(du_host)*(this.delta.get(du_host) - this.Omega);
			}
			if(flagQ.equals("Hat")) {
				sum += this.hat_lambda_QueueLength.get(du_host)*(this.delta.get(du_host) + this.Omega);
				sum -= this.hat_pi_QueueLength.get(du_host)*(this.delta.get(du_host) - this.Omega);
			}
		}
		//if(sum <= 0) sum = 0;
		return sum;
	}
	

	private double Get_DU_Rate() {//DTNHost host, DTNHost otherHost
		//10-40  40-80
		int r = this.privSeed.nextInt(this.DU_TransRateUpbound - this.DU_TransRateDownbound+1)
				     +this.DU_TransRateDownbound;
		return r;
	}

	private double Get_CU_Rate() {
		//160-200
		int r = this.privSeed.nextInt(this.CU_TransRateUpBound - this.CU_TransRateDownBound+1)
			     +this.CU_TransRateDownBound;
		return r;
	}
}
