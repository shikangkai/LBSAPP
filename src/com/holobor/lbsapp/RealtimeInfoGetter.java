package com.holobor.lbsapp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.content.Context;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;

/**
 * 用于获取实时信息的类
 * @author Holobor
 *
 */
public class RealtimeInfoGetter {

	TelephonyManager telephonyManager;
	GsmCellLocation gsmCellLocation;
	SignalStrength signalStrength;
	
	public RealtimeInfoGetter(Context context) {
		telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		telephonyManager.listen(new PhoneStateListener() {

			@Override
			public void onCellLocationChanged(CellLocation location) {
				gsmCellLocation = (GsmCellLocation) location;
				super.onCellLocationChanged(location);
			}

			@Override
			public void onSignalStrengthsChanged(SignalStrength signalStrength) {
				RealtimeInfoGetter.this.signalStrength = signalStrength;
				super.onSignalStrengthsChanged(signalStrength);
			}
			
		}, PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
		CellLocation.requestLocationUpdate();
	}
	
	/**
	 * 获取实时信息
	 * @return
	 */
	public Map<String, Object> getRealtimeInfo() {
		
		Map<String, Object> map = new HashMap<String, Object>();
		StringBuffer lbsInfo = new StringBuffer();
		if (null == gsmCellLocation) {
			
			map.put("lbs", lbsInfo.toString());
			return map;
		}
		
		lbsInfo.append("0,");
		lbsInfo.append(signalStrength.getGsmSignalStrength());
		lbsInfo.append(",0,460,0,0,");
		lbsInfo.append(gsmCellLocation.getCid());
		lbsInfo.append(",0,0,");
		lbsInfo.append(gsmCellLocation.getLac());
		lbsInfo.append(",0");
		
		List<NeighboringCellInfo> neighboringCellInfos = telephonyManager.getNeighboringCellInfo();
		for (int i = 0; i < neighboringCellInfos.size(); i++) {
			
			lbsInfo.append(",0,");
			lbsInfo.append(neighboringCellInfos.get(i).getRssi());
			lbsInfo.append(",0,");
			lbsInfo.append(neighboringCellInfos.get(i).getCid());
			lbsInfo.append(",460,0,");
			lbsInfo.append(neighboringCellInfos.get(i).getLac());
		}
		
		map.put("lbs", lbsInfo.toString());
		return map;
	}
}