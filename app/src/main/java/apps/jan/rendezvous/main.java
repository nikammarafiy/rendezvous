package apps.jan.rendezvous;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The main client class handles all communication
 * with the third party app server through the GCM
 * connection server. This class also handles interaction
 * with the sqlite master database on the local device.
 */
public class main extends Activity implements
    GooglePlayServicesClient.ConnectionCallbacks,
    GooglePlayServicesClient.OnConnectionFailedListener,
    LocationListener {

    private String[] groupMarkerColors = {

    };

    /* private location retrieval */
    private static LocationClient mLocationClient;
    private LocationRequest mLocationRequest;
    private static float BROWN_LAT = (float)41.8262;
    private static float BROWN_LONG = (float)-71.4032;

    /* roboto typeface */
    protected Typeface roboto;

    /* global members relevant to toggling location */
    protected boolean updatesRequested = false;
    protected Switch mToggleUpdates;

    /* global group display map */
    protected GoogleMap map;
    static MarkerOptions myGlobalPositionMarker = new MarkerOptions().anchor(0.0f, 1.0f).title("Me");
    static Marker myPosition;

    /* navbar menu fragments */
    protected Fragment contactsList;
    protected Fragment groupList;
    protected LinearLayout groups_menu;
    protected LinearLayout search_menu;
    public String groups_fragment_tag = "GROUPS_FRAG";
    public String contacts_fragment_tag = "CONTACTS_FRAG";

    private static final int NAV_BAR_BUTTON_COUNT = 3;
    private Button[] navBarButtons = new Button[NAV_BAR_BUTTON_COUNT];

    /* shared prefs for storing registration */
    protected static SharedPreferences mPrefs;
    protected static SharedPreferences.Editor mEditor;

    /* google cloud messaging */
    public static GoogleCloudMessaging gcm;
    private static AtomicInteger msgId = new AtomicInteger();
    private static String SENDER_ID = "482587758571";

    /* client info */
    protected static Context context;
    protected static String regid;

    /* sqlite local database -- groups storage */
    protected static LocalGroupsDatabase groupsdb;

    /* receive message from gcm intent service */
    protected RequestReceiver receiver = new RequestReceiver();

    /* groupSessionMap : group_id ~> GroupMemberProfile
     * markerMap : group_id ~> (phone_number ~> Marker) */
    protected static HashMap<String, ArrayList<GroupMemberProfile>> groupSessionMap =
            new HashMap<String, ArrayList<GroupMemberProfile>>();
    protected static HashMap<String, HashMap<String, Marker>> markerMap =
            new HashMap<String, HashMap<String, Marker>>();
    protected static String currentOpenGroupId = "";

    /**
     * The RequestReceiver class allows the main
     * activity to update the UI when it receives
     * a message from the GCMIntentService.
     */
    public class RequestReceiver extends BroadcastReceiver {

        RequestReceiver() {
            super();
            Log.i("hello", "MAIN BROADCAST RECEIVER INSTANTIATED");
        }

        public static final String ACTION_RESP =
                "com.example.maps.Main.RESPONSE_RECEIVED";

        @Override
        public void onReceive(Context context, final Intent intent) {
            String unreg_name = intent.getStringExtra("unregistered_name");
            String purpose = intent.getStringExtra("intent_intent");
            if (unreg_name != null){
                if (purpose.equals("user_unregistered")){
                    new AlertDialog.Builder(main.this)
                            .setTitle("Unregisted Contact")
                            .setPositiveButton(R.string.alert_okay,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            // dismiss the window
                                        }
                                    }).setMessage("Woops, looks like " +
                            unreg_name + " is not registered " +
                            "to use meetups.").show();
                } else if (purpose.equals("incomplete_response")) {
                    new AlertDialog.Builder(main.this)
                            .setTitle("Error Getting User Information")
                            .setPositiveButton(R.string.alert_okay,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            // dismiss the window
                                        }
                                    }).setMessage("Woops, there was an error " +
                            "connecting to " + intent.getStringExtra("targetName")).show();
                }
            } else {
                if (purpose.equals("external_add_to_group")) {
                    new AlertDialog.Builder(main.this)
                            .setTitle("Group Request")
                            .setPositiveButton("Accept",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog,
                                                            int which) {
                                            sendAcceptAddToGroup(intent);
                                        }
                                    })
                            .setNegativeButton("Decline",
                                    new DialogInterface.OnClickListener(){
                                        @Override
                                        public void onClick(DialogInterface dialog,
                                                            int which) {
                                        }
                                    }).setMessage("Your contact " + intent.getStringExtra("request_sender") + " wants to add you " +
                            "to a group!").show();
                } else if (purpose.equals("new_user_to_group")){
                    boolean newGroup = intent.getStringExtra("group_type").equals("new");
                    if (newGroup){
                        createGroupTable(intent.getStringExtra("group_id"));
                    }
                    insertUserToGroup(intent.getStringExtra("accepted_phone"),
                            intent.getStringExtra("accepted_name"),
                            intent.getStringExtra("accepted_regid"),
                            intent.getStringExtra("group_id"));
                } else if (purpose.equals("incoming_group_member_location")){
                    String group_id = intent.getStringExtra("unique_group_id");
                    if (currentOpenGroupId.equals(group_id)){
                        if (!markerMap.containsKey(group_id)){
                            HashMap<String, Marker> newMap = new HashMap<String,Marker>();
                            markerMap.put(group_id, newMap);
                        }
                        String toRenderPhone = intent.getStringExtra("sender_phone");
                        Float lat = Float.valueOf(
                                intent.getStringExtra("sender_latitude"));
                        Float longit = Float.valueOf(
                                intent.getStringExtra("sender_longitude"));
                        LatLng target = new LatLng(lat, longit);
                        Log.i("Received Location", "This is the received location " + target);
                        if (markerMap.get(group_id).containsKey(toRenderPhone)){
                            LatLngInterpolator interpolator = new LatLngInterpolator.LinearFixed();
                            MarkerAnimation.animateMarkerToHC(
                                    markerMap.get(group_id).get(toRenderPhone),
                                    target, interpolator);
                        } else {
                            Marker newPosition = map.addMarker(
                                    new MarkerOptions()
                                            .anchor(0.0f, 1.0f)
                                            .position(target));
                            newPosition.setPosition(target);
                            newPosition.setTitle(intent.getStringExtra("sender_name").split("\\s+")[0]);
                            newPosition.setInfoWindowAnchor(0.5f,0.0f);
                            newPosition.showInfoWindow();
                            markerMap.get(group_id)
                                     .put(toRenderPhone, newPosition);
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks if the client has a stable connection
     * to google play services.
     * @return
     */
    private boolean servicesConnected(){
        int resultCode = GooglePlayServicesUtil.
                isGooglePlayServicesAvailable(this);
        if (ConnectionResult.SUCCESS == resultCode){
            Log.d("Location updates", "play services are available");
            return true;
        } else {
            Log.i("Location Updates", "play services are not available");
            return false;
        }
    }

    /**
     * broadCastLocationToGCM is the basic method
     * that send the location of the device to the
     * third party app server.
     */
    public static void broadCastLocationToGCM(){
        new AsyncTask<Void, String, String>(){

            @Override
            protected String doInBackground(Void... params) {
                Location curr_location = null;
                String lat, longit;
                try {
                    curr_location = mLocationClient.getLastLocation();
                    if (curr_location == null){
                        lat = String.valueOf(curr_location.getLatitude());
                        longit = String.valueOf(curr_location.getLongitude());
                    } else {
                        lat = String.valueOf(curr_location.getLatitude());
                        longit = String.valueOf(curr_location.getLongitude());
                    }
                } catch (IllegalStateException e){
                    Log.i("Illegal State Exception", "exception caught in broadcast location");
                }

                if (curr_location == null){
                    lat = String.valueOf(BROWN_LAT);
                    longit = String.valueOf(BROWN_LONG);
                } else {
                    lat = String.valueOf(curr_location.getLatitude());
                    longit = String.valueOf(curr_location.getLongitude());
                }
                try {
                    Bundle data = new Bundle();
                    data.putString("latitude", lat);
                    data.putString("longitude", longit);
                    String id = Integer.toString(msgId.incrementAndGet());
                    gcm.send(SENDER_ID + "@gcm.googleapis.com", id, data);
                } catch (IOException ex) {
                    return "Error:" + ex.getMessage();
                }
                return null;
            }

            @Override
            protected void onPostExecute(String msg){
            }

        }.execute(null,null,null);
    }

    /**
     * requestAddToGroup is the handler for all actions
     * that occur when the client chooses to add a user
     * to a either an existing group, or a new group. This
     * method is called from the ContactsFragment class when
     * the user selects such an action.
     * @param infoBundle - the relevant user information
     * @param newGroup - is it a new group?
     * @param group_ID - the UUID for the group
     * @param groupsDB - the local database interface
     */
    public static void requestAddToGroup(final HashMap<String,String> infoBundle, final boolean newGroup,
                                         final String group_ID, final SQLiteOpenHelper groupsDB){
        AsyncTask<Void, String, String> execute = new AsyncTask<Void, String, String>() {
            @Override
            protected String doInBackground(Void... arg0) {
                String lat = String.valueOf(
                        mLocationClient.getLastLocation().getLatitude());
                String longitude = String.valueOf(
                        mLocationClient.getLastLocation().getLongitude());
                try {
                    Bundle data = new Bundle();

                    if (!newGroup) {
                        StringBuilder group_regids = new StringBuilder();
                        StringBuilder group_names = new StringBuilder();
                        StringBuilder group_phones = new StringBuilder();
                        SQLiteDatabase db = groupsDB.getReadableDatabase();
                        if (isExistingGroup(group_ID, true)) {
                            String[] columns = {GroupsDataFormat.GroupData.COLUMN_ACCOUNT_NAME,
                                    GroupsDataFormat.GroupData.COLUMN_PHONE_NUMBER,
                                    GroupsDataFormat.GroupData.COLUMN_REGID};
                            Cursor c = db.query(GroupsDataFormat.GroupData.TABLE_NAME + group_ID,
                                    columns, null, null, null, null, GroupsDataFormat.GroupData.COLUMN_ACCOUNT_NAME);
                            c.moveToFirst();
                            while (!c.isAfterLast()) {
                                group_regids.append(c.getString(GroupsDataFormat.GroupData.REGID_INDEX));
                                group_names.append(c.getString(GroupsDataFormat.GroupData.ACCOUNT_INDEX));
                                group_phones.append(c.getString(GroupsDataFormat.GroupData.PHONE_INDEX));
                                group_regids.append(",");
                                group_names.append(",");
                                group_phones.append(",");
                                c.moveToNext();
                            }
                            c.close();
                            data.putString("group_phones", group_phones.toString());
                            data.putString("group_names", group_names.toString());
                            data.putString("group_regids", group_regids.toString());
                            data.putString("group_type", "existing");
                            data.putString("group_id", group_ID);
                        }

                    } else {
                        data.putString("group_phones", infoBundle.get("senderPhone") + ",");
                        data.putString("group_names", infoBundle.get("senderName") + ",");
                        data.putString("group_regids", regid + ",");
                        data.putString("group_type", "new");
                    }

                    data.putString("message_intent", "request_add_to_group");
                    data.putString("senderPhone", infoBundle.get("senderPhone"));
                    data.putString("senderName", infoBundle.get("senderName"));

                    data.putString("targetPhone", infoBundle.get("targetPhone"));
                    data.putString("targetName", infoBundle.get("targetName"));
                    data.putString("latitude", lat);
                    data.putString("longitude", longitude);

                    String id = Integer.toString(msgId.incrementAndGet());
                    gcm.send(SENDER_ID + "@gcm.googleapis.com", id, data);

                } catch (IOException e) {
                    AlertDialog dia = new AlertDialog.Builder(context)
                            .setMessage("We're having trouble connecting to our servers, you're request" +
                                    "could not be completed.")
                            .setNeutralButton("okay", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                }
                            }).show();
                    return "Error: " + e.getMessage();
                }
                return null;
            }
        }.execute(null, null, null);
    }

    /**
     * sendAcceptAddToGroup allows the client
     * to accept an invitation to a group
     * @param intent - the intent passed from
     *               the request receiver.
     */
    public void sendAcceptAddToGroup(final Intent intent){
        new AsyncTask<Void, Void, Void>(){

            @Override
            protected Void doInBackground(Void... arg0) {
                @SuppressWarnings("unused")
                SQLiteDatabase db = groupsdb.getWritableDatabase();
                StringBuilder group_id;
                boolean newGroup = intent.getStringExtra("group_type").equals("new");
                if (newGroup){
                    group_id = new StringBuilder(UUID.randomUUID().toString());
                    for (int i = 0; i < group_id.length(); i++){
                        if (group_id.charAt(i) == '-'){
                            group_id.setCharAt(i, '0');
                        }
                    }
                    createGroupTable(group_id.toString());
                } else { // use the id sent in the message
                    group_id = new StringBuilder(intent.getStringExtra("group_id"));
                    createGroupTable(group_id.toString());
                }

                String[] group_names =
                        intent.getStringExtra("group_names").split(",");
                String[] group_phones =
                        intent.getStringExtra("group_phones").split(",");
                String[] group_regids =
                        intent.getStringExtra("group_regids").split(",");

                for (int i = 0; i < group_regids.length; i++){
                    insertUserToGroup(group_phones[i],
                            group_names[i],
                            group_regids[i],
                            group_id.toString());
                }

                refreshGroupsMenu();

                Bundle requestAcceptedBundle = new Bundle();
                requestAcceptedBundle.putString("message_intent", "accept_add_to_group");
                if (newGroup){
                    requestAcceptedBundle.putString("group_type", "new");
                } else {
                    requestAcceptedBundle.putString("group_type", "existing");
                }
                requestAcceptedBundle.putString("group_id", group_id.toString());

                requestAcceptedBundle.putString("accepted_regid", regid);
                if (mPrefs.getString("LOCAL_USER_NAME","").isEmpty() ||
                        mPrefs.getString("LOCAL_PHONE_NUMBER", "").isEmpty()){
                    getLocalUserInfo();
                }
                // add the local user info to the message
                String new_group_phones  =
                        intent.getStringExtra("group_phones") +
                                mPrefs.getString("LOCAL_USER_PHONE", "") + ",";
                String new_group_regids  =
                        intent.getStringExtra("group_regids") +
                                regid + ",";
                String new_group_names =
                        intent.getStringExtra("group_names") +
                                mPrefs.getString("LOCAL_USER_NAME", "") + ",";

                requestAcceptedBundle.putString("receiver_group_phones", new_group_phones);
                requestAcceptedBundle.putString("receiver_group_regids", new_group_regids);
                requestAcceptedBundle.putString("receiver_group_names", new_group_names);

                requestAcceptedBundle.putString("accepted_name", mPrefs.getString("LOCAL_USER_NAME", ""));
                requestAcceptedBundle.putString("accepted_phone", mPrefs.getString("LOCAL_PHONE_NUMBER",""));
                requestAcceptedBundle.putLong("latitude",
                        (long) mLocationClient.getLastLocation().getLatitude());
                requestAcceptedBundle.putLong("longitude",
                        (long) mLocationClient.getLastLocation().getLongitude());


                String id = Integer.toString(msgId.incrementAndGet());
                try {
                    gcm.send(SENDER_ID + "@gcm.googleapis.com", id, requestAcceptedBundle);
                    return null;
                } catch(IOException e) {
                    System.out.println("Failed to send accepted request response");
                    e.printStackTrace();
                }
                return null;
           }
        }.execute(null,null,null);
    }

    /**
     * When the user selects a group from the groups menu,
     * the client sends out its current location to all of
     * the group members.
     * @param group_id
     */
    protected static void sendCurrentLocationToGroup(final String group_id){
        if (!groupSessionMap.containsKey(group_id)){
            SQLiteDatabase currdb = groupsdb.getReadableDatabase();
            if (isExistingGroup(group_id, currdb.isOpen())){
                ArrayList<GroupMemberProfile> curr = new ArrayList<GroupMemberProfile>();
                String[] columns = {GroupsDataFormat.GroupData.COLUMN_PHONE_NUMBER,
                                    GroupsDataFormat.GroupData.COLUMN_ACCOUNT_NAME,
                                    GroupsDataFormat.GroupData.COLUMN_REGID};
                String table_name = GroupsDataFormat.GroupData.TABLE_NAME + String.valueOf(group_id);
                Cursor c = currdb.query(table_name, columns, null, null, null, null, columns[0]);
                c.moveToFirst();
                while (!c.isAfterLast()){
                    String c_phone_number = c.getString(GroupsDataFormat.GroupData.PHONE_INDEX);
                    String c_name = c.getString(GroupsDataFormat.GroupData.ACCOUNT_INDEX);
                    String c_regid = c.getString(GroupsDataFormat.GroupData.REGID_INDEX);
                    Log.i("something","" + c.getString(2));
                    GroupMemberProfile curr_mem = new GroupMemberProfile(c_phone_number, c_regid, c_name);
                    curr.add(curr_mem);
                    c.moveToNext();
                }
                c.close();
                groupSessionMap.put(group_id, curr);
            }
        }
        ArrayList<GroupMemberProfile> curr = groupSessionMap.get(group_id);
        for (final GroupMemberProfile g_mem : curr){
            new AsyncTask<Void, Void, Void>(){
                @Override
                protected Void doInBackground(Void... voids) {
                    Bundle sendToGroup = new Bundle();
                    Log.i("Receiver Traits", "Name: " + g_mem.getName() + " Phone: " + g_mem.getPhoneNumber() + " Regid: " + g_mem.getRegid());
                    sendToGroup.putString("message_intent", "send_client_location_to_group");
                    sendToGroup.putString("sender_name", mPrefs.getString("LOCAL_USER_NAME", ""));
                    sendToGroup.putString("sender_phone", mPrefs.getString("LOCAL_PHONE_NUMBER", ""));
                    sendToGroup.putString("group_receiver_name", g_mem.getName());
                    sendToGroup.putString("group_receiver_phone", g_mem.getPhoneNumber());
                    sendToGroup.putString("group_receiver_regid", g_mem.getRegid());
                    sendToGroup.putString("unique_group_id", group_id);

                    Location lastLocation = mLocationClient.getLastLocation();


                    if (lastLocation == null){
                        sendToGroup.putString("sender_latitude", String.valueOf(BROWN_LAT));
                        sendToGroup.putString("sender_longitude", String.valueOf(BROWN_LONG));
                    } else {
                        sendToGroup.putString("sender_latitude", String.valueOf(lastLocation.getLatitude()));
                        sendToGroup.putString("sender_longitude", String.valueOf(lastLocation.getLongitude()));
                    }

                    String id = Integer.toString(msgId.incrementAndGet());
                    try {
                        gcm.send(SENDER_ID + "@gcm.googleapis.com", id, sendToGroup);
                        Log.i("CLIENT", "Sent location to group in main");
                        return null;
                    } catch(IOException e) {
                        System.out.println("Failed to send location to group with id " + group_id);
                        e.printStackTrace();
                    }
                    return null;
                }
            }.execute(null,null,null);
        }
    }

    /**
     * Update the location preference
     * based on the state of the updates
     * toggle switch
     */
    protected void updateLocationPrefs(){
        mPrefs = getSharedPreferences("Shared Preferences", Context.MODE_PRIVATE);
        mEditor = mPrefs.edit();
        if (!mPrefs.contains("KEY_UPDATES_ON")) {
            mEditor.putBoolean("KEY_UPDATES_ON", updatesRequested).commit();
        }
    }

    /**
     * Initialize the location client if
     * play services are available and
     * connected.
     */
    protected void initLocationClient(){
        if (servicesConnected()){
            mLocationClient = new LocationClient(this,this,this);
            mLocationRequest = LocationRequest.create();
            mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            mLocationRequest.setInterval(ConnectionParameters.UPDATE_INTERVAL);
            mLocationRequest.setFastestInterval(ConnectionParameters.FASTEST_INTERVAL);
        } else {
            Log.i("Google Play Services", "servicesConnected(): play services not found");
        }
    }

    /**
     * registedInBackground asynchronously registers
     * the device if no registration id is found on
     * in the shared preferences of the client.
     */
    private void registerInBackground() {
        new AsyncTask<Void, Object, Object>() {
            @Override
            protected String doInBackground(Void... params) {
                String msg = "";
                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(context);
                    }
                    regid = gcm.register(SENDER_ID);
                    msg = "Device registered, registration ID=" + regid;
                    Log.i("Registration Confirmed", msg);
                    getGCMPreferences(context).edit()
                            .putString("LOCAL_REGISTRATION_ID", regid)
                            .commit();
                    getLocalUserInfo();
                    sendRegistrationIdToBackend(regid);
                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();

                }
                return msg;
            }
        }.execute(null, null, null);
    }

    /**
     * Get the GCM registration id of the
     * client from the application context
     * @param context - context of the app
     * @return
     */
    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGCMPreferences(context);
        String registrationId = prefs.getString("LOCAL_REGISTRATION_ID", "");

        if (registrationId.isEmpty()) {
            Log.i("gcm", "Registration not found.");
            return "";
        }
        return registrationId;
    }

    /**
     * Get GCM-specific shared preferences
     * from the application context.
     * @param context
     * @return
     */
    private SharedPreferences getGCMPreferences(Context context) {
        return getSharedPreferences(main.class.getSimpleName(),
                Context.MODE_PRIVATE);
    }

    /**
     * sendRegistrationIdToBackend sends the registration
     * information of the client to the third party app
     * server so that it may be accessed by other users
     * who have the client as a contact.
     * @param regID - client gcm registration id
     */
    private void sendRegistrationIdToBackend(final String regID){
        final SharedPreferences prefs = getGCMPreferences(context);
        prefs.edit().putString("LOCAL_REGISTRATION_ID", regID).commit();
        new AsyncTask<Void, Void, Void>(){

            @Override
            protected Void doInBackground(Void... params) {

                try {
                    if (mPrefs.getString("LOCAL_USER_NAME", "").isEmpty() ||
                            mPrefs.getString("LOCAL_PHONE_NUMBER", "").isEmpty()){
                        getLocalUserInfo();
                    }
                    Bundle data = new Bundle();
                    data.putString("message_intent", "register_new_user");
                    data.putString("local_regid", regID);
                    data.putString("local_phone_number",
                            mPrefs.getString("LOCAL_PHONE_NUMBER", ""));
                    data.putString("local_user_name",
                            mPrefs.getString("LOCAL_USER_NAME", ""));
                    String id = Integer.toString(msgId.incrementAndGet());
                    gcm.send(SENDER_ID + "@gcm.googleapis.com", id, data);
                    Log.i("Device Registration Bundle", "Device registration bundle sent.");
                } catch (IOException e){
                    e.printStackTrace();
                }
                return null;
            }

        }.execute(null,null,null);
    }

    /**
     * A master method to retrieve relevant client
     * information including longest matching account
     * name, phone number, and gcm registration id.
     */
    private void getLocalUserInfo(){
        if (mPrefs.getString("LOCAL_PHONE_NUMBER", "").isEmpty()){
            TelephonyManager tm = ( TelephonyManager ) getSystemService(Context.TELEPHONY_SERVICE);
            mEditor.putString("LOCAL_PHONE_NUMBER", "+" + tm.getLine1Number()).commit();
        } else {
            Log.i("GOT LOCAL USER NAME", mPrefs.getString("LOCAL_PHONE_NUMBER",""));
        }

        if (mPrefs.getString("LOCAL_USER_NAME", "").isEmpty()){
            String localUserName = "";
            ArrayList<String> potentialEmailBackups = new ArrayList<String>();

            AccountManager manager = ( AccountManager ) getSystemService(Context.ACCOUNT_SERVICE);
            Account[] list = manager.getAccounts();

            for (Account acc : list){
                String[] tempName = acc.name.toString().split("\\s+");
                if (tempName.length > 1){
                    if (tempName.length > localUserName.split("//s+").length){
                        localUserName = acc.name.toString();
                    }
                } else if (tempName.length == 1) {
                    if (tempName[0].contains("@")){
                        String[] emailComponent = tempName[0].split("@");
                        potentialEmailBackups.add(emailComponent[0]);
                    }
                }
            }

            if (localUserName.equals("")){
                for (String acc_name : potentialEmailBackups){
                    if (acc_name.length() > localUserName.length()){
                        localUserName = acc_name;
                    }
                }
            }

            mEditor.putString("LOCAL_USER_NAME", localUserName).commit();
        } else {
            Log.i("LOCAL_USER_NAME", mPrefs.getString("LOCAL_USER_NAME", ""));
        }
    }

    /**
     * A method that handles map initialization. The default
     * location for the map initialization is Brown University.
     */
    protected void mapInit(){
        map = (( MapFragment ) getFragmentManager().findFragmentById(R.id.main_map)).getMap();
        map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        setMapUiElements(map.getUiSettings());

        float lastLatitude = mPrefs.getFloat("LAST_LATITUDE", BROWN_LAT);
        float lastLongitude = mPrefs.getFloat("LAST_LONGITUDE", BROWN_LONG);

        LatLng mapInitPos = new LatLng(lastLatitude, lastLongitude);

        CameraPosition defaultCameraPosition = new CameraPosition.Builder()
                .target(mapInitPos)
                .zoom(17)
                .tilt(30).build();
        map.animateCamera(CameraUpdateFactory.newCameraPosition(defaultCameraPosition));

        myGlobalPositionMarker.position(mapInitPos);
        myPosition = map.addMarker(myGlobalPositionMarker);
        myPosition.setTitle("Me");
        myPosition.setInfoWindowAnchor(0.5f,0.0f);
        myPosition.showInfoWindow();

        if (!currentOpenGroupId.isEmpty()){
            HashMap<String, Marker> curr_map = markerMap.get(currentOpenGroupId);
            if (curr_map != null) {
                for (String key : curr_map.keySet()) {
                    Marker m = curr_map.get(key);
                    MarkerOptions m_o = new MarkerOptions()
                            .anchor(0.0f, 1.0f)
                            .position(m.getPosition());
                    m = map.addMarker(m_o);
                    if (m != null) {
                        ArrayList<GroupMemberProfile> group = groupSessionMap.get(currentOpenGroupId);
                        for (GroupMemberProfile g_mem : group) {
                            Log.i("LOOKING AT GROUP MEMBER", "G_MEM PHONE!!!!!! " + g_mem.getPhoneNumber() + " KEY PHONE " + key);
                            if (g_mem.getPhoneNumber().equals(key)) {
                                m.setTitle(g_mem.getName().split("\\s+")[0]);
                                Log.i("Hello", "SET THE TITLE OF THE MARKER TO " + g_mem.getName().split("\\s+")[0]);
                            }
                        }
                        m.showInfoWindow();
                        m.setInfoWindowAnchor(0.5f, 0.0f);
                    }
                }
            }
        }
    }

    protected void setMapUiElements(UiSettings ui){
        ui.setCompassEnabled(false);
        ui.setZoomControlsEnabled(false);
    }

    /**
     * Sets the components of the navigation bar
     * in the main activity.
     */
    protected void getNavBarButtons(){
        roboto = Typeface.createFromAsset(getAssets(),
                "Roboto-Regular.ttf");
        int i = 0;
        navBarButtons[i] = (Button) findViewById(R.id.groups);
        i+=1;
        navBarButtons[i] = (Button) findViewById(R.id.friends);
        i+=1;
        navBarButtons[i] = (Button) findViewById(R.id.search);

        for (int j = 0; j < NAV_BAR_BUTTON_COUNT; j++){
            navBarButtons[j].setTypeface(roboto);
        }
    }

    /**
     * Sets the relevant listeners for the location
     * updates toggle to be display in all activities.
     * This allows the user to easily disable location
     * broadcasting.
     */
    protected void setLocationUpdatesToggle(){
        mToggleUpdates = ( Switch )findViewById(R.id.updates_toggle);
        roboto = Typeface.createFromAsset(getAssets(),
                "Roboto-Bold.ttf");
        mToggleUpdates.setTypeface(roboto);
        if (mPrefs.getBoolean("KEY_UPDATES_ON", false)){
            mToggleUpdates.setChecked(true);
        } else {
            mToggleUpdates.setChecked(false);
        }
        mToggleUpdates.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener(){
                    @Override
                    public void onCheckedChanged(CompoundButton arg0,
                                                 boolean isChecked) {
                        if (isChecked){
                            updatesRequested = true;
                            if (mLocationClient.isConnected()) {
                                mLocationClient.requestLocationUpdates(mLocationRequest, main.this);
                            }
                        } else {
                            updatesRequested = false;
                            if (mLocationClient.isConnected()) {
                                mLocationClient.removeLocationUpdates(main.this);
                            }
                        }
                        mEditor.putBoolean("KEY_UPDATES_ON", updatesRequested).commit();
                    }
                });
    }

    /**
     * This method checks for the presence of a
     * GPS location provider and displays a window
     * that suggests that this service be turned on
     * for better location targeting.
     */
    protected void checkForGPS(){
        LocationManager lm = ( LocationManager ) getApplicationContext()
                .getSystemService(Context.LOCATION_SERVICE);
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            @SuppressWarnings("unused")
            AlertDialog enableGPSAlert = new AlertDialog.Builder(main.this)
                    .setTitle("Location Settings")
                    .setMessage(R.string.gps_alert_message)
                    .setPositiveButton(R.string.gps_alert_positive,
                            new DialogInterface.OnClickListener(){
                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    Intent enableGPS = new Intent(
                                            android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                    startActivity(enableGPS);
                                }
                            }).setNegativeButton(R.string.gps_alert_negative,
                            new DialogInterface.OnClickListener(){
                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                }
                            }).setInverseBackgroundForced(true).show();
        }
    }

    /**
     * Checks if the group that is about to be looked up
     * exists in the local sqlite database.
     * @param table_id - UUID of the group
     * @param isOpenDB - is the database open?
     * @return
     */
    public static  boolean isExistingGroup(String table_id, boolean isOpenDB){
        SQLiteDatabase db = groupsdb.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT tbl_name FROM sqlite_master WHERE " +
                "tbl_name ='" + GroupsDataFormat.GroupData.TABLE_NAME + table_id + "'", null);
        cursor.moveToFirst();
        if (cursor != null) {
            if (cursor.getCount() > 0){
                cursor.close();
                return true;
            }
            cursor.close();
        }
        cursor.close();
        return false;
    }

    /**
     * Create a new table in the client side
     * sqlite master.
     * @param group_num - new group UUID
     */
    public void createGroupTable(String group_num){
        SQLiteDatabase db = groupsdb.getWritableDatabase();
        LocalGroupsDatabase.createGroupTable(db, group_num);
    }

    /**
     * Insert a user into a group in the client-side
     * sqlite group databse.
     * @param phone_number - phone number to insert
     * @param acc_name - principal account name to insert
     * @param regid - GCM registration id to insert
     * @param tableID - target group UUID
     * @return
     */
    public boolean insertUserToGroup (String phone_number, String acc_name, String regid, String tableID){
        if (groupsdb == null){
            return false;
        }
        SQLiteDatabase db = groupsdb.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(GroupsDataFormat.GroupData.COLUMN_PHONE_NUMBER, phone_number);
        values.put(GroupsDataFormat.GroupData.COLUMN_ACCOUNT_NAME, acc_name);
        values.put(GroupsDataFormat.GroupData.COLUMN_REGID, regid);

        if (isExistingGroup(tableID, db.isOpen())){
            db.insert(GroupsDataFormat.GroupData.TABLE_NAME + tableID, "null", values);
            refreshGroupsMenu();
            return true;
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initLocationClient();
        updateLocationPrefs();
        setContentView(R.layout.activity_main);
        IntentFilter filter = new IntentFilter();
        filter.addAction(RequestReceiver.ACTION_RESP);
        registerReceiver(receiver,filter);

        context = getApplicationContext();
        groupsdb = new LocalGroupsDatabase(context);
    }

    /**
     * Wrapper method in charge of initializing
     * the group menu and all relevant event
     * listeners.
     */
    protected void initGroupsMenu(){
        groups_menu = ( LinearLayout ) findViewById(R.id.groups_menu);
        groupList = new GroupsFragment();
        Button groups = ( Button ) findViewById(R.id.groups);
        groups.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                FragmentManager fm = getFragmentManager();
                if (fm.findFragmentByTag(groups_fragment_tag) == null){
                    Log.i("WHATS UP", "HELLO");
                    FragmentTransaction ft = fm.beginTransaction();
                    ft.add(R.id.groups_menu, groupList, groups_fragment_tag).commit();
                }
                if (!groups_menu.isShown()){
                    if (search_menu != null && search_menu.isShown()){
                        search_menu.setVisibility(View.GONE);
                    }
                    groups_menu.setVisibility(View.VISIBLE);
                    groups_menu.requestFocus();
                } else {
                    groups_menu.setVisibility(View.GONE);
                }
            }
        });
    }

    /**
     * A method to update the groups menu as it appears in
     * the UI. This is a very crude method that should be
     * refined later with animations.
     */
    protected void refreshGroupsMenu(){
        FragmentManager fm = getFragmentManager();
        groupList = fm.findFragmentByTag(groups_fragment_tag);
        if (groupList != null){
            if (groupList.isVisible()){
                groupList.setUserVisibleHint(false);
            }
            FragmentTransaction ft = fm.beginTransaction();
            ft.remove(groupList);
            groupList = new GroupsFragment();
            ft.add(groupList, groups_fragment_tag).commit();
        }
    }

    /**
     * Initialize the contacts menu and all relevant
     * event listeners.
     */
    protected void initContactsMenu(){
        search_menu = ( LinearLayout ) findViewById(R.id.search_menu);
        final EditText search_bar = ( EditText ) findViewById(R.id.search_bar);
        final InputMethodManager imm = ( InputMethodManager ) getSystemService(Context.INPUT_METHOD_SERVICE);

        search_bar.setOnFocusChangeListener(
            new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View view, boolean focused) {
                    if (focused){
                        imm.showSoftInput(search_bar,InputMethodManager.SHOW_IMPLICIT);
                    } else {
                        imm.hideSoftInputFromWindow(search_bar.getWindowToken(),0);
                    }
                }
            });

        search_bar.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable editable) {

                FragmentManager fm = getFragmentManager();
                FragmentTransaction ft = fm.beginTransaction();
                if (fm.findFragmentByTag(contacts_fragment_tag) != null) {
                    ft.remove(contactsList);
                }
                Bundle args = new Bundle();
                args.putString("search", search_bar.getText().toString());
                contactsList = new ContactsFragment();
                contactsList.setArguments(args);
                ft.add(R.id.search_menu, contactsList, contacts_fragment_tag).commit();
            }

            @Override
            public void beforeTextChanged(CharSequence arg0, int arg1,
                                          int arg2, int arg3) {
            }
            @Override
            public void onTextChanged(CharSequence arg0, int arg1, int arg2,
                                      int arg3) {
            }

        });

        Button searchButton = ( Button ) findViewById(R.id.search);
        searchButton.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View view) {
                search_bar.requestFocus();
                if (search_menu.isShown()){
                    search_menu.setVisibility(View.GONE);
                } else {
                    search_menu.setVisibility(View.VISIBLE);
                }
            }

        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        mapInit();
        if (servicesConnected() && !mLocationClient.isConnected()
                && !mLocationClient.isConnecting()) {
            mLocationClient.connect();
        }

        getNavBarButtons();
        setLocationUpdatesToggle();
        initGroupsMenu();
        initContactsMenu();

        gcm = GoogleCloudMessaging.getInstance(this);
        regid = getRegistrationId(context);

        if (regid.isEmpty()){
            Log.i("GCM Register", "Registering Device");
            registerInBackground();
        }
        return true;
    }

    @Override
    public void onBackPressed(){
        LinearLayout searchMenu = ( LinearLayout ) findViewById(R.id.search_menu);
        LinearLayout groupsMenu = ( LinearLayout ) findViewById(R.id.groups_menu);
        if (searchMenu.isFocused() || searchMenu.isShown()){
            searchMenu.setVisibility(View.GONE);
        } else if (groupsMenu.isFocused() || groupsMenu.isShown()) {
            groupsMenu.setVisibility(View.GONE);
        } else {
            this.finish();
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (servicesConnected()){
            if (mToggleUpdates != null){
                mToggleUpdates.setChecked(updatesRequested);
            }
            if (updatesRequested && mLocationClient.isConnected()){
                mLocationClient.requestLocationUpdates(mLocationRequest, this);
            }
            if (!mLocationClient.isConnected() && !mLocationClient.isConnecting()){
                mLocationClient.connect();
                mLocationClient.requestLocationUpdates(mLocationRequest, this);
            }
        }
    }

    @Override
    public void onDisconnected() {
        mEditor.putFloat("LAST_LATITUDE",
                (float) myPosition.getPosition().latitude);
        mEditor.putFloat("LAST_LONGITUDE",
                (float) myPosition.getPosition().longitude);
        mEditor.commit();
        if (mLocationClient.isConnected()){
            mLocationClient.removeLocationUpdates(this);
            mToggleUpdates.setChecked(false);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.i("Location Changed", "LOCATION CHANGED " + location);
        if (updatesRequested && mLocationClient.isConnected()) {
            if (!currentOpenGroupId.isEmpty()){
                sendCurrentLocationToGroup(currentOpenGroupId);
            }
            final LatLng target = new LatLng(location.getLatitude(), location.getLongitude());
            final LatLngInterpolator interpolator = new LatLngInterpolator.LinearFixed();
            CameraPosition updateCam = new CameraPosition.Builder()
                    .target(new LatLng(location.getLatitude(), location.getLongitude()))
                    .zoom(17)
                    .tilt(30)
                    .build();
            map.animateCamera(CameraUpdateFactory.newCameraPosition(updateCam));

            if (myPosition != null) {
                MarkerAnimation.animateMarkerToHC(myPosition, target, interpolator);
            }
            myGlobalPositionMarker.position(myPosition.getPosition());
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (result.hasResolution()){
            try {
                result.startResolutionForResult(
                        this,
                        ConnectionParameters.CONNECTION_FAILURE_RESOLUTION_REQUEST);
            } catch (IntentSender.SendIntentException e){
                e.printStackTrace();
            }
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Failed to connect")
                    .setTitle("Failure");
            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data){
        switch (requestCode){
            case ConnectionParameters.CONNECTION_FAILURE_RESOLUTION_REQUEST:
                switch (resultCode){
                    case Activity.RESULT_OK:
                        servicesConnected();
                        break;
                }
        }
    }

    @Override
    protected void onStart(){
        super.onStart();
        if (mToggleUpdates != null){
            mToggleUpdates.setChecked(updatesRequested);
        }
        ConnectivityManager cm = ( ConnectivityManager ) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo nw = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        NetworkInfo nw_ = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        if (nw.isConnected() || nw_.isConnected()){
            checkForGPS();
        }
    }

    @Override
    protected void onPause(){
        super.onPause();
        if (!mLocationClient.isConnected() && !mLocationClient.isConnecting()){
            mLocationClient.connect();
        }
        mEditor.putBoolean("KEY_UPDATES_ON", updatesRequested);
        Location pauseLocation;
        try {
            pauseLocation = mLocationClient.getLastLocation();
            mEditor.putFloat("LAST_LATITUDE", (float) pauseLocation.getLatitude());
            mEditor.putFloat("LAST_LONGITUDE", (float) pauseLocation.getLongitude());
        } catch (Exception e) {
            Log.i("onPause", "Exception caught when attempting to save most recent location");
            mEditor.putFloat("LAST_LATITUDE", BROWN_LAT);
            mEditor.putFloat("LAST_LONGITUDE", BROWN_LONG);
        }
        mEditor.commit();
    }

    @Override
    protected void onResume(){
        super.onResume();
        if (!mLocationClient.isConnected() && !mLocationClient.isConnecting()){
            mLocationClient.connect();
        }
        setLocationUpdatesToggle();
        if (mPrefs.contains("KEY_UPDATES_ON")){
            updatesRequested = mPrefs.getBoolean("KEY_UPDATES_ON", false);
        } else {
            mEditor.putBoolean("KEY_UPDATES_ON", false);
            mEditor.commit();
        }
        mToggleUpdates.setChecked(updatesRequested);
        if (servicesConnected() && mLocationClient.isConnected() && updatesRequested){
            Location resume_location;
            LatLngInterpolator interpolator = new LatLngInterpolator.LinearFixed();
            try {
                resume_location = mLocationClient.getLastLocation();
                MarkerAnimation.animateMarkerToHC(myPosition,
                        new LatLng(resume_location.getLatitude(), resume_location.getLongitude()), interpolator);
            } catch (Exception e) {
                MarkerAnimation.animateMarkerToHC(myPosition,
                        new LatLng(BROWN_LAT, BROWN_LONG), interpolator);
            }
        }
    }

    @Override
    protected void onStop(){
        super.onStop();
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        if (mLocationClient != null && mLocationClient.isConnected()) {
            mLocationClient.removeLocationUpdates(this);
            mLocationClient.disconnect();
        }
        unregisterReceiver(receiver);
    }
}
