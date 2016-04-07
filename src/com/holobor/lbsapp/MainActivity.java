package com.holobor.lbsapp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity implements OnInitListener {
	TextView textInfo, textNotice;
	Button buttonUpload, buttonRefresh, buttonQRCode;
	boolean uploadServiceIsRunning = false;
	String deviceID;
	
	TextToSpeech tts;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		startActivity(new Intent(MainActivity.this, CodeActivity.class));
		
		if (getIntent().getBooleanExtra("boot", false)) {
			onBootComplete();
		}
		
		setContentView(R.layout.activity_main);
		deviceID = ((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).getDeviceId();
		
		textInfo = (TextView) findViewById(R.id.text_info);
		textNotice = (TextView) findViewById(R.id.text_notice);
		buttonUpload = (Button) findViewById(R.id.button_upload);
		buttonUpload.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				//
				onBootComplete();
			}
		});
		
		buttonRefresh = (Button) findViewById(R.id.button_refresh);
		buttonRefresh.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				refreshData();
			}
		});
		
		buttonQRCode = (Button) findViewById(R.id.button_qrcode);
		buttonQRCode.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				startActivity(new Intent(MainActivity.this, CodeActivity.class));
			}
		});
		
		refreshData();
		
		tts = new TextToSpeech(this, this);
	}

	private void refreshData() {
		StringBuffer info = new StringBuffer();
		info.append("当前设备号\t: ");
		info.append(deviceID);
		info.append("\n\n");
		
		ConnectivityManager connectivityManager = (ConnectivityManager) getBaseContext().getSystemService(Context.CONNECTIVITY_SERVICE);
		if (connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).isConnected()) {
			info.append("移动数据\t: 已连接\n");
		} else {
			info.append("移动数据\t: 未连接\n");
		}
		
		if (connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) {
			info.append("无线网络\t: 已连接\n\n");
		} else {
			info.append("无线网络\t: 未连接\n\n");
		}
	
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault());
		
		ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE); 
		List<ActivityManager.RunningServiceInfo> serviceList = activityManager.getRunningServices(64);
		uploadServiceIsRunning = false;
		for (int i = 0; i< serviceList.size(); i++) {
           if (serviceList.get(i).service.getClassName().equals("com.holobor.lbsapp.DataCollectUploadService")) {
               uploadServiceIsRunning = true;
               break;
           }
		}
		if (!uploadServiceIsRunning) {
			textNotice.setText("数据收集上传服务是否运行\t: 否");
			buttonUpload.setText("开启上传服务");
		} else {
			textNotice.setText("数据收集上传服务是否运行\t: 是");
			buttonUpload.setText("关闭上传服务");
		}
		
		SharedPreferences sharedPreferences = getSharedPreferences("info", MODE_PRIVATE);
		info.append("数据上传时隔\t: ");
		info.append(sharedPreferences.getInt("di", -1));
		info.append(" 秒\nLBS采集时隔\t: ");
		info.append(sharedPreferences.getInt("li", -1));
		info.append(" 秒\nGPS采集时隔\t: ");
		info.append(sharedPreferences.getInt("gi", -1));
		info.append(" 秒\nWIFI采集时隔\t: ");
		info.append(sharedPreferences.getInt("wi", -1));
		info.append(" 秒\n上次数据上传时隔: ");
		if (-1 == sharedPreferences.getLong("ut", -1)) {
			info.append("未上传");
		} else {
			info.append(simpleDateFormat.format(new Date(sharedPreferences.getLong("ut", 0) * 1000)));
		}
		
		DatabaseHelper databaseHelper = new DatabaseHelper(this, "db", null, 1);
		SQLiteDatabase database = databaseHelper.getReadableDatabase();
		
		Cursor cursor =  database.rawQuery("select count(*) from LBSData;", null);
		int total = 0, unup = 0;
		if (cursor.moveToNext()) {
			total = cursor.getInt(0);
		} else {
			total = 0;
		}
		
		cursor =  database.rawQuery("select count(*) from LBSData where time > ? ;", new String[] {"" + sharedPreferences.getLong("ut", -1)});
		if (cursor.moveToNext()) {
			unup = cursor.getInt(0);
		} else {
			unup = 0;
		}
		
		info.append("\n\n**数据上传统计(未上传/总计)**");
		info.append("\n\nLBS\t\t: ");
		info.append(unup);
		info.append('/');
		info.append(total);
		
		cursor =  database.rawQuery("select count(*) from GPSData;", null);
		if (cursor.moveToNext()) {
			total = cursor.getInt(0);
		} else {
			total = 0;
		}
		
		cursor =  database.rawQuery("select count(*) from GPSData where time > ? ;", new String[] {"" + sharedPreferences.getLong("ut", -1)});
		if (cursor.moveToNext()) {
			unup = cursor.getInt(0);
		} else {
			unup = 0;
		}
		
		info.append("\nGPS\t\t: ");
		info.append(unup);
		info.append('/');
		info.append(total);

		cursor =  database.rawQuery("select count(*) from WIFIData;", null);
		if (cursor.moveToNext()) {
			total = cursor.getInt(0);
		} else {
			total = 0;
		}
		
		cursor =  database.rawQuery("select count(*) from WIFIData where time > ? ;", new String[] {"" + sharedPreferences.getLong("ut", -1)});
		if (cursor.moveToNext()) {
			unup = cursor.getInt(0);
		} else {
			unup = 0;
		}
		
		info.append("\nWIFI\t\t: ");
		info.append(unup);
		info.append('/');
		info.append(total);
		
		info.append("\n计步\t\t: ");
		info.append(sharedPreferences.getInt("st", 0));
		info.append('/');
		info.append(sharedPreferences.getInt("上次上传到现在累计", 0));
		
		textInfo.setText(info.toString());
		
	}
	
	/**
	 * 启动手机的事件
	 */
	private void onBootComplete() {
		
		/**
		 * 1. 设置闹钟
		 * 2. 设置数据采集Service
		 * 3. 设置数据采集发送的Service
		 * 4. 动态注册拦截短信的receiver
		 */
		
		//注册短信内容监听
		SmsObserver smsObserver = new SmsObserver(getApplicationContext(), new Handler());
		getContentResolver().registerContentObserver(Uri.parse("content://sms/"), true, smsObserver);
		
		//监听电池电量的变化
		IntentFilter filter=new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(new ButteryChangedReceiver(), filter);
		
		AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		PendingIntent lbsIntent = PendingIntent.getService(getApplicationContext(), NetConfig.LBS_COLLECT, new Intent(getApplicationContext(), LBSCollectUploadService.class), PendingIntent.FLAG_CANCEL_CURRENT);
		//
//		alarmManager.setExact(
//				AlarmManager.RTC_WAKEUP, 
//				0,
//				lbsIntent);
		//alarmManager.cancel(lbsIntent);
		alarmManager.setRepeating(
				AlarmManager.RTC_WAKEUP, 
				0, 
				1000l * getSharedPreferences("info", Context.MODE_PRIVATE).getInt("li", 0), 
				lbsIntent);
		
		PendingIntent hrIntent = PendingIntent.getService(getApplicationContext(), NetConfig.HR_COLLECT, new Intent(getApplicationContext(), HeartRateService.class), PendingIntent.FLAG_CANCEL_CURRENT);
//		alarmManager.setRepeating(
//				AlarmManager.RTC_WAKEUP, 
//				0, 
//				30 * 60000l,
//				hrIntent);

		alarmManager.cancel(hrIntent);
		//if (1 == getSharedPreferences("info", Context.MODE_PRIVATE).getInt("stepCount", 0)) {
		/*
			SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
			SensorEventListener sensorEventListener = new SensorEventListener() {
				
				@Override
				public void onSensorChanged(SensorEvent event) {}
				
				@Override
				public void onAccuracyChanged(Sensor sensor, int accuracy) {}
			};
			
	        Sensor stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
	        sensorManager.registerListener(sensorEventListener, stepSensor, Sensor.TYPE_STEP_COUNTER, SensorManager.SENSOR_DELAY_UI);
	    */
		//}

		Intent gpsIntent = new Intent(getApplicationContext(), GPSService.class);
		PendingIntent pendingGPSIntent = PendingIntent.getService(getApplicationContext(), NetConfig.GPS_COLLECT, gpsIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		int gpsInterval = getSharedPreferences("info", Context.MODE_PRIVATE).getInt("gi", 0) < 300 ? 300 : getSharedPreferences("info", Context.MODE_PRIVATE).getInt("gi", 0);
		alarmManager.setRepeating(
				AlarmManager.RTC_WAKEUP, 
				0, 
				1000l * gpsInterval, 
				pendingGPSIntent);
	}

	@Override
	public void onBackPressed() {
		
	}

	@Override
	public void onInit(int status) {
		if (TextToSpeech.SUCCESS == status) {
			tts.setLanguage(Locale.CHINA);
			tts.speak("测试", TextToSpeech.QUEUE_ADD, null);
		}
	}
}
