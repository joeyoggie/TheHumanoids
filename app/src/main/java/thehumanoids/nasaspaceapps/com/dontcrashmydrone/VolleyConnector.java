package thehumanoids.nasaspaceapps.com.dontcrashmydrone;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

/**
 * Created by Joey on 4/23/2016.
 */

//A class that will handle all the HTTP connections in background threads (queues) using Volley library
public class VolleyConnector {
    private static VolleyConnector mInstance;
    private RequestQueue mRequestQueue;
    private static Context mContext;

    private VolleyConnector(Context context){
        mContext = context;
        mRequestQueue = getRequestQueue();
    }

    public static synchronized VolleyConnector getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new VolleyConnector(context);
        }
        return mInstance;
    }

    public RequestQueue getRequestQueue() {
        if (mRequestQueue == null) {
            //getApplicationContext() is key, it keeps you from leaking the
            //Activity or BroadcastReceiver if someone passes one in.
            mRequestQueue = Volley.newRequestQueue(mContext.getApplicationContext());
        }
        return mRequestQueue;
    }

    public <T> void addToRequestQueue(Request<T> req) {
        getRequestQueue().add(req);
    }


}
