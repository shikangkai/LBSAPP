package com.holobor.lbsapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ThreadObserveReveiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intentBoot) {
		
		Intent mainIntent = new Intent(context.getApplicationContext(), MainActivity.class);
		mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		mainIntent.putExtra("boot", true);
		context.startActivity(mainIntent);
	}

}
