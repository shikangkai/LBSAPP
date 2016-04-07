package com.holobor.lbsapp;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.Socket;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Handler;

public class SmsObserver extends ContentObserver{

	Context context;
	
	public SmsObserver(Context context, Handler handler) {
		super(handler);
		this.context = context;
	}
	@Override
	public void onChange(boolean selfChange) {
		Uri uri = Uri.parse("content://sms/inbox");
        Cursor c = context.getContentResolver().query(uri, null, null, null, "date desc");
        if (c != null) {
            while (c.moveToNext()) {
            	if (0 == c.getInt(c.getColumnIndex("read"))) {
                    //未读信息
            		parseSMS(c.getString(c.getColumnIndex("address")), c.getString(c.getColumnIndex("body")));
            	} else {
            		//已读消息
            	}
                break;
            }
            c.close();
        }
		
		super.onChange(selfChange);
	}

	/**
	 * 解析短信
	 * @param address 来信号码
	 * @param body 短信内容
	 */
	private void parseSMS(String address, String body) {
		System.out.println("来信号码：" + address + "\n短信内容：" + body);
		body = body.replace("【众行智能】您的验证码是", "");
		/**
		 * 01	发送实时数据
		 * 02	建立发送语音的长连接
		 * 03	更改配置信息
		 * 04	发送闹钟信息
		 */
		switch (body.charAt(1)) {
		case '1': {
			/**
			 * 通过DataCollectService获取最新数据
			 */
			final String deviceId = body.substring(2, 17);
			final String toDeviceId = body.substring(17);
			/**
			 * 1. 采集最新数据
			 * 2. 发送
			 * 3. 发送完成后发送：发送实时数据的请求
			 * 4. 服务器根据deviceID转发实时数据给相应的设备
			 */
			try {
				Thread thread = new Thread(new Runnable() {
					
					@Override
					public void run() {
						try {
							RealtimeInfoGetter realtimeInfoGetter = new RealtimeInfoGetter(context);
							Thread.sleep(3000);
							String lbsinfo = realtimeInfoGetter.getRealtimeInfo().get("lbs").toString();
							
							ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
							if (!connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).isConnected() && 
									!connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) {
								Class cmClass         = connectivityManager.getClass();
						        Class[] argClasses     = new Class[1];
						        argClasses[0]         = boolean.class;

					            Method method = cmClass.getMethod("setMobileDataEnabled", argClasses);
					            method.invoke(connectivityManager, true);
								Thread.sleep(3000);
							}
							
							/**
							 * 连接服务器
							 */
							Socket socket = new Socket(NetConfig.SERVER_IP, NetConfig.SERVER_PORT);
							OutputStream outputStream = socket.getOutputStream();
							InputStream inputStream = socket.getInputStream();
							
							/**
							 * 发送注册信息
							 */
							String imei_imsi = deviceId + deviceId;
							String to_device_id = toDeviceId;
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
							
							/**
							 * 发送基站信息
							 */
							byte[] lbsbytes = new byte[lbsinfo.length() + 9];
							long lbstime = System.currentTimeMillis() / 1000;

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
							byte[] result = new byte[6];
							inputStream.read(result);
							
							outputStream.write(new byte[] {0, 0, (byte) (to_device_id.length() + 6), 0, 0, 1});
							outputStream.write(to_device_id.getBytes());
							outputStream.flush();
							outputStream.close();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				});
				thread.start();
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		} break;
			
		case '2': {
			/**
			 * x. 启动语音聊天界面（建立连接等）
			 */
		} break;
		
		case '3': {
			/**
			 * 1. 更改xml文件中li，gi，wi，di的值
			 * 2. 重启DataCollect服务
			 */
			
			String subSMS = body.substring(17);
			String[] subSMSContents = subSMS.split(",");
			SharedPreferences sharedPreferences = context.getSharedPreferences("info", Context.MODE_PRIVATE);
			SharedPreferences.Editor editor = sharedPreferences.edit();

			if (sharedPreferences.getInt("gi", 0) == Integer.parseInt(subSMSContents[0]) &&
					sharedPreferences.getInt("li", 0) == Integer.parseInt(subSMSContents[1]) &&
					sharedPreferences.getInt("wi", 0) == Integer.parseInt(subSMSContents[2]) &&
					sharedPreferences.getInt("di", 0) == Integer.parseInt(subSMSContents[3])) {
				return ;
			}
			editor.putInt("gi", Integer.parseInt(subSMSContents[0]));
			editor.putInt("li", Integer.parseInt(subSMSContents[1]));
			editor.putInt("wi", Integer.parseInt(subSMSContents[2]));
			editor.putInt("di", Integer.parseInt(subSMSContents[3]));
			editor.commit();
			
			/*
			ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE); 
			List<ActivityManager.RunningServiceInfo> serviceList = activityManager.getRunningServices(64);
			for (int i = 0; i< serviceList.size(); i++) {
	           if (serviceList.get(i).service.getClassName().equals("com.holobor.lbsapp.DataCollectUploadService") == true) {
	               Intent intent = new Intent();
	               intent.setComponent(serviceList.get(i).service);
	        	   context.getApplicationContext().stopService(intent);
	               break;
	           }
			}
			context.getApplicationContext().startService(new Intent(context.getApplicationContext(), LBSCollectUploadService.class));
			*/
			AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
			Intent intent = new Intent(context.getApplicationContext(), LBSCollectUploadService.class);
			PendingIntent pendingIntent = PendingIntent.getService(context.getApplicationContext(), NetConfig.LBS_COLLECT, intent, PendingIntent.FLAG_CANCEL_CURRENT);
			//
//			alarmManager.setExact(
//					AlarmManager.RTC_WAKEUP, 
//					System.currentTimeMillis() + 5000, 
//					pendingIntent);
			//alarmManager.cancel(pendingIntent);
			alarmManager.setRepeating(
					AlarmManager.RTC_WAKEUP, 
					0, 
					1000l * Integer.parseInt(subSMSContents[1]), 
					pendingIntent);
			
			Intent gpsIntent = new Intent(context.getApplicationContext(), GPSService.class);
			PendingIntent pendingGPSIntent = PendingIntent.getService(context.getApplicationContext(), NetConfig.GPS_COLLECT, gpsIntent, PendingIntent.FLAG_CANCEL_CURRENT);
			int gpsInterval = Integer.parseInt(subSMSContents[0]) < 300 ? 300 : Integer.parseInt(subSMSContents[0]);
			alarmManager.setRepeating(
					AlarmManager.RTC_WAKEUP, 
					0, 
					1000l * gpsInterval, 
					pendingGPSIntent);
					
		} break;

		case '4':
			/**
			 * x1. 停止闹钟
			 * x2. 开启闹钟
			 */
			break;
		
		//是否计步
		case '5': {
			SharedPreferences.Editor editor = context.getSharedPreferences("info", Context.MODE_PRIVATE).edit();
			if ('0' == body.charAt(body.length() - 1)) {
				editor.putInt("stepCount", 0);
			} else {
				editor.putInt("stepCount", 1);
			}
			editor.commit();
		} break;
		
		//是否休眠
		case '6': {
//			SharedPreferences sharedPreferences = context.getSharedPreferences("info", Context.MODE_PRIVATE);
//			SharedPreferences.Editor editor = sharedPreferences.edit();
//			int opt;
//			if ('0' == body.charAt(body.length() - 1)) {
//				editor.putInt("sleepMode", 0);
//				opt = 0;
//			} else {
//				editor.putInt("sleepMode", 1);
//				opt = 1;
//			}
//			
//			if (opt != sharedPreferences.getInt("sleepMode", 0)) {
//				AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
//				PendingIntent lbsIntent = PendingIntent.getService(context.getApplicationContext(), NetConfig.LBS_COLLECT, new Intent(context.getApplicationContext(), LBSCollectUploadService.class), PendingIntent.FLAG_CANCEL_CURRENT);
//
//				alarmManager.cancel(lbsIntent);
//				if (1 == opt) {
//
//					
//					Calendar calendar = Calendar.getInstance();
//					calendar.set(Calendar.MINUTE, 0);
//					calendar.set(Calendar.SECOND, 0);
//					
//					if (calendar.get(Calendar.HOUR_OF_DAY) >= LBSCollectUploadService.SLEEP_MODE_START_TIME && calendar.get(Calendar.HOUR_OF_DAY) < LBSCollectUploadService.SLEEP_MODE_END_TIME) {
//						alarmManager.setExact(
//								AlarmManager.RTC_WAKEUP, 
//								calendar.getTimeInMillis() + 3600000l * (LBSCollectUploadService.SLEEP_MODE_END_TIME - calendar.get(Calendar.HOUR_OF_DAY)) + 30000,
//								lbsIntent);
//					} else {
//						alarmManager.setExact(
//								AlarmManager.RTC_WAKEUP, 
//								System.currentTimeMillis(),
//								lbsIntent);
//					}
//					
//				} else {
//					alarmManager.cancel(lbsIntent);
//					alarmManager.setExact(
//							AlarmManager.RTC_WAKEUP, 
//							System.currentTimeMillis(),
//							lbsIntent);
//				}
//			}
//			
//			editor.commit();
			
		} break;
		
		default:
			break;
		}
	}
}
