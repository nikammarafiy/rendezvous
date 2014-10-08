package apps.jan.rendezvous;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

/**
 * This is the main intent service that is started
 * by the GcmBroadcastReceiver when it receives a
 * message from the GCM server. This class communicates
 * with the UI on the main thread to display dialogs about
 * the status of add-to-group requests etc.
 */
public class GcmIntentService extends IntentService {

    public GcmIntentService(){
        super("jan");
        Log.i("intent service", "instantiated");
    }

    public GcmIntentService(String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
        String messageType = gcm.getMessageType(intent);

        if (!extras.isEmpty()){
            if (GoogleCloudMessaging
                    .MESSAGE_TYPE_SEND_ERROR.equals(messageType)){
                Log.e("Intent Service Error: ", "" + extras.toString());
            } else if (GoogleCloudMessaging.
                    MESSAGE_TYPE_DELETED.equals(messageType)){
                Log.e("Intent Service Deleted: ", "" + extras.toString());
            } else if (GoogleCloudMessaging.
                    MESSAGE_TYPE_MESSAGE.equals(messageType)){
                String msgStatus = extras.getString("meetups_error");
                String msgIntent = extras.getString("message_intent");
                if (msgStatus != null){
                    switch (msgStatus) {

                        case "USER_UNREGISTERED":
                            Log.i("Result: ", extras.getString("targetName") + " is not registered");
                            Intent broadcastIntent = new Intent();
                            broadcastIntent.setAction(main.RequestReceiver.ACTION_RESP);
                            broadcastIntent.putExtra("unregistered_name",
                                    extras.getString("targetName"));
                            broadcastIntent.putExtra("intent_intent", "user_unregistered");
                            sendBroadcast(broadcastIntent);
                            break;

                        case "INCOMPLETE_REGISTRATION":
                            Intent incompleteIntent = new Intent();
                            incompleteIntent.setAction(main.RequestReceiver.ACTION_RESP);
                            incompleteIntent.putExtra("unregistered_name",
                                    extras.getString("targetName"));
                            incompleteIntent.putExtra("intent_intent", "incomplete_response");
                            break;

                        case "EXISTING_USER_REGISTERED":
                            break;
                    }
                } else if (msgIntent != null && msgStatus == null){
                    switch (msgIntent){

                        case "request_add_to_group":
                            Intent external_add_intent = new Intent();
                            external_add_intent.setAction(main.RequestReceiver.ACTION_RESP);
                            external_add_intent.putExtra("request_sender",
                                    extras.getString("senderName"));

                            external_add_intent.putExtra("group_names", extras.getString("group_names"));
                            external_add_intent.putExtra("group_phones", extras.getString("group_phones"));
                            external_add_intent.putExtra("group_regids", extras.getString("group_regids"));

                            external_add_intent.putExtra("intent_intent", "external_add_to_group");
                            external_add_intent.putExtra("group_type", extras.getString("group_type"));

                            if (!extras.getString("group_type").equals("new")) {
                                external_add_intent.putExtra("group_id", extras.getString("group_id"));
                            }

                            sendBroadcast(external_add_intent);
                            break;

                        case "new_user_to_group":
                            Intent new_user_added_intent = new Intent();
                            new_user_added_intent.setAction(main.RequestReceiver.ACTION_RESP);
                            new_user_added_intent.putExtra("intent_intent", "new_user_to_group");
                            new_user_added_intent.putExtra("group_type", intent.getStringExtra("group_type"));
                            new_user_added_intent.putExtra("group_id", intent.getStringExtra("group_id"));
                            new_user_added_intent.putExtra("accepted_name", intent.getStringExtra("accepted_name"));
                            new_user_added_intent.putExtra("accepted_phone", intent.getStringExtra("accepted_phone"));
                            new_user_added_intent.putExtra("accepted_regid", intent.getStringExtra("accepted_regid"));
                            sendBroadcast(new_user_added_intent);
                            break;

                        case "user_registration_success":
                            break;

                        case "incoming_group_member_location":
                            Log.i("intent service", "intent service : received routed location");
                            Intent incoming_group_member_location = new Intent();
                            incoming_group_member_location.setAction(main.RequestReceiver.ACTION_RESP);
                            incoming_group_member_location.putExtra("intent_intent",
                                    "incoming_group_member_location");
                            incoming_group_member_location.putExtra("sender_latitude",
                                    intent.getStringExtra("sender_latitude"));
                            incoming_group_member_location.putExtra("sender_longitude",
                                    intent.getStringExtra("sender_longitude"));
                            incoming_group_member_location.putExtra("unique_group_id",
                                    intent.getStringExtra("unique_group_id"));
                            incoming_group_member_location.putExtra("sender_phone",
                                    intent.getStringExtra("sender_phone"));
                            incoming_group_member_location.putExtra("sender_name",
                                    intent.getStringExtra("sender_name"));
                            sendBroadcast(incoming_group_member_location);
                            break;
                    }
                }
            }
        }
        WakefulBroadcastReceiver.completeWakefulIntent(intent);
    }

}
