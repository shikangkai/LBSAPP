package com.holobor.lbsapp;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * 操作数据库
 * @author Holobor
 *
 */
public class DatabaseHelper extends SQLiteOpenHelper {

	public DatabaseHelper(Context context, String name, CursorFactory factory,
			int version) {
		super(context, name, factory, version);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		//创建表存储采集的信息
		db.execSQL("create table LBSData (time bigint, data text);");
		db.execSQL("create table GPSData (time bigint, data text);");
		db.execSQL("create table WIFIData (time bigint, data text);");
		db.execSQL("create table HRData (time bigint, data int);");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		
	}

}
