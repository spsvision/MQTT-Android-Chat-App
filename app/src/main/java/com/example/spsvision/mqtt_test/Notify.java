package com.example.spsvision.mqtt_test;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat.Builder;
import android.widget.Toast;

import java.util.Calendar;

/**
 * Provides static methods for creating and showing notifications to the user.
 *
 */
public class Notify {

    /** Message ID Counter **/
    private static int MessageID = 0;

    /**
     * Displays a notification in the notification area of the UI
     * @param context Context from which to create the notification
     * @param messageString The string to display to the user as a message
     * @param intent The intent which will start the activity when the user clicks the notification
     * @param notificationTitle The resource reference to the notification title
     */
    static void notification(Context context, String messageString, Intent intent, int notificationTitle) {

        //Get the notification manage which we will use to display the notification
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(ns);
        Calendar.getInstance().getTime().toString();

        long when = System.currentTimeMillis();

        //get the notification title from the application's strings.xml file
        CharSequence contentTitle = context.getString(notificationTitle);

        //the message that will be displayed as the ticker
        String ticker = contentTitle + " " + messageString;

        //build the pending intent that will start the appropriate activity
        PendingIntent pendingIntent = PendingIntent.getActivity(context,
                ActivityConstants.showHistory, intent, 0);

        //build the notification
        Builder notificationCompat = new Builder(context);
        notificationCompat.setAutoCancel(true)
                .setContentTitle(contentTitle)
                .setContentIntent(pendingIntent)
                .setContentText(messageString)
                .setTicker(ticker)
                .setWhen(when)
                .setSmallIcon(R.mipmap.ic_launcher);

        Notification notification = notificationCompat.build();

        //Clear previous notifications so we don't get a flood of notifications
        mNotificationManager.cancelAll();

        //display the notification
        mNotificationManager.notify(MessageID, notification);
        MessageID++;


    }

    /**
     * Display a toast notification to the user
     * @param context Context from which to create a notification
     * @param text The text the toast should display
     * @param duration The amount of time for the toast to appear to the user
     */
    static void toast(Context context, CharSequence text, int duration) {
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

}
