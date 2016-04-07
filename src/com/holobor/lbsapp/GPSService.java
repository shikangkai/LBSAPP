package com.holobor.lbsapp;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.app.Service;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;

/**
 * 用于采集GPS数据的服务
 * @author Holobor
 *
 */
public class GPSService extends Service {
	
	LocationManager locationManager;
	Location location;
	
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (!GPSIsOPen()) {
            Intent GPSIntent = new Intent();  
            GPSIntent.setClassName("com.android.settings",  
                    "com.android.settings.widget.SettingsAppWidgetProvider");  
            GPSIntent.addCategory("android.intent.category.ALTERNATIVE");  
            GPSIntent.setData(Uri.parse("custom:3"));  
            try {
            	//改变GPS开关状态
                PendingIntent.getBroadcast(this, 0, GPSIntent, 0).send();
            } catch (CanceledException e) {  
                e.printStackTrace();  
            } 
        }
        
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5, new LocationListener() {
			
			@Override
			public void onStatusChanged(String provider, int status, Bundle extras) {}
			
			@Override
			public void onProviderEnabled(String provider) {}
			
			@Override
			public void onProviderDisabled(String provider) {}
			
			@Override
			public void onLocationChanged(Location location) {
				GPSService.this.location = location;
				//关闭GPS
				DatabaseHelper databaseHelper = new DatabaseHelper(GPSService.this, "db", null, 1);
				SQLiteDatabase database = databaseHelper.getWritableDatabase();
				database.execSQL("insert into GPSData (time, data) values (" + (System.currentTimeMillis() / 1000) + ", '" + location.getLatitude() + "," + location.getLongitude() + "');");
				database.close();
				databaseHelper.close();
				
				if (GPSIsOPen()) {
		            Intent GPSIntent = new Intent();  
		            GPSIntent.setClassName("com.android.settings",  
		                    "com.android.settings.widget.SettingsAppWidgetProvider");  
		            GPSIntent.addCategory("android.intent.category.ALTERNATIVE");  
		            GPSIntent.setData(Uri.parse("custom:3"));  
		            try {
		            	//改变GPS开关状态
		                PendingIntent.getBroadcast(GPSService.this, 0, GPSIntent, 0).send();
		            } catch (CanceledException e) {  
		                e.printStackTrace();  
		            } 
		        }
			}
		});
        
        new Thread(new Runnable() {
			
			@Override
			public void run() {
				try {
					Thread.sleep(180000);
					if (null == location) {
						if (GPSIsOPen()) {
				            Intent GPSIntent = new Intent();  
				            GPSIntent.setClassName("com.android.settings",  
				                    "com.android.settings.widget.SettingsAppWidgetProvider");  
				            GPSIntent.addCategory("android.intent.category.ALTERNATIVE");  
				            GPSIntent.setData(Uri.parse("custom:3"));  
				            try {
				            	//改变GPS开关状态
				                PendingIntent.getBroadcast(GPSService.this, 0, GPSIntent, 0).send();
				            } catch (CanceledException e) {  
				                e.printStackTrace();  
				            } 
				        }
						stopSelf();
					}
				} catch (Exception e) {
					//
				}
			}
		}).start();
	}
	
	 /** 
     * 判断GPS是否开启
     * @param context 
     * @return true 表示开启 
     */
	public boolean GPSIsOPen() {  
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }
}
