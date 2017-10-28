package com.example.spsvision.mqtt_test;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.sql.Connection;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

import static com.example.spsvision.mqtt_test.R.styleable.View;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getName();
    MqttAndroidClient client;
    private View rootView;
    private EditText messageET;
    private ListView messagesContainer;
    private ImageButton sendBtn;
    private ChatAdapter adapter;
    private ArrayList<ChatMessage> chatHistory;
    private ArrayAdapter<String> adapters;
    private static int id = 0;
    private static Connection mqttConnection = null;
    private String lastReceivedMessage = "";
    private String lastSentMessage = "";
    private MainActivity.ChangeListener changeListener = new MainActivity.ChangeListener();
    private String cHandle = "";
    private boolean rth = false;


    private boolean blnUseExternalMQTT = true;  //false;
    private Handler handlerMQTTConnectionCheck = new Handler();
    private Runnable runnableMQTTConnectionCheck = new Runnable() {
        @Override
        public void run() {
            //logDebug("runnableMQTTConnectionCheck --> checking connection");
            connectToMQTTServer();
            handlerMQTTConnectionCheck.postDelayed(this, 1000);
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        messagesContainer = (ListView) findViewById(R.id.list_of_messages);

        sendBtn = (ImageButton) findViewById(R.id.sendMessageButton);

        handlerMQTTConnectionCheck.postDelayed(runnableMQTTConnectionCheck, 5000);
        initControls();
    }
    @Override
    public void onStart() {
        super.onStart();
        connectToMQTTServer();
    }

    @Override
    public void onStop() {
        super.onStop();

        if(mqttConnection.getClient().isConnected())
            mqttConnection.getClient().close();
        mqttConnection.getClient().unregisterResources();
    }

    @Override
    public void onPause() {
        super.onPause();

        if(mqttConnection != null) {
            if(mqttConnection.isConnected()) {
                mqttConnection.getClient().close();
            }
            mqttConnection.getClient().unregisterResources();
        }
    }
    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
    }
    private void initControls() {
        messagesContainer = (ListView) rootView.findViewById(R.id.list_of_messages);
        messageET = (EditText) rootView.findViewById(R.id.messageEditText);
        sendBtn = (ImageButton) rootView.findViewById(R.id.sendMessageButton);

        RelativeLayout container = (RelativeLayout) findViewById(R.id.container);
        loadStarterMessage();

        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String messageText = messageET.getText().toString();
                if (TextUtils.isEmpty(messageText)) {
                    return;
                }
                lastSentMessage = messageText;
                publishTelemetry(getString(R.string.OneEndPublish),messageText);
                messageET.setText("");
            }
        });
    }

    public void displayMessage(ChatMessage message) {
        adapter.add(message);
        adapter.notifyDataSetChanged();
        scroll();
    }

    private void scroll() {
        messagesContainer.setSelection(messagesContainer.getCount() - 1);
    }

    private void loadStarterMessage(){

        chatHistory = new ArrayList<ChatMessage>();

        ChatMessage msg = new ChatMessage();
        msg.setId(1);
        msg.setMe(false);
        msg.setMessage("Hello! Welcome to the InDro Field Station. I'll be your pilot. Please click the GREEN 'play' button to begin your mission. To stop this mission at any point in time, please click the RED 'stop' button to abort.");
        msg.setDate(DateFormat.getDateTimeInstance().format(new Date()));
        chatHistory.add(msg);

        // Message history adapter is defined here.
        adapter = new ChatAdapter(this, new ArrayList<ChatMessage>());
        messagesContainer.setAdapter(adapter);

        for(int i=0; i<chatHistory.size(); i++) {
            ChatMessage message = chatHistory.get(i);
            displayMessage(message);
        }
    }
    private void publishTelemetry(String telemetryTopic, String telemetryData) {
        publishTelemetry(telemetryTopic, telemetryData, 1);
    }

    private void publishTelemetry(String telemetryTopic, String telemetryData, int qos) {
        if(mqttConnection != null
                && mqttConnection.getClient() != null
                && mqttConnection.isConnected())

            new MainActivity.MQTTPublishAsyncTask().doInBackground(telemetryTopic, telemetryData, String.valueOf(qos));
    }
    private void subscribeToTopic(String topic) {
        String[] topics = new String[1];
        topics[0] = topic;

        try {
            if(mqttConnection != null) {
                if(mqttConnection.isConnected()){
                    if(mqttConnection.getClient() != null)
                        mqttConnection.getClient().subscribe(topic, 1, null, new WifiP2pManager.ActionListener(this, WifiP2pManager.ActionListener.Action.SUBSCRIBE, cHandle,topics));
                } else {
                    connectToMQTTServer();
                }
            }
        }
        catch (MqttSecurityException e) {
            Log.e(this.getClass().getCanonicalName(), "SecurityException: Failed to subscribe to" + topic + " the client with the handle " + cHandle, e);
        }
        catch (MqttException e) {
            Log.e(this.getClass().getCanonicalName(), "Failed to subscribe to" + topic + " the client with the handle " + cHandle, e);
        }
    }
    private class MQTTPublishAsyncTask extends AsyncTask<String, Integer, Boolean> {
        @Override
        protected Boolean doInBackground(String... params) {
            MqttMessage message = new MqttMessage(params[1].getBytes());
            message.setQos(Integer.parseInt(params[2]));

            if(mqttConnection == null) {
                Log.e(TAG, "mqttConnection is NULL, in publishTelemetry()");
                return false;
            }

            if(!mqttConnection.isConnected()) {
                Log.e(TAG, "mqttConnection is not connected");
                this.cancel(true);
                return false;
            }
            try {
                mqttConnection.getClient().publish(params[0], message);
            } catch(MqttException me) {
                Log.d(TAG, "reason "+me.getReasonCode());
                Log.d(TAG, "msg "+me.getMessage());
                Log.d(TAG, "loc "+me.getLocalizedMessage());
                Log.d(TAG, "cause "+me.getCause());
                Log.d(TAG, "excep "+me);
                me.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
        }
    }

    private class ChangeListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent event) {
            if (!event.getPropertyName().equals(ActivityConstants.ConnectionStatusProperty)) {
                return;
            }
            Log.i("ChangeListener", event.toString());
            Log.i("MQTT ChangeListener", event.getPropertyName());

            getActivity().runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    if(mqttConnection != null) {
                        if(mqttConnection != null) {
                            if(mqttConnection.getClient() != null) {
                                if(mqttConnection.isConnected()) {
                                    subscribeToTopic(getString(R.string.BackEndPublish));
                                    subscribeToTopic(getString(R.string.OneEndPublish));
                                    publishTelemetry(getString(R.string.StationConnection), mqttConnection.isConnected() ? "1" : "0");
                                }
                            }
                        }
                    } else {
                        Log.i("MQTT ChangeListener", "Not Connected");
                    }

                }

            });
        }

    }

    private void connectToMQTTServer() {
        if(mqttConnection != null) {
            if(mqttConnection.isConnected()) {
                return;
            }
        }

        if(blnUseExternalMQTT) {
            cHandle = "tcp://" + getString(R.string.mqtt_server_EXTERNAL) + getString(R.string.mqtt_clientID);
        } else {
            cHandle = "tcp://" + getString(R.string.mqtt_server) + getString(R.string.mqtt_clientID);
        }

        //Connect to MQTT server
        Bundle dataBundle = new Bundle();
        String server;
        if(blnUseExternalMQTT) {
            server = getString(R.string.mqtt_server_EXTERNAL);
        } else {
            server = getString(R.string.mqtt_server);
        }
        String port = getString(R.string.mqtt_port);
        String clientId = getString(R.string.mqtt_clientID);
        Boolean cleanSession = true;

        //put data into a bundle to be passed back to ClientConnections
        dataBundle.putString(ActivityConstants.server, server);
        dataBundle.putString(ActivityConstants.port, port);
        dataBundle.putString(ActivityConstants.clientId, clientId);
        dataBundle.putInt(ActivityConstants.action, ActivityConstants.connect);
        dataBundle.putBoolean(ActivityConstants.cleanSession, cleanSession);

        dataBundle.putString(ActivityConstants.message, "DISCONNECTED UNEXPECTEDLY");   //ActivityConstants.empty
        dataBundle.putString(ActivityConstants.topic, getString(R.string.topicFieldstation));   //ActivityConstants.empty
        dataBundle.putInt(ActivityConstants.qos, ActivityConstants.defaultQos);
        dataBundle.putBoolean(ActivityConstants.retained, ActivityConstants.defaultRetained);

        dataBundle.putString(ActivityConstants.username, getString(R.string.mqtt_username));    //ActivityConstants.empty
        dataBundle.putString(ActivityConstants.password, getString(R.string.mqtt_password));    //ActivityConstants.empty

        dataBundle.putInt(ActivityConstants.timeout, ActivityConstants.defaultTimeOut);
        dataBundle.putInt(ActivityConstants.keepalive, ActivityConstants.defaultKeepAlive);
        dataBundle.putBoolean(ActivityConstants.ssl, ActivityConstants.defaultSsl);

        connectAction(dataBundle);
    }

    private void connectAction(Bundle data) {
        MqttConnectOptions conOpt = new MqttConnectOptions();
    /*
     * Mutal Auth connections could do something like this
     *
     *
     * SSLContext context = SSLContext.getDefault();
     * context.init({new CustomX509KeyManager()},null,null); //where CustomX509KeyManager proxies calls to keychain api
     * SSLSocketFactory factory = context.getSSLSocketFactory();
     *
     * MqttConnectOptions options = new MqttConnectOptions();
     * options.setSocketFactory(factory);
     *
     * client.connect(options);
     *
     */

        // The basic client information
        String server = (String) data.get(ActivityConstants.server);
        String clientId = (String) data.get(ActivityConstants.clientId);
        int port = Integer.parseInt((String) data.get(ActivityConstants.port));
        boolean cleanSession = (Boolean) data.get(ActivityConstants.cleanSession);

        boolean ssl = false;
        String ssl_key = "";


        String uri = null;
        if (ssl) {
            Log.e("SSLConnection", "Doing an SSL Connect");
            uri = "ssl://";

        }
        else {
            uri = "tcp://";
        }

        uri = uri + server + ":" + port;


        client = Connections.getInstance(getActivity()).createClient(getActivity(), uri, clientId);

        if (ssl){
            try {
                if(ssl_key != null && !ssl_key.equalsIgnoreCase(""))
                {
                    FileInputStream key = new FileInputStream(ssl_key);
                    conOpt.setSocketFactory(client.getSSLSocketFactory(key,
                            "mqtttest"));
                }

            } catch (MqttSecurityException e) {
                Log.e(this.getClass().getCanonicalName(),
                        "MqttException Occured: ", e);
            } catch (FileNotFoundException e) {
                Log.e(this.getClass().getCanonicalName(),
                        "MqttException Occured: SSL Key file not found", e);
            }
        }

        // create a client handle
        String clientHandle = "tcp://" + server + clientId;

        // last will message
        String message = (String) data.get(ActivityConstants.message);
        String topic = (String) data.get(ActivityConstants.topic);
        Integer qos = (Integer) data.get(ActivityConstants.qos);
        Boolean retained = (Boolean) data.get(ActivityConstants.retained);

        // connection options

        String username = (String) data.get(ActivityConstants.username);
        String password = (String) data.get(ActivityConstants.password);

        int timeout = getResources().getInteger(R.integer.mqtt_timeout);
        int keepalive = getResources().getInteger(R.integer.mqtt_keepalive);

        Connection connection = new Connection(clientHandle, clientId, server, port,
                getActivity(), client, ssl);

        // connect client
        connection.registerChangeListener(changeListener);

        String[] actionArgs = new String[1];
        actionArgs[0] = clientId;
        connection.changeConnectionStatus(Connection.ConnectionStatus.CONNECTING);

        conOpt.setCleanSession(cleanSession);
        conOpt.setConnectionTimeout(timeout);
        conOpt.setKeepAliveInterval(keepalive);
        if(username != null && password != null) {
            if (!username.equals(ActivityConstants.empty)) {
                conOpt.setUserName(username);
            }
            if (!password.equals(ActivityConstants.empty)) {
                conOpt.setPassword(password.toCharArray());
            }
        }
        final ActionListener callback = new ActionListener(getActivity(),
                ActionListener.Action.CONNECT, clientHandle, actionArgs);

        boolean doConnect = true;

        if(message == null || topic == null || qos == null || retained == null) {
            Log.e(this.getClass().getCanonicalName(), "message/topic/qos/retained is null");
        } else {
            if ((!message.equals(ActivityConstants.empty))
                    || (!topic.equals(ActivityConstants.empty))) {
                // need to make a message since last will is set
                try {
                    conOpt.setWill(topic, message.getBytes(), qos,
                            retained);
                }
                catch (Exception e) {
                    Log.e(this.getClass().getCanonicalName(), "Exception Occured", e);
                    doConnect = false;
                    callback.onFailure(null, e);
                }
            }
            //CALL
            client.setCallback(new MqttCallbackHandler(getActivity(), clientHandle));

            //set traceCallback
            client.setTraceCallback(new MqttTraceCallback());

            connection.addConnectionOptions(conOpt);
            Connections.getInstance(getActivity()).addConnection(connection);
            if (doConnect) {
                try {
                    client.connect(conOpt, null, callback);
                }
                catch (MqttException e) {
                    Log.e(this.getClass().getCanonicalName(),
                            "MqttException Occured", e);
                }
            }
            mqttConnection = connection;
            Log.d("MQTT Connection", String.valueOf(connection.isConnected()));

        }
    }
    /**
     * Handles call backs from the MQTT Client
     *
     */
    public class MqttCallbackHandler implements MqttCallback {

        /** {@link Context} for the application used to format and import external strings**/
        private Context context;
        /** Client handle to reference the connection that this handler is attached to**/
        private String clientHandle;

        private long lastMQTTCommandTime = 0;

        /**
         * Creates an <code>MqttCallbackHandler</code> object
         * @param context The application's context
         * @param clientHandle The handle to a {@link Connection} object
         */
        public MqttCallbackHandler(Context context, String clientHandle)
        {
            this.context = context;
            this.clientHandle = clientHandle;
        }

        /**
         * @see org.eclipse.paho.client.mqttv3.MqttCallback#connectionLost(Throwable)
         */
        @Override
        public void connectionLost(Throwable cause) {
            if (cause != null) {
                Connection c = Connections.getInstance(context).getConnection(clientHandle);
                c.addAction("Connection Lost");
                c.changeConnectionStatus(Connection.ConnectionStatus.DISCONNECTED);

                //format string to use a notification text
                Object[] args = new Object[2];
                args[0] = c.getId();
                args[1] = c.getHostName();

                String message = context.getString(R.string.connection_lost, args);
                publishTelemetry("","Connection Lost");
                //build intent
                Intent intent = new Intent();
                intent.setClassName(context, "org.eclipse.paho.android.service.sample.ConnectionDetails");
                intent.putExtra("handle", clientHandle);

                //notify the user
                Notify.notification(context, message, intent, R.string.notifyTitle_connectionLost);
            }
        }

        /**
         * @see org.eclipse.paho.client.mqttv3.MqttCallback#messageArrived(String, org.eclipse.paho.client.mqttv3.MqttMessage)
         */
        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            //TROY: THIS IS WHERE WE HANDLE INCOMING SUBSCRIPTION MESSAGES!!

            //Get connection object associated with this object
            Connection c = Connections.getInstance(context).getConnection(clientHandle);
            lastReceivedMessage = message.toString();
            //create arguments to format message arrived notifcation string
            String[] args = new String[2];
            args[0] = new String(message.getPayload());
            args[1] = topic+";qos:"+message.getQos()+";retained:"+message.isRetained();

            //create intent to start activity
            Intent intent = new Intent();
            intent.setClassName(context, "org.eclipse.paho.android.service.sample.ConnectionDetails");
            intent.putExtra("handle", clientHandle);

            //format string args
            Object[] notifyArgs = new String[3];
            notifyArgs[0] = c.getId();
            notifyArgs[1] = new String(message.getPayload());
            notifyArgs[2] = topic;
            ChatMessage chatMessage = new ChatMessage();

            if (topic.equals(getString(R.string.topicFieldTechnicianPublish))) {
                chatMessage.setId(id++);//dummy
                chatMessage.setMessage(message.toString());
                chatMessage.setDate(DateFormat.getDateTimeInstance().format(new Date()));
                chatMessage.setMe(true);
            } else {
                if (topic.equals(getString(R.string.topicDroneAbortMission))) {
                    rth = true;
                }
                chatMessage.setId(id++);
                chatMessage.setMessage(message.toString());
                chatMessage.setDate(DateFormat.getDateTimeInstance().format(new Date()));
                chatMessage.setMe(false);
            }
            //notify the user
            //Notify.notification(context, context.getString(R.string.notification, notifyArgs), intent, R.string.notifyTitle);
            //TROY:DON'T OVERLOAD PHONE WITH NOTIFICATIONS
//    Toast.makeText(context, (String)notifyArgs[1], Toast.LENGTH_SHORT).show();
            //Toast.makeText(context, (String)notifyArgs[2] + ":" + (String)notifyArgs[1], Toast.LENGTH_SHORT).show();
            displayMessage(chatMessage);

            //update client history
            c.addAction(lastReceivedMessage);

        }

        /**
         * @see org.eclipse.paho.client.mqttv3.MqttCallback#deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken)
         */
        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            // Do nothing
        }

    }
}
