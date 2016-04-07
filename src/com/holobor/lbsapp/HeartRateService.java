package com.holobor.lbsapp;

import com.jx.heart.HRManager;
import com.jx.heart.HeartRateListener;

import android.app.Service;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.IBinder;
import android.os.RemoteException;

/**
 * 用于心率采集的服务
 * @author Holobor
 *
 */
public class HeartRateService extends Service {

	private HRManager mHRManager;
	private boolean isClosed = false;
	
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		
		mHRManager = HRManager.getInstance(this);
		mHRManager.setOnHeartRateListener(new HeartRateListener() {
			
			@Override
			public void onHeartRateChange(int rate) {
				try {
					mHRManager.closeHR();
					isClosed = true;
				} catch (RemoteException e) {
					e.printStackTrace();
				}

				DatabaseHelper databaseHelper = new DatabaseHelper(getBaseContext(), "db", null, 1);
				SQLiteDatabase sqLiteDatabase = databaseHelper.getWritableDatabase();
				sqLiteDatabase.execSQL("insert into HRData (time, data) values (" + System.currentTimeMillis() / 1000 + ", " + rate + "); ");
				sqLiteDatabase.close();
				databaseHelper.close();
				stopSelf();
			}
		});

		try {
			mHRManager.openHR();
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				try {
					//24秒之后没有检测到心率就关闭
					Thread.sleep(24000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				} finally {
					if (!isClosed) {
						try {
							mHRManager.closeHR();
							stopSelf();
						} catch (RemoteException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}).start();
	}
	
}
