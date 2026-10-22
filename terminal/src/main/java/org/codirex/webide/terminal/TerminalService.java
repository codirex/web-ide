package org.codirex.webide.terminal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

/**
 * Keeps the terminal sessions alive while the app is in the background, and shows a notification
 * with an Exit action while they run.
 */
public final class TerminalService extends Service {

    /** Send this action to the service to end all sessions and stop it. */
    public static final String ACTION_EXIT = "org.codirex.webide.terminal.action.EXIT";

    private static final String CHANNEL_ID = "terminal";
    private static final int NOTIFICATION_ID = 1;

    /** Gives the screen access to the service. */
    public final class LocalBinder extends Binder {
        public TerminalService getService() {
            return TerminalService.this;
        }
    }

    private final IBinder binder = new LocalBinder();
    private TerminalSessionManager manager;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, getString(R.string.terminal_channel_name),
                NotificationManager.IMPORTANCE_LOW));

        manager = new TerminalSessionManager(this);
        manager.setCountObserver(this::onSessionCountChanged);
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_EXIT.equals(intent.getAction())) {
            manager.finishAll();
            stopForeground(true);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        manager.finishAll();
        super.onDestroy();
    }

    public TerminalSessionManager getManager() {
        return manager;
    }

    private void onSessionCountChanged() {
        if (manager.getSessions().isEmpty()) {
            stopForeground(true);
            stopSelf();
        } else {
            getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent().setClassName(getPackageName(), "org.codirex.webide.TerminalActivity");
        PendingIntent openIntent = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent exit = new Intent(this, TerminalService.class).setAction(ACTION_EXIT);
        PendingIntent exitIntent = PendingIntent.getService(this, 1, exit, PendingIntent.FLAG_UPDATE_CURRENT);

        int count = manager == null ? 0 : manager.getSessions().size();
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_terminal_notification)
                .setContentTitle(getString(R.string.terminal_notification_title))
                .setContentText(getString(R.string.terminal_notification_text, count))
                .setContentIntent(openIntent)
                .setOngoing(true)
                .addAction(0, getString(R.string.terminal_notification_exit), exitIntent)
                .build();
    }
}
