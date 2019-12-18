package routing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;

import com.ampl.AMPL;
import com.ampl.Objective;
import com.ampl.Variable;

import core.DTNHost;
import core.Settings;
import core.SimScenario;
import core.World;
import report.MessageStatsReport;

public class CNRouter extends ActiveRouter {

	// 输入参数--------------------------------------------------
	public static final String CNRouter_NS = "CNRouter";

	public static final String RHO = "App_rho";
	public int rho = 0;

	public static final String DU_NUM = "DU_Num";
	public int du_num = 0;

	public static final String CU_NUM = "CU_Num";
	public int cu_num = 0;
	
	double obj_function;  //保存目标函数值
	// ----------------------------------------------------------------------------------------------
	MessageStatsReport msr;
	public boolean init = true;    
	//-----------------------------------------------------------------------------------------------
	
	//***********************************构造函数************************************
	public CNRouter(Settings s) {
		super(s);		
		Settings RhoSettings = new Settings(CNRouter_NS);
		if (RhoSettings.contains(RHO)) 
			rho = RhoSettings.getInt(RHO);		
		
		if (RhoSettings.contains(DU_NUM)) 
			du_num = RhoSettings.getInt(DU_NUM);	
		
		if (RhoSettings.contains(CU_NUM)) 
			cu_num = RhoSettings.getInt(CU_NUM);
		
		obj_function = 0;
	}

	public CNRouter(CNRouter r) {
		super(r);
		this.rho = r.rho;
		this.du_num = r.du_num;
		this.cu_num = r.cu_num;
		this.obj_function = 0;
		this.init=true;
	}

	@Override
	public MessageRouter replicate() {
		CNRouter r = new CNRouter(this);
		return r;
	}

	@Override
	public void update() {
		super.update();
		if(this.init)
		{
			this.msr = (MessageStatsReport) mListeners.get(0);
			this.init=false;
		}
		
		World w = SimScenario.getInstance().getWorld();
		ArrayList<DTNHost> host_list = (ArrayList<DTNHost>) w.getHosts();
		
		// 自己的算法求解P1和P2
		HashMap<Integer, Integer> connNum = new HashMap<>();
		P1_Ours(host_list, connNum);
		try {
			P2_Ours(host_list);
		} catch (Exception e) {
			e.printStackTrace();
		}
		updateP1_Final(host_list, connNum);
	}
	
    //*******************************************P1_ours**************************************************
	private void P1_Ours(ArrayList<DTNHost> host_list, HashMap<Integer, Integer> connNum) {
		String P1_biMatching_FilePath = ".\\src\\routing\\py_Matching\\P1_Ours_GraphInfo.txt";
		PrintWriter pw = null;
		try {
			pw = new PrintWriter(new FileWriter(P1_biMatching_FilePath));
		} catch (Exception e) {
			e.printStackTrace();
		}
		StringBuffer sb = new StringBuffer();
		// 二分图节点数 不需要
		//int totalNum = this.du_num * this.cu_num + this.du_num;
		//sb.append(totalNum + "\n");
		double r = 0;
		// 构造二分图的边
		for (int du = 0; du < this.du_num; du++)
			for (int cu = 0; cu < this.cu_num; cu++)
				for (int k = 1; k <= this.du_num; k++) {
					sb.append(du + " " + cu + "k" + k);
					DTNHost du_host = host_list.get(du);
					DURouter du_router = (DURouter) du_host.getRouter();
					DTNHost cu_host = host_list.get(cu + this.du_num);
					CURouter cu_router = (CURouter) cu_host.getRouter();
					r = cu_router.DU_Rates.get(du_host) * (du_router.mu_QueueLength
							- cu_router.eta_QueueLength.get(du_host) - cu_router.DU_CU_Trans_Cost.get(du_host));
					r = Math.log(Math.pow(k - 1, k - 1) * r / Math.pow(k, k));
					if (r <= 0)
						r = 0;
					sb.append(" " + format(r) + "\n");
				}
		pw.println(sb.toString());
		// 调用python的二分图匹配
		Process proc = null;
		try {
			//String[] args = new String[] {"python3"," F:\\eclipse-workbench\\the-one-master\\src\\routing\\py_Matching\\bipartiteMatch.py", P1_biMatching_FilePath};
			String exe_cmd = "python3  F:\\eclipse-workbench\\the-one-master\\src\\routing\\py_Matching\\bipartiteMatch.py  ";			
			proc = Runtime.getRuntime()
					.exec(exe_cmd.concat(P1_biMatching_FilePath));
			// 用输入输出流来截取结果
			BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line = null;
			while ((line = in.readLine()) != null) {
				System.out.println(line);
				line.trim();
				line = line.replace("(", "");
				line = line.replace(")", "");
				line = line.replace("{", "");
				line = line.replace("}", "");
				line = line.replace(" ", "");
				String[] points = line.split(",");

				int firstNode, secondNode;
				for (int i = 0; i < points.length; i += 2) {
					if (points[i].contains("k")) {
						String[] split = points[i].split("k");
						firstNode = Integer.valueOf(split[0]);
						if (!connNum.containsKey(firstNode))
							connNum.put(firstNode, Integer.valueOf(split[1]));
						else if (connNum.get(firstNode) < Integer.valueOf(split[1]))
							connNum.put(firstNode, Integer.valueOf(split[1]));
						secondNode = Integer.valueOf(points[i + 1]);
						updateP1(firstNode, secondNode, host_list);
					} else {
						firstNode = Integer.valueOf(points[i]);
						String[] split = points[i + 1].split("k");
						secondNode = Integer.valueOf(split[0]);
						if (!connNum.containsKey(secondNode))
							connNum.put(secondNode, Integer.valueOf(split[1]));
						else if (connNum.get(secondNode) < Integer.valueOf(split[1]))
							connNum.put(secondNode, Integer.valueOf(split[1]));
						updateP1(secondNode, firstNode, host_list);
					}
				}
			}
			in.close();
			proc.waitFor();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
 //-------------------------------------------------p1 update-------------------------------------------------------
	private void updateP1(int CuNode, int DuNode, ArrayList<DTNHost> host_list) {
		DTNHost du_host = host_list.get(DuNode);
		DTNHost cu_host = host_list.get(CuNode + this.du_num);
		CURouter cu_router = (CURouter) cu_host.getRouter();
		cu_router.DU_Alpha.put(du_host, 1);
	}
	private void updateP1_Final(ArrayList<DTNHost> host_list, HashMap<Integer, Integer> connNum) {
		for (int cu = 0; cu < this.cu_num; cu++)
			for (int du = 0; du < this.du_num; du++) {
				DTNHost du_host = host_list.get(du);
				DTNHost cu_host = host_list.get(cu + this.du_num);
				CURouter cu_router = (CURouter) cu_host.getRouter();
				if (cu_router.DU_Alpha.get(du_host) == 1) {
					cu_router.DU_Theta.put(du_host, 1.0 / connNum.get(cu));
					cu_router.GetDataFromDU(du_host);
					obj_function += cu_router.DU_CU_Trans_Cost.get(du_host) / connNum.get(cu) * cu_router.DU_Rates.get(du_host);
					cu_router.DU_Alpha.put(du_host, 0);   //清空alpha
				}
			}
	}
	//--------------------------------------------------------------------------------------------------------
	//***************************************End P1_ours**************************************************
	
	//*******************************************P2_ours**************************************************
	@SuppressWarnings({ "resource" })
	private void P2_Ours(ArrayList<DTNHost> host_list) throws Exception {
		PrintWriter pw = null;
		String P2_Matching_FilePath = ".\\src\\routing\\py_Matching\\P2_Ours_GraphInfo.txt";
		try {
			pw = new PrintWriter(new FileWriter(P2_Matching_FilePath));
		} catch (Exception e) {
			e.printStackTrace();
		}
		StringBuffer sb = new StringBuffer();
		// 图节点数
		//int totalNum = this.cu_num * 2;
		//sb.append(totalNum + "\n");
		double r = 0;
		
		HashMap<CURouter, double[]> self_X = new HashMap<>();
		
		//自己连自己-------------------------------------------------------------------------------------
		for (int self_cu = 0; self_cu < this.cu_num; self_cu++) {
			DTNHost self_cu_host = host_list.get(self_cu + this.du_num);
			CURouter self_cu_router = (CURouter) self_cu_host.getRouter();
			getP2_X_Param(self_cu_router);
			sb.append(self_cu + " " + "m" + self_cu);

			// 调用AMPL----------P1   固定j，y系数为0   m max sum(ax)--------------------
			String p1_ampl_modFilePath = ".\\src\\routing\\ampl_ModDat\\P1_model.mod";
			String p1_ampl_datFilePath = ".\\src\\routing\\ampl_ModDat\\p1.dat";
			px_outTodat(self_cu_router, p1_ampl_datFilePath);
			// Create an AMPL instance
			AMPL ampl_p1 = new AMPL();
			ampl_p1.setOption("solver", "./minos");
			ampl_p1.read(p1_ampl_modFilePath);
			ampl_p1.readData(p1_ampl_datFilePath);
			ampl_p1.solve();
			// 获取返回值
			// 获取x_ij--->x_j[i]
			Objective obj = ampl_p1.getObjective("p1_obj");
			Variable xij_Var = ampl_p1.getVariable("x_ij");
			//double[] x_j = new double[this.du_num];
			self_X.put(self_cu_router, new double[this.du_num]);
			getOneDimensionVarFromAmpl(xij_Var, self_X.get(self_cu_router));
			// ------------------------------------------------------------------
			r = obj.value();
			// 需要返回结果给r赋值
			if (r <= 0)
				r = 0;
			sb.append(" " + format(r) + "\n");
		}
		
		HashMap<CURouter, double[]> Conn_X = new HashMap<>();
		
		//CU连不同CU---------------------------------------------------------------------
		for (int sent_cu = 0; sent_cu < this.cu_num; sent_cu++)
			for (int recv_cu = sent_cu + 1; recv_cu < this.cu_num; recv_cu++) {
				if (sent_cu == recv_cu)
					continue;

				DTNHost sent_cu_host = host_list.get(sent_cu + this.du_num);
				CURouter sent_cu_router = (CURouter) sent_cu_host.getRouter();				
				DTNHost recv_cu_host = host_list.get(recv_cu + this.du_num);
				CURouter recv_cu_router = (CURouter) recv_cu_host.getRouter();
				
				getP2_X_Param(sent_cu_router);
				getP2_X_Param(recv_cu_router);
				getP2_Y_Param(sent_cu_router, recv_cu_router);
				getP2_Y_Param(recv_cu_router, sent_cu_router);

				sb.append(sent_cu + " " + recv_cu);
				
				// 调用AMPL-----------P2  固定j,k    max sum(ax+by)------------------------
				String p2_ampl_modFilePath = ".\\src\\routing\\ampl_ModDat\\P2_model.mod";
				String p2_ampl_datFilePath = ".\\src\\routing\\ampl_ModDat\\p2.dat";
				pxy_outTodat(sent_cu_router, recv_cu_router, p2_ampl_datFilePath);
				// Create an AMPL instance
				AMPL ampl_p2 = new AMPL();
				ampl_p2.setOption("solver", "./minos");
				ampl_p2.read(p2_ampl_modFilePath);
				ampl_p2.readData(p2_ampl_datFilePath);
				ampl_p2.solve();
				// 获取返回值
				// 获取x_ij--->x_j[i]
				Variable xij_Var = ampl_p2.getVariable("x_ij");
				Conn_X.put(sent_cu_router, new double[this.du_num]);
				getOneDimensionVarFromAmpl(xij_Var, Conn_X.get(sent_cu_router));
				// 获取x_ik--->x_k[i]
				Variable xik_Var = ampl_p2.getVariable("x_ik");
				Conn_X.put(recv_cu_router, new double[this.du_num]);
				getOneDimensionVarFromAmpl(xik_Var, Conn_X.get(recv_cu_router));
				// 获取y_ijk--->y_jk[i]
				Variable yijk_Var = ampl_p2.getVariable("y_ijk");
				double[] y_jk = new double[this.du_num];
				getOneDimensionVarFromAmpl(yijk_Var, y_jk);
				int duID = 0;
				for(double dd : y_jk) sent_cu_router.CU_Y.get(recv_cu_router.getHost()).put(host_list.get(duID++), dd);
				// 获取y_ikj--->y_kj[i]
				Variable yikj_Var = ampl_p2.getVariable("y_ikj");
				double[] y_kj = new double[this.du_num];
				getOneDimensionVarFromAmpl(yikj_Var, y_kj);
				duID = 0;
				for(double dd : y_kj) recv_cu_router.CU_Y.get(sent_cu_router.getHost()).put(host_list.get(duID++), dd);
				// ------------------------------------------------------------------
				Objective obj = ampl_p2.getObjective("p2_obj");
				 r = obj.value();
				// 需要返回结果给r赋值
				if (r <= 0)
					r = 0;
				sb.append(" " + format(r) + "\n");
			}		
		pw.println(sb.toString());
		pw.close();
		// 调用python的图匹配
		Process proc = null;
		try {
			//String[] args = new String[] {"python3"," F:\\eclipse-workbench\\the-one-master\\src\\routing\\py_Matching\\bipartiteMatch.py", P1_biMatching_FilePath};
			String exe_cmd = "python3  F:\\eclipse-workbench\\the-one-master\\src\\routing\\py_Matching\\bipartiteMatch.py  ";			
			proc = Runtime.getRuntime()
					.exec(exe_cmd.concat(P2_Matching_FilePath));
			// 用输入输出流来截取结果
			BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line = null;
			while ((line = in.readLine()) != null) {
				//System.out.println("----------*************");
				System.out.println(line);
				if(line.equals("set()"))continue;
				line.trim();
				line = line.replace("(", "");
				line = line.replace(")", "");
				line = line.replace("{", "");
				line = line.replace("}", "");
				line = line.replace(" ", "");
				String[] points = line.split(",");
				
				int selfNode, firstNode, secondNode;
				System.out.println(points.length);
				System.out.println("----------*************");
				for (int i = 0; i < points.length; i += 2) {
					if (points[i].contains("m")) {
						selfNode = Integer.valueOf(points[i+1]);
						CURouter cu_router = (CURouter) host_list.get(selfNode+this.du_num).getRouter();
						for(int duID = 0; duID < self_X.get(cu_router).length; duID++)
						{	
							cu_router.CU_X.put(host_list.get(duID), self_X.get(cu_router)[duID]);
						    cu_router.CalculateDUByItself(host_list.get(duID));
						    obj_function += cu_router.CU_Cal_Cost * this.rho * self_X.get(cu_router)[duID];
						}
						
					} 
					if (points[i+1].contains("m")) {
						selfNode = Integer.valueOf(points[i]);
						CURouter cu_router = (CURouter) host_list.get(selfNode+this.du_num).getRouter();
						for(int duID = 0; duID < self_X.get(cu_router).length; duID++)
						{
							cu_router.CU_X.put(host_list.get(duID), self_X.get(cu_router)[duID]);
							cu_router.CalculateDUByItself(host_list.get(duID));
							obj_function += cu_router.CU_Cal_Cost * this.rho * self_X.get(cu_router)[duID];
						}
					}
					else {
						firstNode = Integer.valueOf(points[i]);
						secondNode = Integer.valueOf(points[i+1]);
						CURouter cu_router_first = (CURouter) host_list.get(firstNode+this.du_num).getRouter();
						CURouter cu_router_second = (CURouter) host_list.get(secondNode+this.du_num).getRouter();
						for(int duID = 0; duID < Conn_X.get(cu_router_first).length; duID++)
						{
							DTNHost cu_First_Host = cu_router_first.getHost();
							DTNHost cu_Second_Host = cu_router_second.getHost();
							DTNHost du_Host = host_list.get(duID);
							
							cu_router_first.CU_X.put(du_Host, Conn_X.get(cu_router_first)[duID]);
							cu_router_second.CU_X.put(du_Host, Conn_X.get(cu_router_second)[duID]);
							cu_router_first.CalculateDUByItself(du_Host);
							obj_function += cu_router_first.CU_Cal_Cost * this.rho * Conn_X.get(cu_router_first)[duID];
							
							cu_router_second.CalculateDUByItself(du_Host);
							obj_function += cu_router_second.CU_Cal_Cost * this.rho * Conn_X.get(cu_router_second)[duID];
							
							cu_router_first.SendDataToCU(cu_Second_Host, du_Host);
							obj_function += cu_router_first.CU_CU_Trans_Cost.get(cu_Second_Host) * (cu_router_first.CU_Y.get(cu_Second_Host).get(du_Host));
							
							cu_router_second.SendDataToCU(cu_router_first.getHost(), du_Host);
							obj_function += cu_router_second.CU_CU_Trans_Cost.get(cu_First_Host) * (cu_router_second.CU_Y.get(cu_First_Host).get(du_Host));
					
							cu_router_first.RecvDataFromCU(cu_router_second.getHost(), du_Host);
							obj_function += cu_router_first.CU_Cal_Cost * this.rho * (cu_router_second.CU_Y.get(cu_First_Host).get(du_Host));
							
							cu_router_second.RecvDataFromCU(cu_router_first.getHost(), du_Host);
							obj_function += cu_router_second.CU_Cal_Cost * this.rho * (cu_router_first.CU_Y.get(cu_Second_Host).get(du_Host));
						}
					}
				}
			}
			in.close();
			proc.waitFor();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	//---------------------------------------------------------Get  Edge  Weights-----------------------------------------------
	private void getP2_Y_Param(CURouter sent_cu_router, CURouter recv_cu_router) {
		double para_y = 0;
		for (DTNHost du : sent_cu_router.X_Param.keySet()) {
			para_y = (-1) * recv_cu_router.CU_Cal_Cost * this.rho 
					+ sent_cu_router.eta_QueueLength.get(du)
					- recv_cu_router.lambda_QueueLength.get(du) + recv_cu_router.pi_QueueLength.get(du)
					+ recv_cu_router.GetSum_P2();
			para_y -= sent_cu_router.CU_CU_Trans_Cost.get(recv_cu_router.getHost());
			sent_cu_router.Y_Param.get(recv_cu_router.getHost()).put(du, para_y);
		}
	}

	private void getP2_X_Param(CURouter cu_router) {
		double para_x = 0;
		for (DTNHost du : cu_router.X_Param.keySet()) {
			para_x = (-1) * cu_router.CU_Cal_Cost * this.rho + cu_router.eta_QueueLength.get(du)
					- cu_router.lambda_QueueLength.get(du) + cu_router.pi_QueueLength.get(du) + cu_router.GetSum_P2();
			cu_router.X_Param.put(du, para_x);
		}
	}
	//----------------------------------------------------------------------------------------------------------------------------------
	
	//	----------------------------------------------------------write to XXX.dat----------------------------------------------
	public void px_outTodat(CURouter self_cu_router, String p1_ampl_datFilePath) {
		FileOutputStream out = null;
		OutputStreamWriter osw = null;
		BufferedWriter bw = null;
		try {
			out = new FileOutputStream(new File(p1_ampl_datFilePath));
			osw = new OutputStreamWriter(out);
			bw = new BufferedWriter(osw);
			// 将所需参数写入文件
			bw.append("data;").append("\r");
			AMPL_basicFuc genData = new AMPL_basicFuc();
			genData.outTodatOneNumInt(bw, "du_num", this.du_num);
			genData.outTodatOneNumDouble(bw, "calCap_j", self_cu_router.curCapacity);
			genData.outTodatOneNumInt(bw, "rho", this.rho);
			genData.outTodatOneNumDouble(bw, "epsilon_j", self_cu_router.epsilon);
			double[] x_param = new double[this.du_num];
			for (DTNHost du : self_cu_router.X_Param.keySet()) {
				int du_id = du.getAddress();
				x_param[du_id] = self_cu_router.X_Param.get(du);
				if(x_param[du_id] <= 0) x_param[du_id] = 0;
			}
			genData.outTodatOneDimensionDouble(bw, "co_param", x_param);
			double[] eta_param = new double[this.du_num];
			for (DTNHost du : self_cu_router.eta_QueueLength.keySet()) {
				int du_id = du.getAddress();
				eta_param[du_id] = self_cu_router.eta_QueueLength.get(du);
			}
			genData.outTodatOneDimensionDouble(bw, "eta", eta_param);
			bw.append("end;").append("\r");
//			osw.write(bw.toString());
//			out.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}finally{
			if(bw!=null){
				try {
					bw.close();
					bw=null;
				} catch (IOException e) {
					e.printStackTrace();
				} 
			}
			if(osw!=null){
				try {
					osw.close();
					osw=null;
				} catch (IOException e) {
					e.printStackTrace();
				} 
			}
			if(out!=null){
				try {
					out.close();
					out=null;
				} catch (IOException e) {
					e.printStackTrace();
				} 
			}
		}
	}

	@SuppressWarnings("unlikely-arg-type")
	public void pxy_outTodat(CURouter sent_cu_router, CURouter recv_cu_router, String p2_ampl_datFilePath) {
		FileOutputStream out = null;
		OutputStreamWriter osw = null;
	    BufferedWriter bw = null;	
		try {
			out = new FileOutputStream(new File(p2_ampl_datFilePath));
            osw = new OutputStreamWriter(out);
			bw = new BufferedWriter(osw);
			// 将所需参数写入文件
			bw.append("data;").append("\r");
			AMPL_basicFuc genData = new AMPL_basicFuc();
			genData.outTodatOneNumInt(bw, "du_num", this.du_num);
			genData.outTodatOneNumDouble(bw, "calCap_j", sent_cu_router.curCapacity);
			genData.outTodatOneNumDouble(bw, "calCap_k", recv_cu_router.curCapacity);
			genData.outTodatOneNumInt(bw, "rho", this.rho);
			genData.outTodatOneNumDouble(bw, "epsilon_j", sent_cu_router.epsilon);
			genData.outTodatOneNumDouble(bw, "epsilon_k", recv_cu_router.epsilon);
			Double min_rate = Math.min(sent_cu_router.CU_Rates.get(recv_cu_router.getHost()),
					recv_cu_router.CU_Rates.get(sent_cu_router.getHost()));
			// sent_cu_router.CU_Rates.get(recv_cu_router) >recv_cu_router.CU_Rates.get(sent_cu_router)? sent_cu_router.CU_Rates.get(recv_cu_router):recv_cu_router.CU_Rates.get(sent_cu_router);
			genData.outTodatOneNumDouble(bw, "D_jk", min_rate);
			double[] x_ij_param = new double[this.du_num];
			double[] x_ik_param = new double[this.du_num];
			for (DTNHost du : sent_cu_router.X_Param.keySet()) {
				int du_id = du.getAddress();
				x_ij_param[du_id] = sent_cu_router.X_Param.get(du);
				x_ik_param[du_id] = recv_cu_router.X_Param.get(du);
				if(x_ij_param[du_id] <= 0) x_ij_param[du_id] = 0;
				if(x_ik_param[du_id] <= 0) x_ik_param[du_id] = 0;
			}
			genData.outTodatOneDimensionDouble(bw, "co_xij_param", x_ij_param);
			genData.outTodatOneDimensionDouble(bw, "co_xik_param", x_ik_param);

			double[] y_ijk_param = new double[this.du_num];
			double[] y_ikj_param = new double[this.du_num];
			for (DTNHost du : sent_cu_router.X_Param.keySet()) {
				int du_id = du.getAddress();
				y_ijk_param[du_id] = sent_cu_router.Y_Param.get(recv_cu_router.getHost()).get(du);
				y_ikj_param[du_id] = recv_cu_router.Y_Param.get(sent_cu_router.getHost()).get(du);
				if(y_ijk_param[du_id] <= 0) y_ijk_param[du_id] = 0;
				if(y_ikj_param[du_id] <= 0) y_ikj_param[du_id] = 0;
			}
			genData.outTodatOneDimensionDouble(bw, "co_yijk_param", y_ijk_param);
			genData.outTodatOneDimensionDouble(bw, "co_yikj_param", y_ikj_param);

			double[] eta_j_param = new double[this.du_num];
			double[] eta_k_param = new double[this.du_num];
			for (DTNHost du : sent_cu_router.eta_QueueLength.keySet()) {
				int du_id = du.getAddress();
				eta_j_param[du_id] = sent_cu_router.eta_QueueLength.get(du);
				eta_k_param[du_id] = recv_cu_router.eta_QueueLength.get(du);
			}
			genData.outTodatOneDimensionDouble(bw, "eta_j", eta_j_param);
			genData.outTodatOneDimensionDouble(bw, "eta_k", eta_k_param);

			bw.append("end;").append("\r");
		} catch (Exception e) {
			e.printStackTrace();
		}finally{
			if(bw!=null){
				try {
					bw.close();
					bw=null;
				} catch (IOException e) {
					e.printStackTrace();
				} 
			}
			if(osw!=null){
				try {
					osw.close();
					osw=null;
				} catch (IOException e) {
					e.printStackTrace();
				} 
			}
			if(out!=null){
				try {
					out.close();
					out=null;
				} catch (IOException e) {
					e.printStackTrace();
				} 
			}
		}
	}
	//	----------------------------------------------------------------------------------------------------------------
	
	//------------------------------------------Get Variable From Ampl------------------------------------------
	
	public void getOneDimensionVarFromAmpl(Variable xij_Var, double[] doubleValue) {
		int index_xj = 0;
		for (int i = 0; i < this.du_num; i++) {
			Object[] df = xij_Var.getValues().getRowByIndex(index_xj++);
			double tempD = Double.valueOf(df[1] == null ? "" : df[1].toString()).doubleValue();
			// y[j] = (tempD > 0.5) ? 1 : 0;
			doubleValue[i] = tempD;
		}
	}
	//	--------------------------------------------------------------------------------------------------------
 
	private String format(double value) {
		return String.format("%.2f", value);
	}

}
