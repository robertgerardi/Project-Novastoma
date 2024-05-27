package com.example.testappjava;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

public class DBHandler extends SQLiteOpenHelper {

    // creating a constant variables for our database.
    // below variable is for our database name.
    private static final String DB_NAME = "WasteDatadb";

    // below int is our database version
    private static final int DB_VERSION = 1;


    private static final String TABLE_NAME = "myWasteData";

    private static final String ID_COL = "id";


    private static final String YEAR_COL = "Year";


    private static final String MONTH_COL = "Month";


    private static final String DAY_COL = "Day";


    private static final String HOUR_COL = "Hour";


    private static final String MINUTE_COL = "Minute";
    private static final String DP_COL = "WasteLevel";

    // creating a constructor for our database handler.
    public DBHandler(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    // below method is for creating a database by running a sqlite query
    @Override
    public void onCreate(SQLiteDatabase db) {
        // on below line we are creating
        // an sqlite query and we are
        // setting our column names
        // along with their data types.
        String query = "CREATE TABLE " + TABLE_NAME + " ("
                + ID_COL + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + YEAR_COL + " INT, "
                + MONTH_COL + " INT,"
                + DAY_COL + " INT,"
                + HOUR_COL + " INT,"
                + MINUTE_COL + " INT,"
                + DP_COL + " INT)";

        // at last we are calling a exec sql
        // method to execute above sql query
        db.execSQL(query);
    }

    // this method is use to add new course to our sqlite database.
    public void addNewDataPoint(int Year, int Month, int Day, int Hour, int Minute, int DP) {

        // on below line we are creating a variable for
        // our sqlite database and calling writable method
        // as we are writing data in our database.
        SQLiteDatabase db = this.getWritableDatabase();

        // on below line we are creating a
        // variable for content values.
        ContentValues values = new ContentValues();

        // on below line we are passing all values
        // along with its key and value pair.
        values.put(YEAR_COL,Year);
        values.put(MONTH_COL,Month);
        values.put(DAY_COL,Day);
        values.put(HOUR_COL,Hour);
        values.put(MINUTE_COL,Minute);
        values.put(DP_COL,DP);


        // after adding all values we are passing
        // content values to our table.
        db.insert(TABLE_NAME, null, values);

        // at last we are closing our
        // database after adding database.
        db.close();
    }
    public ArrayList<dataPoint> readDataPoints1Day(){
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursorPoint
                = db.rawQuery("SELECT * FROM " + TABLE_NAME + " ORDER BY ID DESC ", null);

        ArrayList<dataPoint> DPArrayList
                = new ArrayList<>();


        if (cursorPoint.moveToFirst()) {
            int base = cursorPoint.getInt(3);
            // need to calculate
            do {
                // on below line we are adding the data from
                // cursor to our array list.
                DPArrayList .add(new dataPoint(
                        cursorPoint.getInt(1),
                        cursorPoint.getInt(2),
                        cursorPoint.getInt(3),
                        cursorPoint.getInt(4),
                        cursorPoint.getInt(5),
                        cursorPoint.getInt(6)));

            } while (cursorPoint.moveToNext() && cursorPoint.getInt(3) == base );
            // moving our cursor to next.
        }
        // at last closing our cursor
        // and returning our array list.
        cursorPoint.close();
        return DPArrayList;

    }
    public ArrayList<dataPoint> readDataPoints3Day(){
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursorPoint
                = db.rawQuery("SELECT * FROM " + TABLE_NAME + " ORDER BY ID DESC ", null);

        ArrayList<dataPoint> DPArrayList
                = new ArrayList<>();


        if (cursorPoint.moveToFirst()) {
            int base = cursorPoint.getInt(3);
            int count = 3;
            // need to calculate different base, loop
            do{
                // on below line we are adding the data from
                // cursor to our array list.

                if(base != cursorPoint.getInt(3)){
                    count--;
                    base = cursorPoint.getInt(3);
                }
                if(count == 0){
                    break;
                }
                DPArrayList .add(new dataPoint(
                        cursorPoint.getInt(1),
                        cursorPoint.getInt(2),
                        cursorPoint.getInt(3),
                        cursorPoint.getInt(4),
                        cursorPoint.getInt(5),
                        cursorPoint.getInt(6)));



            }while (cursorPoint.moveToNext());
            // moving our cursor to next.
        }
        // at last closing our cursor
        // and returning our array list.
        cursorPoint.close();
        return DPArrayList;

    }

    public ArrayList<dataPoint> readDataPointsWeek(){
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursorPoint
                = db.rawQuery("SELECT * FROM " + TABLE_NAME + " ORDER BY ID DESC ", null);

        ArrayList<dataPoint> DPArrayList
                = new ArrayList<>();


        if (cursorPoint.moveToFirst()) {
            int base = cursorPoint.getInt(3);
            int count = 7;
            // need to calculate different base, loop
            while (cursorPoint.moveToNext()){
                // on below line we are adding the data from
                // cursor to our array list.

                if(base != cursorPoint.getInt(3)){
                    count--;
                    base = cursorPoint.getInt(3);
                }
                if(count == 0){
                    break;
                }
                DPArrayList .add(new dataPoint(
                        cursorPoint.getInt(1),
                        cursorPoint.getInt(2),
                        cursorPoint.getInt(3),
                        cursorPoint.getInt(4),
                        cursorPoint.getInt(5),
                        cursorPoint.getInt(6)));



            }
        }
        // at last closing our cursor
        // and returning our array list.
        cursorPoint.close();
        return DPArrayList;

    }
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // this method is called to check if the table exists already.
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }
}
