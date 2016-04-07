package com.holobor.lbsapp;

/**
 * 网络操作的配置信息类
 * @author Holobor
 *
 */
public class NetConfig {

	/**
	 * 访问服务器的链接基地址
	 */
	//public static String URL = "http://114.251.56.233:8900/";
	//Socket socket = new Socket("202.122.39.44", 7979);
	public static String SERVER_IP = "114.251.56.233";
	public static int SERVER_PORT = 8600;
	/**
	 * 访问网络的超时时间，单位为毫秒
	 */
	public static int TIMEOUT = 2000;
	/**
	 * 网络操作的错误代码，用于Handler的Message.what的判断用
	 */
	public static int ERROR_CODE = Integer.MIN_VALUE;
	
	//public static int SLEEP_MODE = 1;
	public static int LBS_COLLECT = 0;
	public static int HR_COLLECT = 0;
	public static int GPS_COLLECT = 0;
}
