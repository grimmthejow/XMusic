package com.xapps.media.xmusic.helper;
import com.xapps.media.xmusic.service.PlayerService;

public interface ServiceCallback {
    
    public static int CALLBACK_COLORS_UPDATE = 1;
    public static int CALLBACK_PROGRESS_UPDATE = 2;
    public static int CALLBACK_VUMETER_UPDATE = 3;
    
    void onServiceEvent(int data);
    
    class Hub {
        private static ServiceCallback callback;

        public static void set(ServiceCallback cb) {
            callback = cb;
        }

        @SuppressWarnings("unchecked")
        public static void send(int data) {
            if (callback != null) ((ServiceCallback) callback).onServiceEvent(data);
        }
    }
}