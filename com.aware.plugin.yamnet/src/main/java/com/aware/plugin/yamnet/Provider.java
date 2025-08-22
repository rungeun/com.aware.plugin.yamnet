package com.aware.plugin.yamnet;

import android.annotation.SuppressLint;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

import com.aware.Aware;
import com.aware.utils.DatabaseHelper;

import java.util.HashMap;
import java.util.Objects;

public class Provider extends ContentProvider {

	public static final int DATABASE_VERSION = 3; // 버전 증가 (컬럼 추가)

	/**
	 * Provider authority: com.aware.plugin.yamnet.provider.yamnet
	 */
	public static String AUTHORITY = "com.aware.plugin.yamnet.provider.yamnet";

	private static final int YAMNET = 1;
	private static final int YAMNET_ID = 2;
	private static final int YAMNET_AUDIO = 3;
	private static final int YAMNET_AUDIO_ID = 4;

	public static final String DATABASE_NAME = "plugin_yamnet.db";

	public static final String[] DATABASE_TABLES = {
			"plugin_yamnet",
			"plugin_yamnet_audio"  // 다시 추가
	};

	public static final String[] TABLES_FIELDS = {
			// 메인 테이블 (동기화됨)
			YAMNet_Data._ID + " integer primary key autoincrement," +
					YAMNet_Data.TIMESTAMP + " real default 0," +
					YAMNet_Data.DEVICE_ID + " text default ''," +
					YAMNet_Data.DURATION + " integer default 0," +
					YAMNet_Data.ANALYSIS_RESULTS + " text",

			// 오디오 테이블 (로컬 전용)
			YAMNet_Audio._ID + " integer primary key autoincrement," +
					YAMNet_Audio.TIMESTAMP + " real default 0," +
					YAMNet_Audio.DEVICE_ID + " text default ''," +
					YAMNet_Audio.DURATION + " integer default 0," +
					YAMNet_Audio.RAW_AUDIO + " blob"
	};

	// 메인 데이터 테이블 (동기화용)
	public static final class YAMNet_Data implements BaseColumns {
		private YAMNet_Data(){};

		public static final Uri CONTENT_URI = Uri.parse("content://"+AUTHORITY+"/plugin_yamnet");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.plugin.yamnet";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.plugin.yamnet";

		public static final String _ID = "_id";
		public static final String TIMESTAMP = "timestamp";
		public static final String DEVICE_ID = "device_id";
		public static final String DURATION = "duration";  // 수집 시간 (밀리초)
		public static final String ANALYSIS_RESULTS = "analysis_results";
	}

	// 오디오 데이터 테이블 (로컬 전용)
	public static final class YAMNet_Audio implements BaseColumns {
		private YAMNet_Audio(){};

		public static final Uri CONTENT_URI = Uri.parse("content://"+AUTHORITY+"/plugin_yamnet_audio");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.plugin.yamnet.audio";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.plugin.yamnet.audio";

		public static final String _ID = "_id";
		public static final String TIMESTAMP = "timestamp";
		public static final String DEVICE_ID = "device_id";
		public static final String DURATION = "duration";  // 수집 시간 (밀리초)
		public static final String RAW_AUDIO = "raw_audio";
	}

	private static UriMatcher URIMatcher;
	private static HashMap<String, String> databaseMap;
	private static HashMap<String, String> audioMap;

	/**
	 * Returns the provider authority that is dynamic
	 * @return
	 */
	public static String getAuthority(Context context) {
		AUTHORITY = context.getPackageName() + ".provider.yamnet";
		return AUTHORITY;
	}

	@SuppressLint("SuspiciousIndentation")
	@Override
	public boolean onCreate() {
		AUTHORITY = Objects.requireNonNull(getContext()).getPackageName() + ".provider.yamnet";

		URIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		URIMatcher.addURI(AUTHORITY, DATABASE_TABLES[0], YAMNET);
		URIMatcher.addURI(AUTHORITY, DATABASE_TABLES[0]+"/#", YAMNET_ID);
		URIMatcher.addURI(AUTHORITY, DATABASE_TABLES[1], YAMNET_AUDIO);
		URIMatcher.addURI(AUTHORITY, DATABASE_TABLES[1]+"/#", YAMNET_AUDIO_ID);

		// 메인 테이블 맵
		databaseMap = new HashMap<>();
		databaseMap.put(YAMNet_Data._ID, YAMNet_Data._ID);
		databaseMap.put(YAMNet_Data.TIMESTAMP, YAMNet_Data.TIMESTAMP);
		databaseMap.put(YAMNet_Data.DEVICE_ID, YAMNet_Data.DEVICE_ID);
		databaseMap.put(YAMNet_Data.DURATION, YAMNet_Data.DURATION);
		databaseMap.put(YAMNet_Data.ANALYSIS_RESULTS, YAMNet_Data.ANALYSIS_RESULTS);

		// 오디오 테이블 맵
		audioMap = new HashMap<>();
		audioMap.put(YAMNet_Audio._ID, YAMNet_Audio._ID);
		audioMap.put(YAMNet_Audio.TIMESTAMP, YAMNet_Audio.TIMESTAMP);
		audioMap.put(YAMNet_Audio.DEVICE_ID, YAMNet_Audio.DEVICE_ID);
		audioMap.put(YAMNet_Audio.DURATION, YAMNet_Audio.DURATION);
		audioMap.put(YAMNet_Audio.RAW_AUDIO, YAMNet_Audio.RAW_AUDIO);

		return true;
	}

	private DatabaseHelper dbHelper;
	private static SQLiteDatabase database;

	private void initialiseDatabase() {
		if (dbHelper == null)
			dbHelper = new DatabaseHelper(getContext(), DATABASE_NAME, null, DATABASE_VERSION, DATABASE_TABLES, TABLES_FIELDS);
		if (database == null)
			database = dbHelper.getWritableDatabase();
	}

	@Override
	public synchronized int delete(Uri uri, String selection, String[] selectionArgs) {
		initialiseDatabase();

		database.beginTransaction();

		int count;
		switch (URIMatcher.match(uri)) {
			case YAMNET:
				count = database.delete(DATABASE_TABLES[0], selection, selectionArgs);
				break;
			case YAMNET_AUDIO:
				count = database.delete(DATABASE_TABLES[1], selection, selectionArgs);
				break;
			default:
				database.endTransaction();
				throw new IllegalArgumentException("Unknown URI " + uri);
		}

		database.setTransactionSuccessful();
		database.endTransaction();

		getContext().getContentResolver().notifyChange(uri, null, false);
		return count;
	}

	@Override
	public String getType(Uri uri) {
		switch (URIMatcher.match(uri)) {
			case YAMNET:
				return YAMNet_Data.CONTENT_TYPE;
			case YAMNET_ID:
				return YAMNet_Data.CONTENT_ITEM_TYPE;
			case YAMNET_AUDIO:
				return YAMNet_Audio.CONTENT_TYPE;
			case YAMNET_AUDIO_ID:
				return YAMNet_Audio.CONTENT_ITEM_TYPE;
			default:
				throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override
	public synchronized Uri insert(Uri uri, ContentValues initialValues) {
		initialiseDatabase();

		ContentValues values = (initialValues != null) ? new ContentValues(
				initialValues) : new ContentValues();

		database.beginTransaction();

		switch (URIMatcher.match(uri)) {
			case YAMNET:
				long yamnet_id = database.insertWithOnConflict(DATABASE_TABLES[0], YAMNet_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);

				if (yamnet_id > 0) {
					Uri new_uri = ContentUris.withAppendedId(
							YAMNet_Data.CONTENT_URI,
							yamnet_id);
					Objects.requireNonNull(getContext()).getContentResolver().notifyChange(new_uri, null, false);
					database.setTransactionSuccessful();
					database.endTransaction();
					return new_uri;
				}
				database.endTransaction();
				throw new SQLException("Failed to insert row into " + uri);

			case YAMNET_AUDIO:
				long audio_id = database.insertWithOnConflict(DATABASE_TABLES[1], YAMNet_Audio.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);

				if (audio_id > 0) {
					Uri new_uri = ContentUris.withAppendedId(
							YAMNet_Audio.CONTENT_URI,
							audio_id);
					Objects.requireNonNull(getContext()).getContentResolver().notifyChange(new_uri, null, false);
					database.setTransactionSuccessful();
					database.endTransaction();
					return new_uri;
				}
				database.endTransaction();
				throw new SQLException("Failed to insert row into " + uri);

			default:
				database.endTransaction();
				throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {

		initialiseDatabase();

		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		switch (URIMatcher.match(uri)) {
			case YAMNET:
				qb.setTables(DATABASE_TABLES[0]);
				qb.setProjectionMap(databaseMap);
				break;
			case YAMNET_AUDIO:
				qb.setTables(DATABASE_TABLES[1]);
				qb.setProjectionMap(audioMap);
				break;
			default:
				throw new IllegalArgumentException("Unknown URI " + uri);
		}
		try {
			Cursor c = qb.query(database, projection, selection, selectionArgs,
					null, null, sortOrder);
			c.setNotificationUri(Objects.requireNonNull(getContext()).getContentResolver(), uri);
			return c;
		} catch (IllegalStateException e) {
			if (Aware.DEBUG)
				Log.e(Aware.TAG, e.getMessage());

			return null;
		}
	}

	@Override
	public synchronized int update(Uri uri, ContentValues values, String selection,
								   String[] selectionArgs) {
		initialiseDatabase();

		database.beginTransaction();

		int count;
		switch (URIMatcher.match(uri)) {
			case YAMNET:
				count = database.update(DATABASE_TABLES[0], values, selection,
						selectionArgs);
				break;
			case YAMNET_AUDIO:
				count = database.update(DATABASE_TABLES[1], values, selection,
						selectionArgs);
				break;
			default:
				database.endTransaction();
				throw new IllegalArgumentException("Unknown URI " + uri);
		}

		database.setTransactionSuccessful();
		database.endTransaction();

		getContext().getContentResolver().notifyChange(uri, null, false);
		return count;
	}
}