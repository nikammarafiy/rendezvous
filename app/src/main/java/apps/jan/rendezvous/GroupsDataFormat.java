package apps.jan.rendezvous;

import android.provider.BaseColumns;

/**
 * This is a class for specifying the format of
 * sql queries that we want to run on the local
 * sqlite master.
 */
public class GroupsDataFormat {

    public static int group_number = 1;
    private static final String TEXT_TYPE = " TEXT";

    public static String genTableQuery(String group_num){
        return "CREATE TABLE " + GroupData.TABLE_NAME + group_num +
                " (" + GroupData.COLUMN_PHONE_NUMBER + " TEXT PRIMARY KEY," +
                GroupData.COLUMN_ACCOUNT_NAME + TEXT_TYPE + ", " +
                GroupData.COLUMN_REGID + TEXT_TYPE + ")";
    }

    public static String genDropQuery(int group_num){
        return "DROP TABLE IF EXISTS " + GroupData.TABLE_NAME + group_num;
    }

    public GroupsDataFormat(){}

    /* inner class defining basic table entries */
    public static abstract class GroupData implements BaseColumns {
        public static int ACCOUNT_INDEX = 0;
        public static int PHONE_INDEX = 1;
        public static int REGID_INDEX = 2;

        public static final String TABLE_NAME = "group_";
        public static final String COLUMN_PHONE_NUMBER = "phone_number";
        public static final String COLUMN_ACCOUNT_NAME = "acc_name";
        public static final String COLUMN_REGID = "regid";
    }

}
