package apps.jan.rendezvous;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Loader;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class facilitates a contacts interface for
 * the application that is compatible with the main
 * thread.
 */
public class ContactsFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor>,
        AdapterView.OnItemClickListener {

    private Typeface roboto;

    /*
     * Contains column names to move from the Cursor to the
     * ListView.
     */
    @SuppressLint("InlinedApi")
    private final static String[] FROM_COLUMNS = {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ?
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY :
                    ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
    };

    /*
     * Contains resource ids for the layout views
     * that get the Cursor column contents.
     */
    private final static int[] TO_IDS = {
            android.R.id.text1,
            android.R.id.text2
    };

    ListView mContactsList;
    long mContactId;
    String mContactKey;
    Uri mContactUri;
    private SimpleCursorAdapter mCursorAdapter;

    public ContactsFragment(){}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ListView lv = ( ListView )inflater.inflate(R.layout.contacts_list_layout, container, false);
        mContactsList = ( ListView )lv.findViewById(R.id.contact_list);
        return lv;
    }

    @Override
    public void onActivityCreated (Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getLoaderManager().initLoader(0,null,this);
        roboto = Typeface.createFromAsset(getActivity().getAssets(),
                "Roboto-Light.ttf");
        mCursorAdapter = new SimpleCursorAdapter(
                getActivity(),
                R.layout.contacts_list_item,
                null,
                FROM_COLUMNS, TO_IDS,
                0);
        mContactsList.setAdapter(mCursorAdapter);
        mContactsList.setOnItemClickListener(this);
    }

    private static final String SELECTION =
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ?
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " LIKE ?" :
                    ContactsContract.Contacts.DISPLAY_NAME + " LIKE ?") +
                    " AND " + ContactsContract.Contacts.HAS_PHONE_NUMBER + " == '1'";

    @SuppressLint("InlinedApi")
    private static final String[] PROJECTION = {
            ContactsContract.Contacts._ID,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ?
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY :
                    ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
    };

    private String mSearchString;
    private String[] mSelectionArgs = {mSearchString};
    private static final int CONTACT_NAME_INDEX = 1;
    private static final int CONTACT_PHONE_INDEX = 2;

    @Override
    public void onItemClick(final AdapterView<?> parent, View item, final int position, long rowID) {
        LinearLayout selected = ( LinearLayout ) item;
        LinearLayout btnGroup = ( LinearLayout )item.findViewById(R.id.add_selected);
        TextView addToExisting = ( TextView )item.findViewById(R.id.add_to_existing);
        TextView addToNew = ( TextView )item.findViewById(R.id.add_to_new);
        addToExisting.setTypeface(roboto);
        addToNew.setTypeface(roboto);

        if (btnGroup.isShown()){
            btnGroup.setVisibility(View.GONE);
        } else {
            btnGroup.setVisibility(View.VISIBLE);
        }

        Cursor cursor = ((CursorAdapter) parent.getAdapter())
                .getCursor();
        cursor.moveToPosition(position);

        String targetName = cursor.getString(CONTACT_NAME_INDEX);
        String targetPhone = cursor.getString(CONTACT_PHONE_INDEX);

        TelephonyManager phoneManager = ( TelephonyManager )
                getActivity().getApplicationContext()
                        .getSystemService( Context.TELEPHONY_SERVICE );
        String senderPhone = "+" + phoneManager.getLine1Number();
        String finalSenderName = "";
        ArrayList<String> potentialEmailBackups = new ArrayList<String>();

        AccountManager manager = ( AccountManager ) getActivity().getSystemService(Context.ACCOUNT_SERVICE);
        Account[] list = manager.getAccounts();

        for (Account acc : list){
            String[] tempName = acc.name.split("\\s+");
            if (tempName.length > 1){
                if (tempName.length > finalSenderName.split("//s+").length){
                    finalSenderName = acc.name;
                }
            } else if (tempName.length == 1) {
                if (tempName[0].contains("@")){
                    String[] emailComponent = tempName[0].split("@");
                    potentialEmailBackups.add(emailComponent[0]);
                }
            }
        }

        if (finalSenderName.equals("")){
            for (String acc_name : potentialEmailBackups){
                if (acc_name.length() > finalSenderName.length()){
                    finalSenderName = acc_name;
                }
            }
        }

        Log.i("FINAL SENDER INFO", finalSenderName + " " + senderPhone);
        Log.i("FINAL RECEIEVER INFO", targetName + " " + targetPhone);

        final HashMap<String, String> requestAddGroupMap = new HashMap<String,String>();

        requestAddGroupMap.put("senderPhone", senderPhone);
        requestAddGroupMap.put("senderName", finalSenderName);
        requestAddGroupMap.put("targetPhone", targetPhone);
        requestAddGroupMap.put("targetName", targetName);

        addToNew.setOnClickListener(
                new View.OnClickListener(){
                    @Override
                    public void onClick(View arg0) {
                        main.requestAddToGroup(requestAddGroupMap,
                                true,"", main.groupsdb);
                    }
                });

        addToExisting.setOnClickListener(
                new View.OnClickListener(){

                    @Override
                    public void onClick(View arg0) {
                        ArrayList<String> existing_groups = new ArrayList<String>();
                        SQLiteDatabase db = main.groupsdb.getReadableDatabase();
                        Cursor c_ = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
                        c_.moveToFirst();
                        while (!c_.isAfterLast()){
                            if (c_.getString(0).split("_")[0].equals("group")){
                                existing_groups.add(c_.getString(0));
                            }
                            c_.moveToNext();
                        }
                        final String[] items = new String[existing_groups.size()];
                        for (int i = 0; i < items.length; i++){
                            items[i] = existing_groups.get(i);
                        }

                        AlertDialog.Builder chooseGroup =
                                new AlertDialog.Builder(getActivity());
                        chooseGroup.setTitle("Choose a Group");
                        if (items.length == 0){
                            chooseGroup.setMessage("No existing groups!");
                        } else {
                            chooseGroup.setSingleChoiceItems(items, -1,
                                    new DialogInterface.OnClickListener(){
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Log.i("selected:", items[which].split("_")[1]);
                                            main.requestAddToGroup(requestAddGroupMap,
                                                    false, items[which].split("_")[1], main.groupsdb);
                                        }
                                    });
                        }
                        chooseGroup.setNegativeButton("Cancel",
                                new DialogInterface.OnClickListener(){
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                                        int which) {
                                    }
                        }).show();
                    }
                });
    }

    @Override
    public Loader<Cursor> onCreateLoader(int loaderId, Bundle args) {
        mSearchString = "%" + getArguments().getString("search") + "%";
        mSelectionArgs[0] = mSearchString;
        return new CursorLoader(
                getActivity(),
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                PROJECTION,
                SELECTION,
                mSelectionArgs,
                null
        );
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        mCursorAdapter.swapCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> arg0) {
        mCursorAdapter.swapCursor(null);
    }

}
