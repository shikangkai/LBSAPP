package com.holobor.lbsapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 监听电池电量变化的类
 * @author Holobor
 *
 */
public class ButteryChangedReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		
		if (intent.getIntExtra("level", 0) / (float) intent.getIntExtra("scale", 100) < 0.05) {
			//启动界面，发送数据
			Intent serviceIntent = new Intent(context.getApplicationContext(), LBSCollectUploadService.class);
			serviceIntent.putExtra("hasWarning", true);
			serviceIntent.putExtra("warningCode", 2);
			serviceIntent.putExtra("warningDesc", "手表电池电量低于5%");
			serviceIntent.putExtra("warningAddr", "未知路段");
			
			context.startService(serviceIntent);
			return ;
		}
		
		if (intent.getIntExtra("level", 0) / (float) intent.getIntExtra("scale", 100) < 0.15) {
			//启动界面，发送数据
			Intent serviceIntent = new Intent(context.getApplicationContext(), LBSCollectUploadService.class);
			serviceIntent.putExtra("hasWarning", true);
			serviceIntent.putExtra("warningCode", 2);
			serviceIntent.putExtra("warningDesc", "手表电池电量低于15%");
			serviceIntent.putExtra("warningAddr", "未知路段");
			
			context.startService(serviceIntent);
		}
	}

}
