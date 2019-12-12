package routing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import core.Connection;
import core.DTNHost;
import core.Settings;

public class CURouter extends ActiveRouter  {
	public static final String CURouter_NS = "CURouter";
	
	//输入变量
	public int privIndex = 0;
	public Random privSeed = new Random();
	//public static final String CapacityFileDir = "CapacityFileDir";
    public String CapFileDir = "./ICDCS_Data/result_google-cluster-data-1.csv";//defaultCapacityOfCU.txt";
    public double[] CapacityPool;// = new double[16]; //没有初始化
    public double curCapacity;
    
    public HashMap<DTNHost, Double> QueuesForDUs = new HashMap<>();
    public HashMap<DTNHost, Double> DU_Rates = new HashMap<>(); // CU to DUs    
    public HashMap<DTNHost, Double> CU_Rates = new HashMap<>(); // CU to CUs(others)
	
	//控制变量
    public HashMap<DTNHost, Double> DU_Alpha = new HashMap<>();
    
    public HashMap<DTNHost, Double> DU_Theta = new HashMap<>();
	
    public HashMap<DTNHost, Double> CU_X = new HashMap<>();
    
    public HashMap<DTNHost, Double> CU_Lambda = new HashMap<>();
    
    //第一维是对端CU, 第二维是DU: 该CU给哪个CU, 传哪个DU的数据
    public HashMap<DTNHost, HashMap<DTNHost, Double>> CU_Y = new HashMap<>();

    private void ReadFile(String fileName) throws IOException {
		StringBuffer sb = new StringBuffer();
        File file = new File(fileName);
        if (file.isFile() && file.exists())
        { 
            InputStreamReader read = new InputStreamReader(new FileInputStream(file));
            BufferedReader bufferedReader = new BufferedReader(read);
            String lineTxt = null;
            while ((lineTxt = bufferedReader.readLine()) != null)
                   sb.append(lineTxt);
            bufferedReader.close();
            read.close();
        }
       // System.out.println("#TestString: \n" + sb.toString());
        String[] str = sb.toString().split(",");
        System.out.println(str.length);
        CapacityPool = new double[str.length];
        for(int i=1; i<str.length; i++) {
        	//System.out.print(Double.parseDouble(str[i]));
        	this.CapacityPool[i-1] = Double.parseDouble(str[i]);
        }
	}

    public CURouter(Settings s) {
		super(s);
		
		//读取配置
//		Settings CpFileNSettings = new Settings(CURouter_NS);
//		if(CpFileNSettings.contains(CapacityFileDir)) {
//			CapFileDir = CpFileNSettings.getSetting(CapacityFileDir);
//		}else {
//			CapFileDir = "./ICDCS_Data/defaultCapacityOfCU.txt";
//		}
		try {
			// Init the CapacityPool
			ReadFile(this.CapFileDir);   //Read File----------------------------------------------
		} catch (IOException e) {
			System.out.println("File error!!");
		}
		
		// 设置种子
		//System.out.println(this.getHost().getAddress());
		this.privSeed.setSeed(++this.privIndex);
	}
	
	protected CURouter(CURouter r) {
		super(r);
		this.CapacityPool = r.CapacityPool;
		this.privIndex = ++r.privIndex ;
		//System.out.println(this.privIndex);
		this.privSeed.setSeed(this.privIndex);
		
		this.QueuesForDUs = new HashMap<>();
		this.DU_Rates = new HashMap<>();
		this.DU_Alpha = new HashMap<>();
		this.DU_Theta = new HashMap<>();
		
		this.CU_X = new HashMap<>();
		this.CU_Rates = new HashMap<>();
		this.CU_Lambda = new HashMap<>();
		this.CU_Y = new HashMap<>();
	}
	
	@Override
	public MessageRouter replicate() {
		CURouter r = new CURouter(this);
		return r;
	}

	@Override
	public void update() {
		super.update();
		//System.out.println(this.getHost().getAddress());
		//System.out.println("-------------");
		//更新当前计算能力		
		this.curCapacity = this.CapacityPool[this.privSeed.nextInt(this.CapacityPool.length)];
		
		//更新该CU的连接情况-- 实现初始化，主要是更新DU_Rates and CU_Rates
		DTNHost host = this.getHost();
		ArrayList<Connection> conns = (ArrayList<Connection>) host.getConnections();
		//遍历所有连接
		for(Connection c : conns)
		{
			DTNHost otherHost = c.getOtherNode(host);
			
			if(otherHost.toString().contains("D"))  //DU
			{
				//System.out.println("update DU info");
				if(!this.QueuesForDUs.containsKey(otherHost))
				{
					this.QueuesForDUs.put(otherHost, 0.0);
					this.DU_Alpha.put(otherHost, 0.0);
					this.DU_Theta.put(otherHost, 0.0);
				}
				double rate = Get_DU_Rate(host, otherHost); //1/dist * 10
				this.DU_Rates.put(otherHost, rate);
			}
			if(otherHost.toString().contains("C"))  //CU
			{
				//System.out.println("update CU info");
				if(!this.CU_Lambda.containsKey(otherHost))
				{
				     this.CU_Lambda.put(otherHost, 0.0);
				     HashMap<DTNHost, Double> tmpMap = new HashMap<>();
				     for(DTNHost tmpHost : this.QueuesForDUs.keySet())
				    	 tmpMap.put(tmpHost, 0.0);
				     this.CU_Y.put(otherHost, tmpMap);
				}
				double rate = Get_CU_Rate(host, otherHost); //1/dist * 20
				this.CU_Rates.put(otherHost, rate);
			}
		}
	}


	public void GetDataFromDU(DTNHost DU)
	{
		double queueSize = this.QueuesForDUs.get(DU);
		double size = this.DU_Theta.get(DU) * this.DU_Rates.get(DU);
		queueSize += size;
		this.QueuesForDUs.put(DU, queueSize);// put也可以替换旧值，且不存在时放入新值
		DURouter router = (DURouter)DU.getRouter();
		router.updateByCU(size);
	}
	
	public void SendDataToCU(DTNHost CU, DTNHost DU)
	{
		double sendAmount = this.CU_Y.get(CU).get(DU);
		double curLength = this.QueuesForDUs.get(DU);
		curLength = Math.max(curLength-sendAmount, 0);
		this.QueuesForDUs.put(DU, curLength);
	}
	
	public void CalculateDUByItself(DTNHost DU)
	{
		double queueSize = this.QueuesForDUs.get(DU);
		queueSize = Math.max(queueSize - this.CU_X.get(DU), 0);
		this.QueuesForDUs.put(DU, queueSize);
	}
	
	private double Get_DU_Rate(DTNHost host, DTNHost otherHost) {
		 double distance = host.getLocation().distance(otherHost.getLocation());
		 //System.out.println(distance);
		 double rate = (1000.0/distance) * this.privSeed.nextDouble() * 10; // 10M, 和距离有关
		 rate = (double)(Math.round((rate * 1000)/1000)); // 保留两位
		 //System.out.println(rate);
		 //To do rate!!!!--------------------------------------------
		 return rate;
	}
	
	private double Get_CU_Rate(DTNHost host, DTNHost otherHost) {
		//To do rate!!!!--------------------------------------------
		double distance = host.getLocation().distance(otherHost.getLocation());
		//System.out.println(distance);
		double rate = (1000.0/distance) * this.privSeed.nextDouble() * 20; // 20M, 和距离有关
		rate = (double)(Math.round((rate * 1000)/1000)); // 保留两位
		//System.out.println(rate);
		//To do rate!!!!--------------------------------------------
		return rate;
	}
}

