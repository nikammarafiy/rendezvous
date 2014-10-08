package apps.jan.rendezvous;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

/**
 * The GCM broadcast receiver to manage communication
 * with the third party app server as well as the intent
 * service that runs on the client.
 */
public class GcmBroadcastReceiver extends WakefulBroadcastReceiver{

    @Override
    public void onReceive(Context context, Intent broadcast) {
        ComponentName comp = new ComponentName(context.getPackageName(),
                GcmIntentService.class.getName());
        startWakefulService(context, broadcast.setComponent(comp));
        setResultCode(Activity.RESULT_OK);
    }

    public void handleUserNotRegisteredResponse(){

    }

}
