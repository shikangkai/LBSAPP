package com.holobor.lbsapp;

public class BytesInfoHelper {

	/**
	 * 获取位置表示的字节
	 * @param wifi : bssid,ssid,encrypt,level
	 * @param gps  : longitude,latitude,altitude 
	 * @param lbs  : lac,cellid,rxl,lac,cellid,rxl...
	 * @return
	 */
	public static byte[] getLocInfoBytes(String wifi, String gps, String lbs) {

		long time = System.currentTimeMillis() / 1000;
		byte[] wifiBytes = null; byte[] wifiContentBytes = null;
		byte[] gpsBytes  = null; byte[] gpsContentBytes = null;
		byte[] lbsBytes  = null; byte[] lbsContentBytes = null;
		
		if (null != wifi && !"".equals(wifi)) {
			wifiContentBytes = wifi.getBytes();
			wifiBytes = new byte[9];
			
			wifiBytes[0] = 0x0e;												//type
			wifiBytes[1] = (byte) ((wifiContentBytes.length + 9) >> 8 & 0xff);	//length
			wifiBytes[2] = (byte) ((wifiContentBytes.length + 9) & 0xff);
			wifiBytes[3] = (byte) (time >> 24 & 0xff);							//time
			wifiBytes[4] = (byte) (time >> 16 & 0xff);
			wifiBytes[5] = (byte) (time >> 8 & 0xff);
			wifiBytes[6] = (byte) (time & 0xff);
			wifiBytes[7] = (byte) ((wifiContentBytes.length + 2) >> 8 & 0xff);	//length
			wifiBytes[8] = (byte) ((wifiContentBytes.length + 2) & 0xff);
		}
		
		if (null != lbs && !"".equals(lbs)) {
			String[] lbsInfos = lbs.split(",");
			StringBuffer lbsInfoContent = new StringBuffer();
			lbsInfoContent.append("0,");
			lbsInfoContent.append(lbsInfos[2]);
			lbsInfoContent.append(",0,460,0,0,");
			lbsInfoContent.append(lbsInfos[1]);
			lbsInfoContent.append(",0,0,");
			lbsInfoContent.append(lbsInfos[0]);
			lbsInfoContent.append(",0");
			
			for (int i = 1; i < lbsInfos.length / 3; i++) {
				lbsInfoContent.append(",0,");
				lbsInfoContent.append(lbsInfos[3 * i + 2]);
				lbsInfoContent.append(",0,");
				lbsInfoContent.append(lbsInfos[3 * i + 1]);
				lbsInfoContent.append(",460,0,");
				lbsInfoContent.append(lbsInfos[3 * i]);
			}

			lbsContentBytes = lbsInfoContent.toString().getBytes();
			lbsBytes = new byte[9];
			
			lbsBytes[0] = 0x02;													//type
			lbsBytes[1] = (byte) ((lbsContentBytes.length + 9) >> 8 & 0xff);	//length
			lbsBytes[2] = (byte) ((lbsContentBytes.length + 9) & 0xff);
			lbsBytes[3] = (byte) (time >> 24 & 0xff);							//time
			lbsBytes[4] = (byte) (time >> 16 & 0xff);
			lbsBytes[5] = (byte) (time >> 8 & 0xff);
			lbsBytes[6] = (byte) (time & 0xff);
			lbsBytes[7] = (byte) ((lbsContentBytes.length + 2) >> 8 & 0xff);	//length
			lbsBytes[8] = (byte) ((lbsContentBytes.length + 2) & 0xff);
		}
		
		if (null != gps && !"".equals(gps) && !"0,0,0".equals(gps)) {
			gpsContentBytes = gps.getBytes();
			gpsBytes = new byte[9];
			
			gpsBytes[0] = 0x0e;												//type
			gpsBytes[1] = (byte) ((wifiContentBytes.length + 9) >> 8 & 0xff);	//length
			gpsBytes[2] = (byte) ((wifiContentBytes.length + 9) & 0xff);
			gpsBytes[3] = (byte) (time >> 24 & 0xff);							//time
			gpsBytes[4] = (byte) (time >> 16 & 0xff);
			gpsBytes[5] = (byte) (time >> 8 & 0xff);
			gpsBytes[6] = (byte) (time & 0xff);
			gpsBytes[7] = (byte) ((gpsContentBytes.length + 2) >> 8 & 0xff);	//length
			gpsBytes[8] = (byte) ((gpsContentBytes.length + 2) & 0xff);
		}
		
		long length = 5;
		if (null != wifiBytes) {
			length = length + 9 + wifiContentBytes.length;
		}
		if (null != lbsBytes) {
			length = length + 9 + lbsContentBytes.length;
		}
		
		if (null != gps && !"0,0,0".equals(gps)) {
			//添加GPS数据发送
		}
		
		byte[] sendInfoBytes = new byte[(int) length];
		
		sendInfoBytes[0] = 0x02;							//type
		sendInfoBytes[1] = (byte) (length >> 8 & 0xff);		//length
		sendInfoBytes[2] = (byte) (length & 0xff);
		sendInfoBytes[3] = 0x00;							//seqnum
		sendInfoBytes[4] = 0x00;
		
		int offset = 5;
		if (null != wifiBytes) {
			System.arraycopy(wifiBytes, 0, sendInfoBytes, offset, wifiBytes.length);
			offset += 9;
			System.arraycopy(wifiContentBytes, 0, sendInfoBytes, offset, wifiContentBytes.length);
			offset += wifiContentBytes.length;
		}
		
		if (null != lbsBytes) {
			System.arraycopy(lbsBytes, 0, sendInfoBytes, offset, lbsBytes.length);
			offset += 9;
			System.arraycopy(lbsContentBytes, 0, sendInfoBytes, offset, lbsContentBytes.length);
			offset += lbsContentBytes.length;
		}
		return sendInfoBytes;
	}
}
