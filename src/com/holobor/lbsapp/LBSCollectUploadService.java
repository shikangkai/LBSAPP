package com.holobor.lbsapp;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.crypto.Cipher;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

/**
 * 用于上传数据的服务
 * @author Holobor
 * info: gi, li, wi, di, ut
 */
public class LBSCollectUploadService extends Service {

	Socket socket;
	OutputStream outputStream;
	InputStream inputStream;

	WifiManager wifiManager;
	List<ScanResult> scanResults;
	WifiInfo wifiInfo;
	StringBuffer wifiInfosStringBuffer;
	
	TelephonyManager telephonyManager;
	GsmCellLocation gsmCellLocation;
	SignalStrength signalStrength;
	List<NeighboringCellInfo> neighboringCellInfos;
	
	LocationManager locationManager;
	LocationListener locationListener;
	Location location;
	String device;
	
	//是否包含警告信息
	boolean hasWarning = false;
	int warningCode;
	String warningDesc, warningAddr = "未知路段";
	
	SimpleDateFormat simpleDateFormat;
	/*
	SmsObserver smsObserver;
	ThreadObserveReveiver receiver;
	*/
	static final int LBS_FLAG  = -2;
	static final int FINISH_FLAG = -6;
	static final int UPLOAD_FLAG = -5;
	
	int intervalLBS = 0, intervalUpload = 0;
	DatabaseHelper databaseHelper;
	SQLiteDatabase database;
	SharedPreferences sharedPreferences;
	Editor editor;

	public static int SLEEP_MODE_START_TIME = 22;
	public static int SLEEP_MODE_END_TIME = 30;
	
//    SensorManager sensorManager;
//    Sensor stepSensor;
//    SensorEventListener sensorEventListener;
	 
	/**
	 * 数据收集和上传的消息处理类
	 */
	static class DCPHandler extends Handler {
		WeakReference<LBSCollectUploadService> service;
	    
	    //PowerManager powerManager;
	    //PowerManager.WakeLock wakeLock;
		
		public DCPHandler(LBSCollectUploadService dataCollectUploadService) {
			service = new WeakReference<LBSCollectUploadService>(dataCollectUploadService);
			//powerManager = (PowerManager) service.get().getSystemService(POWER_SERVICE);
			//wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "bright");
			
		}
		
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			
			switch (msg.what) {
			
			case LBS_FLAG:
				try {
					//发送LBS数据
					if (null != service.get()) {
						if (0 != msg.arg1) {
							service.get().storageLBSInfo();
							//wakeLock.release();
						} else {
							//当获取间隔小于10秒时，按照正常的流程，只不过数据有延时10秒
							if (service.get().intervalLBS < 10) {
								service.get().storageLBSInfo();
							
							} else {
								Message message = obtainMessage();
								message.arg1 = 1;
								message.what = LBS_FLAG;
								sendMessageDelayed(message, 3000);
							}
							requestUpdateLBS();
						}
						
					}
				} catch (Exception e) {
					service.get().stopSelf();
				}
				break;
				
			case UPLOAD_FLAG:

				if (null != service.get()) {
					new Thread(new Runnable() {
						
						@Override
						public void run() {
							try {
								service.get().sendInfo();
							} catch (Exception e) {
								e.printStackTrace();
								service.get().stopSelf();
							}
						}
					}).start();
				}
				break;
			
			case FINISH_FLAG:
				removeMessages(LBS_FLAG);
				removeMessages(UPLOAD_FLAG);
				service.get().stopSelf();
				break;
				
			default:
				break;
			}
		}

		private void requestUpdateLBS() {
			//wakeLock.acquire();
			GsmCellLocation.requestLocationUpdate();
		}
		
	}
	Handler handler;

	/**
	 * 发送采集的信息，不单独开线程
	 */
	private void sendInfo() throws Exception {
		
		ConnectivityManager connectivityManager = (ConnectivityManager) getBaseContext().getSystemService(Context.CONNECTIVITY_SERVICE);
		if (!connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).isConnected() && 
				!connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) {
			Class cmClass         = connectivityManager.getClass();
	        Class[] argClasses     = new Class[1];
	        argClasses[0]         = boolean.class;
	        
	        try {
	            Method method = cmClass.getMethod("setMobileDataEnabled", argClasses);
	            method.invoke(connectivityManager, true);
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
			handler.sendEmptyMessageDelayed(UPLOAD_FLAG, 5000);	//如果当前处于非连接状态，则5秒以后重新尝试发送数据
			return ;
		}
		
		long ut = System.currentTimeMillis() / 1000;
		long lt = sharedPreferences.getLong("ut", 0);
		//筛选数据库
		if (!database.isOpen()) {
			return ;
		}

		Log.v("tag", "send information at " + simpleDateFormat.format(new Date(System.currentTimeMillis())));
		connectServer();
		
		Cursor cursor = database.rawQuery("select time, data from LBSData where time > ? and time <= ?;", new String[] {"" + lt, "" + ut});
		
		//发送LBS数据
		while (cursor.moveToNext()) {
			
			long lbstime = cursor.getLong(0);
			String lbsinfo = cursor.getString(1);
			byte[] lbsbytes = new byte[lbsinfo.length() + 9];

			lbsbytes[0] = 0x02;											//type
			lbsbytes[1] = (byte) ((lbsinfo.length() + 9) >> 8 & 0xff);	//length
			lbsbytes[2] = (byte) ((lbsinfo.length() + 9) & 0xff);
			lbsbytes[3] = (byte) (lbstime >> 24 & 0xff);				//time
			lbsbytes[4] = (byte) (lbstime >> 16 & 0xff);
			lbsbytes[5] = (byte) (lbstime >> 8 & 0xff);
			lbsbytes[6] = (byte) (lbstime & 0xff);
			lbsbytes[7] = (byte) ((lbsinfo.length() + 2) >> 8 & 0xff);	//length
			lbsbytes[8] = (byte) ((lbsinfo.length() + 2) & 0xff);
			System.arraycopy(lbsinfo.getBytes(), 0, lbsbytes, 9, lbsbytes.length - 9);
			
			byte[] infobytes = new byte[5];
			infobytes[0] = 0x02;										//type
			infobytes[1] = (byte) ((lbsbytes.length + 5) >> 8 & 0xff);	//length
			infobytes[2] = (byte) ((lbsbytes.length + 5) & 0xff);
			infobytes[3] = 0x00;										//seqnum
			infobytes[4] = 0x00;
			
			outputStream.write(infobytes);
			outputStream.write(lbsbytes);
			outputStream.flush();
			//byte[] result = new byte[6];
			//inputStream.read(result);
		}
		cursor.close();

		//发送GPS数据
		Cursor gpsCursor = database.rawQuery("select time, data from GPSData where time > ? and time <= ?;", new String[] {"" + lt, "" + ut});
		
		while (gpsCursor.moveToNext()) {
			long gpstime = gpsCursor.getLong(0);
			String gpsinfo = gpsCursor.getString(1);
			byte[] gpsbytes = new byte[gpsinfo.length() + 9];

			gpsbytes[0] = 0x01;											//type
			gpsbytes[1] = (byte) ((gpsinfo.length() + 9) >> 8 & 0xff);	//length
			gpsbytes[2] = (byte) ((gpsinfo.length() + 9) & 0xff);
			gpsbytes[3] = (byte) (gpstime >> 24 & 0xff);				//time
			gpsbytes[4] = (byte) (gpstime >> 16 & 0xff);
			gpsbytes[5] = (byte) (gpstime >> 8 & 0xff);
			gpsbytes[6] = (byte) (gpstime & 0xff);
			gpsbytes[7] = (byte) ((gpsinfo.length() + 2) >> 8 & 0xff);	//length
			gpsbytes[8] = (byte) ((gpsinfo.length() + 2) & 0xff);
			System.arraycopy(gpsinfo.getBytes(), 0, gpsbytes, 9, gpsbytes.length - 9);
			
			byte[] infobytes = new byte[5];
			infobytes[0] = 0x02;										//type
			infobytes[1] = (byte) ((gpsbytes.length + 5) >> 8 & 0xff);	//length
			infobytes[2] = (byte) ((gpsbytes.length + 5) & 0xff);
			infobytes[3] = 0x00;										//seqnum
			infobytes[4] = 0x00;
			
			outputStream.write(infobytes);
			outputStream.write(gpsbytes);
			outputStream.flush();
			//byte[] result = new byte[6];
			//inputStream.read(result);
		}
		gpsCursor.close();
		
		//发送计步数据 
		
		if (0 != sharedPreferences.getInt("st", 0)) {
			long steptime = System.currentTimeMillis() / 1000;
			String stepinfo = "" + sharedPreferences.getInt("st", 0);
			byte[] stepbytes = new byte[stepinfo.length() + 9];

			stepbytes[0] = 0x07;											//type
			stepbytes[1] = (byte) ((stepinfo.length() + 9) >> 8 & 0xff);	//length
			stepbytes[2] = (byte) ((stepinfo.length() + 9) & 0xff);
			stepbytes[3] = (byte) (steptime >> 24 & 0xff);					//time
			stepbytes[4] = (byte) (steptime >> 16 & 0xff);
			stepbytes[5] = (byte) (steptime >> 8 & 0xff);
			stepbytes[6] = (byte) (steptime & 0xff);
			stepbytes[7] = (byte) ((stepinfo.length() + 2) >> 8 & 0xff);	//length
			stepbytes[8] = (byte) ((stepinfo.length() + 2) & 0xff);
			System.arraycopy(stepinfo.getBytes(), 0, stepbytes, 9, stepbytes.length - 9);
			
			byte[] infobytes = new byte[5];
			infobytes[0] = 0x02;										//type
			infobytes[1] = (byte) ((stepbytes.length + 5) >> 8 & 0xff);	//length
			infobytes[2] = (byte) ((stepbytes.length + 5) & 0xff);
			infobytes[3] = 0x00;										//seqnum
			infobytes[4] = 0x00;
			
			outputStream.write(infobytes);
			outputStream.write(stepbytes);
			outputStream.flush();
			//byte[] result = new byte[6];
			//inputStream.read(result);
		}
		
		/*
		if (hasWarning) {
			long time = System.currentTimeMillis() / 1000;
			String warniginfo = warningDesc + "," + warningAddr;
			
			byte[] infobytes = new byte[8];
			infobytes[0] = 0x03;											//type
			infobytes[1] = (byte) ((warniginfo.length() + 8) >> 8 & 0xff);	//length
			infobytes[2] = (byte) ((warniginfo.length() + 8) & 0xff);
			infobytes[3] = (byte) (time >> 24 & 0xff);						//time
			infobytes[4] = (byte) (time >> 16 & 0xff);
			infobytes[5] = (byte) (time >> 8 & 0xff);
			infobytes[6] = (byte) (time & 0xff);
			infobytes[7] = (byte) warningCode;
			
			outputStream.write(infobytes);
			outputStream.write(warniginfo.getBytes());
			outputStream.flush();
			byte[] result = new byte[8];
			inputStream.read(result);
		}
		*/
		
		//发送心率数据
		Cursor hrcursor = database.rawQuery("select time, data from HRData where time > ? and time <= ?;", new String[] {"" + lt, "" + ut});
		while (hrcursor.moveToNext()) {
			long hrtime = hrcursor.getLong(0);
			String hrinfo = "" + hrcursor.getInt(1);
			byte[] hrbytes = new byte[hrinfo.length() + 9];
			hrbytes[0] = 0x05;											//type
			hrbytes[1] = (byte) ((hrinfo.length() + 9) >> 8 & 0xff);	//length
			hrbytes[2] = (byte) ((hrinfo.length() + 9) & 0xff);
			hrbytes[3] = (byte) (hrtime >> 24 & 0xff);					//time
			hrbytes[4] = (byte) (hrtime >> 16 & 0xff);
			hrbytes[5] = (byte) (hrtime >> 8 & 0xff);
			hrbytes[6] = (byte) (hrtime & 0xff);
			hrbytes[7] = (byte) ((hrinfo.length() + 2) >> 8 & 0xff);	//length
			hrbytes[8] = (byte) ((hrinfo.length() + 2) & 0xff);
			System.arraycopy(hrinfo.getBytes(), 0, hrbytes, 9, hrbytes.length - 9);
			
			byte[] infobytes = new byte[5];
			infobytes[0] = 0x02;										//type
			infobytes[1] = (byte) ((hrbytes.length + 5) >> 8 & 0xff);	//length
			infobytes[2] = (byte) ((hrbytes.length + 5) & 0xff);
			infobytes[3] = 0x00;										//seqnum
			infobytes[4] = 0x00;
			
			outputStream.write(infobytes);
			outputStream.write(hrbytes);
			outputStream.flush();
			//byte[] result = new byte[6];
			//inputStream.read(result);
		}
		hrcursor.close();
		
		socket.close();
		editor.putLong("ut", ut);
		editor.putInt("st", 0);
		editor.commit();
		
		handler.sendEmptyMessage(FINISH_FLAG);
		stopSelf();
	
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	/**
	 * 存储收集到的基站数据信息
	 */
	protected void storageLBSInfo() throws Exception {
		Log.v("tag", "collect lbs info");
		StringBuffer lbsInfo = new StringBuffer();
		if (null == gsmCellLocation) {
			stopSelf();
			return ;
		}
		lbsInfo.append("0,");
		lbsInfo.append(signalStrength.getGsmSignalStrength());
		lbsInfo.append(",0,460,0,0,");
		lbsInfo.append(gsmCellLocation.getCid());
		lbsInfo.append(",0,0,");
		lbsInfo.append(gsmCellLocation.getLac());
		lbsInfo.append(",0");
		
		neighboringCellInfos = telephonyManager.getNeighboringCellInfo();
		for (int i = 0; i < neighboringCellInfos.size(); i++) {
			
			lbsInfo.append(",0,");
			lbsInfo.append(neighboringCellInfos.get(i).getRssi());
			lbsInfo.append(",0,");
			lbsInfo.append(neighboringCellInfos.get(i).getCid());
			lbsInfo.append(",460,0,");
			lbsInfo.append(neighboringCellInfos.get(i).getLac());
		}
		
		if (database.isOpen()) {
			database.execSQL("insert into LBSData (time, data) values (" + (System.currentTimeMillis() / 1000) + ", '" + lbsInfo.toString() + "');");
		}
		
		if (hasWarning || System.currentTimeMillis() / 1000 - sharedPreferences.getLong("ut", 0) > intervalUpload) {

			//if (1 == sharedPreferences.getInt("stepCount", 0)) {
			/*
		        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		        sensorEventListener = new SensorEventListener() {
					
					@Override
					public synchronized void onSensorChanged(SensorEvent event) {
						//(int) event.values[0]表示从开机到现在的步数
						//xml文件中的"stt"变量表示每次获取的步数，如果下次比上次高，则其在计数基础之上取差值，否则直接加上这次获取的计数值
						int step = (int) event.values[0];
						int st = sharedPreferences.getInt("st", 0);
						int stt = sharedPreferences.getInt("stt", 0);
						if (step >= stt) {
							editor.putInt("st", st + step - stt);
						} else {
							editor.putInt("st", st + step);
						}
						editor.putInt("stt", step);
						editor.commit();
						sensorManager.unregisterListener(sensorEventListener);
					}
					
					@Override
					public void onAccuracyChanged(Sensor sensor, int accuracy) {}
					
				};
				
		        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
		        sensorManager.registerListener(sensorEventListener, stepSensor, Sensor.TYPE_STEP_COUNTER, SensorManager.SENSOR_DELAY_UI);
			*/
			//} else {
				
			//}
			
			handler.sendEmptyMessage(UPLOAD_FLAG);	//如果当前处于非连接状态，则5秒以后重新尝试发送数据
		} else {
			stopSelf();
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		
		/*
		//设置重复
		AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		PendingIntent lbsIntent = PendingIntent.getService(getApplicationContext(), NetConfig.LBS_COLLECT, new Intent(getApplicationContext(), LBSCollectUploadService.class), PendingIntent.FLAG_CANCEL_CURRENT);
		
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		Date date;
		
		if (1 == getSharedPreferences("info", Context.MODE_PRIVATE).getInt("sleepMode", 1)) {
			
			
			if ((SLEEP_MODE_END_TIME >= 24 && (calendar.get(Calendar.HOUR_OF_DAY) >= SLEEP_MODE_START_TIME || calendar.get(Calendar.HOUR_OF_DAY) < SLEEP_MODE_END_TIME - 24)) || 
					(SLEEP_MODE_START_TIME < SLEEP_MODE_END_TIME && (calendar.get(Calendar.HOUR_OF_DAY) >= SLEEP_MODE_START_TIME && calendar.get(Calendar.HOUR_OF_DAY) < SLEEP_MODE_END_TIME))) {
				alarmManager.setExact(
						AlarmManager.RTC_WAKEUP, 
						calendar.getTimeInMillis() + 3600000l * (SLEEP_MODE_END_TIME - calendar.get(Calendar.HOUR_OF_DAY)) + 30000,
						lbsIntent);
				date = new Date(calendar.getTimeInMillis() + 3600000l * (SLEEP_MODE_END_TIME - calendar.get(Calendar.HOUR_OF_DAY)) + 30000);
				Log.v("tag", "sleep next alarm : " + date);
			} else {
				alarmManager.setExact(
						AlarmManager.RTC_WAKEUP, 
						System.currentTimeMillis() + 1000l * getSharedPreferences("info", Context.MODE_PRIVATE).getInt("li", 0),
						lbsIntent);
				date = new Date(System.currentTimeMillis() + 1000l * getSharedPreferences("info", Context.MODE_PRIVATE).getInt("li", 0));
				Log.v("tag", "normal next alarm : " + date);
			}
			
		} else {
			alarmManager.setExact(
					AlarmManager.RTC_WAKEUP, 
					System.currentTimeMillis() + 1000l * getSharedPreferences("info", Context.MODE_PRIVATE).getInt("li", 0),
					lbsIntent);
			date = new Date(System.currentTimeMillis() + 1000l * getSharedPreferences("info", Context.MODE_PRIVATE).getInt("li", 0));
			Log.v("tag", "normal next alarm : " + date);
		}
		*/
		Log.v("service", "create function");
		
		try {

			handler = new DCPHandler(this);
			handler.removeMessages(LBS_FLAG);
			
			databaseHelper = new DatabaseHelper(getBaseContext(), "db", null, 1);
			database = databaseHelper.getWritableDatabase();
			sharedPreferences = getBaseContext().getSharedPreferences("info", MODE_PRIVATE);
			editor = sharedPreferences.edit();
			
			wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
			telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
			telephonyManager.listen(new PhoneStateListener() {

				@Override
				public void onCellLocationChanged(CellLocation location) {
					gsmCellLocation = (GsmCellLocation) location;
				}

				@Override
				public void onSignalStrengthsChanged(SignalStrength signalStrength) {
					LBSCollectUploadService.this.signalStrength = signalStrength;
				}
				
			}, PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
			//neighboringCellInfos = new ArrayList<NeighboringCellInfo>();
			device = telephonyManager.getDeviceId();
			locationListener = new LocationListener() {
				
				@Override
				public void onStatusChanged(String provider, int status, Bundle extras) {}
				
				@Override
				public void onProviderEnabled(String provider) {}
				
				@Override
				public void onProviderDisabled(String provider) {}
				
				@Override
				public void onLocationChanged(Location location) {
					LBSCollectUploadService.this.location = location;
					editor = sharedPreferences.edit();
					editor.putFloat("lat", (float) location.getLatitude());
					editor.putFloat("lon", (float) location.getLongitude());
					editor.putLong("gpstime", System.currentTimeMillis() / 1000);
					editor.commit();
				}
			};
			
			locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
			locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 5, locationListener);
			
			/**
			 * 判断是否有配置信息，如果xml文件中没有配置信息，则要向服务器请求
			 */
			intervalLBS = sharedPreferences.getInt("li", 0);
			intervalUpload = sharedPreferences.getInt("di", 0);
			
			simpleDateFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
			
			if (0 != intervalLBS) {
				handler.sendEmptyMessageDelayed(LBS_FLAG, 3000);
			} else {
				stopSelf();
			}
		} catch (Exception e) {
			stopSelf();
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		
		hasWarning = intent.getBooleanExtra("hasWarning", false);
		warningCode = intent.getIntExtra("warningCode", 0);
		warningDesc = null == intent.getStringExtra("warningDesc") ? "" : intent.getStringExtra("warningDesc");
		warningAddr = null == intent.getStringExtra("warningAddr") ? "" : intent.getStringExtra("warningAddr");
		
		return super.onStartCommand(intent, flags, startId);
	}

	/**
	 * 不单独开线程
	 */
	public void connectServer() throws Exception {

		socket = new Socket(NetConfig.SERVER_IP, NetConfig.SERVER_PORT);
		outputStream = socket.getOutputStream();
		inputStream = socket.getInputStream();
		
		/**
		 * 发送注册信息
		 */
		String imei_imsi = device + device;
		byte[] imei_imsiBytes = imei_imsi.getBytes();
		byte[] registerBytes = new byte[imei_imsiBytes.length + 5];
		
		registerBytes[0] = 0x01;										//type
		registerBytes[1] = (byte) (registerBytes.length >> 8 & 0xff);	//length
		registerBytes[2] = (byte) (registerBytes.length & 0xff);
		registerBytes[3] = 0x00;										//seqnum
		registerBytes[4] = 0x00;
		
		for (int i = 0; i < imei_imsiBytes.length; i++) {
			registerBytes[i + 5] = imei_imsiBytes[i];
		}
		outputStream.write(registerBytes);
		outputStream.flush();
		
		byte[] rResult = new byte[4];
		socket.getInputStream().read(rResult);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();

		Log.v("service", "destroy function");
		
		try {
			locationManager.removeUpdates(locationListener);
			database.close();
			databaseHelper.close();
			/*
	        sensorManager.unregisterListener(sensorEventListener, stepSensor);
			getContentResolver().unregisterContentObserver(smsObserver);
			getApplicationContext().unregisterReceiver(receiver);
			*/
			
			/*
			if (null != sensorManager) {
				sensorManager.unregisterListener(sensorEventListener);
			}
			*/
			handler.sendEmptyMessage(FINISH_FLAG);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 将字节型表示的整型数转化为无符号字节所表示的整型数
	 * @param b
	 * @return
	 */
	private int byteToInt(byte b) {
		return b < 0 ? 256 + b : b;
	}
}
