package rnxmpp.service;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.AsyncTask;
import android.view.WindowManager;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatManager;
import org.jivesoftware.smack.chat.ChatManagerListener;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.filter.OrFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.RosterLoadedListener;
import org.jivesoftware.smack.sasl.SASLErrorException;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.jivesoftware.smackx.carbons.packet.CarbonExtension;
import org.jivesoftware.smackx.httpfileupload.HttpFileUploadManager;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jivesoftware.smackx.omemo.OmemoMessage;
import org.jivesoftware.smackx.omemo.exceptions.UndecidedOmemoIdentityException;
import org.jivesoftware.smackx.omemo.internal.OmemoDevice;
import org.jivesoftware.smackx.omemo.listener.OmemoMessageListener;
import org.jivesoftware.smackx.omemo.signal.SignalCachingOmemoStore;
import org.jivesoftware.smackx.omemo.signal.SignalFileBasedOmemoStore;
import org.jivesoftware.smackx.omemo.signal.SignalOmemoService;
import org.jivesoftware.smackx.omemo.trust.OmemoFingerprint;
import org.jivesoftware.smackx.omemo.trust.OmemoTrustCallback;
import org.jivesoftware.smackx.omemo.trust.TrustState;
import org.jivesoftware.smackx.push_notifications.PushNotificationsManager;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import rnxmpp.R;
import rnxmpp.database.MessagesDbHelper;
import rnxmpp.ssl.UnsafeSSLContext;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
import static android.content.Context.NOTIFICATION_SERVICE;


/**
 * Created by Kristian Frølund on 7/19/16.
 * Copyright (c) 2016. Teletronics. All rights reserved
 */

public class XmppServiceSmackImpl implements XmppService, ChatManagerListener, StanzaListener, ConnectionListener, ChatMessageListener, RosterLoadedListener {
    XmppServiceListener xmppServiceListener;
    Logger logger = Logger.getLogger(XmppServiceSmackImpl.class.getName());

    OmemoManager omemoManager;
    SignalOmemoService service;
    XMPPTCPConnection connection;
    Roster roster;
    List<String> trustedHosts = new ArrayList<>();
    String password;
    private ReactApplicationContext reactApplicationContext;
    private final String CHAR_LIST =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
//    FileTransferManager manager;

    private OmemoTrustCallback trustCallback = new OmemoTrustCallback() {
        @Override
        public TrustState getTrust(OmemoDevice device, OmemoFingerprint fingerprint) {
            return TrustState.trusted;
        }

        @Override
        public void setTrust(OmemoDevice device, OmemoFingerprint fingerprint, TrustState state) {

        }
    };

    private OmemoMessageListener messageListener = new OmemoMessageListener() {
        @Override
        public void onOmemoMessageReceived(Stanza stanza, OmemoMessage.Received decryptedMessage) {
            logger.log(Level.INFO, "in the on message receive");
            if (decryptedMessage.isKeyTransportMessage()) {
                return;
            }

            if (appIsInForground()) {
                xmppServiceListener.onOmemoMessage(stanza, decryptedMessage);
            } else {
                insertMessageToDB(stanza, decryptedMessage);
            }
        }

        @Override
        public void onOmemoCarbonCopyReceived(CarbonExtension.Direction direction, Message carbonCopy, Message wrappingMessage, OmemoMessage.Received decryptedCarbonCopy) {

        }
    };

    public XmppServiceSmackImpl(ReactApplicationContext reactApplicationContext) {
        this.xmppServiceListener = new RNXMPPCommunicationBridge(reactApplicationContext);
        this.reactApplicationContext = reactApplicationContext;
    }

    @Override
    public void trustHosts(ReadableArray trustedHosts) {
        for (int i = 0; i < trustedHosts.size(); i++) {
            this.trustedHosts.add(trustedHosts.getString(i));
        }
    }

    @Override
    public void connect(String jid, String password, String authMethod, String hostname, Integer port) {
        final String[] jidParts = jid.split("@");
        String[] serviceNameParts = jidParts[1].split("/");
        String serviceName = serviceNameParts[0];

        XMPPTCPConnectionConfiguration.Builder confBuilder = null;
        try {
            confBuilder = XMPPTCPConnectionConfiguration.builder()
                    .setXmppDomain(serviceName)
                    .setUsernameAndPassword(jidParts[0], password)
                    .setConnectTimeout(3000)
                    //.setDebuggerEnabled(true)
                    .setSecurityMode(ConnectionConfiguration.SecurityMode.required);
        } catch (XmppStringprepException e) {
            e.printStackTrace();
        }

        try {
            if (serviceNameParts.length > 1) {
                confBuilder.setResource(serviceNameParts[1]);
            } else {
                confBuilder.setResource(Long.toHexString(Double.doubleToLongBits(Math.random())));
            }
        } catch (XmppStringprepException e) {
            e.printStackTrace();
        }
        if (hostname != null) {
            confBuilder.setHost(hostname);
        }
        if (port != null) {
            confBuilder.setPort(port);
        }
        if (trustedHosts.contains(hostname) || (hostname == null && trustedHosts.contains(serviceName))) {
            confBuilder.setCustomSSLContext(UnsafeSSLContext.INSTANCE.getContext());
        }
        XMPPTCPConnectionConfiguration connectionConfiguration = confBuilder.build();
        SmackConfiguration.DEBUG = true;
        connection = new XMPPTCPConnection(connectionConfiguration);

        connection.addAsyncStanzaListener(this, new OrFilter(new StanzaTypeFilter(IQ.class), new StanzaTypeFilter(Presence.class)));
        connection.addConnectionListener(this);

        ChatManager.getInstanceFor(connection).addChatListener(this);
        roster = Roster.getInstanceFor(connection);
        roster.addRosterLoadedListener(this);

        // Create the listener for file sending

        // manager = FileTransferManager.getInstanceFor(connection);
        // manager.addFileTransferListener(new FileTransferListener() {
        //     public void fileTransferRequest(FileTransferRequest request) {
        //     // Check to see if the request should be accepted
        //     try {
        //         // Accept it
        //         IncomingFileTransfer transfer = request.accept();
        //          transfer.recieveFile(new File("/storage/emulated/0/" + request.getFileName()));
        //     } catch(IOException | SmackException ex) {

        //     }
        // }
        // });

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    if (connection.isConnected()) {
                        connection.disconnect();
                    }

                    connection.connect().login();

                    SignalOmemoService.acknowledgeLicense();
                    if (!SignalOmemoService.isServiceRegistered()) {
                        SignalOmemoService.setup();
                        service = (SignalOmemoService) SignalOmemoService.getInstance();
                        service.setOmemoStoreBackend(new SignalCachingOmemoStore(new SignalFileBasedOmemoStore(new File(reactApplicationContext.getCacheDir().getPath()))));

                        omemoManager = OmemoManager.getInstanceFor(connection);
                        omemoManager.setTrustCallback(trustCallback);
                        omemoManager.addOmemoMessageListener(messageListener);

                        omemoManager.initializeAsync(new OmemoManager.InitializationFinishedCallback() {
                            @Override
                            public void initializationFinished(OmemoManager manager) {
                                try {
                                    omemoManager.purgeDeviceList();
                                    xmppServiceListener.onOmemoInitResult(true);
                                } catch (Exception e) {
                                    logger.log(Level.SEVERE, "Exception: ", e);
                                }
                            }

                            @Override
                            public void initializationFailed(Exception cause) {
                                xmppServiceListener.onOmemoInitResult(false);
                            }
                        });
                    } else {
                        omemoManager = OmemoManager.getInstanceFor(connection);
                        omemoManager.setTrustCallback(trustCallback);
                        omemoManager.addOmemoMessageListener(messageListener);
                    }

                } catch (XMPPException | SmackException | InterruptedException | IOException e) {
                    logger.log(Level.SEVERE, "Could not login for user " + jidParts[0], e);
                    if (e instanceof SASLErrorException) {
                        XmppServiceSmackImpl.this.xmppServiceListener.onLoginError(((SASLErrorException) e).getSASLFailure().toString());
                    } else {
                        XmppServiceSmackImpl.this.xmppServiceListener.onError(e);
                    }

                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Exception: ", e);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void dummy) {

            }
        }.execute();
    }

    private boolean appIsInForground() {
        ActivityManager.RunningAppProcessInfo appProcessInfo = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(appProcessInfo);
        return (appProcessInfo.importance == IMPORTANCE_FOREGROUND || appProcessInfo.importance == IMPORTANCE_VISIBLE);
    }

    private void insertMessageToDB(Stanza stanza, OmemoMessage.Received decryptedMessage) {
        MessagesDbHelper dbHelper = new MessagesDbHelper(reactApplicationContext);

        String fullContactString = stanza.getFrom().toString();
        String contactAsExtension = fullContactString.substring(0, fullContactString.indexOf("@"));

        JsonObject messageJsonObject = new JsonParser().parse(decryptedMessage.getBody()).getAsJsonObject();
        JsonObject userJsonObject = messageJsonObject.get("user").getAsJsonObject();

        String messageText = messageJsonObject.get("text").getAsString();
        String createdAt = messageJsonObject.get("createdAt").getAsString();
        String messageId = messageJsonObject.get("_id").getAsString();
        String messageUrl = null;
        String messageKey = null;
        Integer recipientId = Integer.valueOf(userJsonObject.get("_id").getAsString());

        try{
            messageUrl = messageJsonObject.get("url").getAsString();
            messageKey = messageJsonObject.get("key").getAsString();
        }
        catch (Exception e)
        {
        }

        int chatId = dbHelper.getChatIdForContactOfMessage(contactAsExtension, messageText, createdAt);

        dbHelper.insertMessage(messageId, messageText, createdAt, contactAsExtension, recipientId, messageUrl, chatId,messageKey);

        dbHelper.closeTransaction();

        sendNotification(messageText, contactAsExtension,messageUrl != null);
    }

    @Override
    public void message(String text, String to,String id, String thread) {
        String chatIdentifier = (thread == null ? to : thread);

        ChatManager chatManager = ChatManager.getInstanceFor(connection);
        Chat chat = chatManager.getThreadChat(chatIdentifier);
        EntityBareJid recipientJid = null;
        if (chat == null) {
            try {
                recipientJid = JidCreate.entityBareFrom(to);
                if (thread == null) {
                    chat = chatManager.createChat(JidCreate.entityBareFrom(to), this);
                } else {
                    chat = chatManager.createChat(JidCreate.entityBareFrom(to), thread, this);
                }
            } catch (XmppStringprepException e) {
                e.printStackTrace();
            }
        }

        OmemoMessage.Sent encrypted = null;
        try {
            omemoManager.requestDeviceListUpdateFor(recipientJid);
            for (OmemoDevice device : omemoManager.getDevicesOf(recipientJid)) {
                OmemoFingerprint fingerprint = omemoManager.getFingerprint(device);
                omemoManager.trustOmemoIdentity(device, fingerprint);
            }

            encrypted = omemoManager.encrypt(recipientJid, text);
        }
        // In case of undecided devices
        catch (UndecidedOmemoIdentityException e) {
            try {
                omemoManager.purgeDeviceList();
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Exception: ", e);
            }
            for (OmemoDevice device : e.getUndecidedDevices()) {
                try {
                    omemoManager.trustOmemoIdentity(device, omemoManager.getFingerprint(device));
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }

            try {
                encrypted = omemoManager.encrypt(recipientJid, text);
            } catch (Exception e1) {
                xmppServiceListener.onOmemoOutgoingMessageResult(false,id);
                e1.printStackTrace();
            }
        } catch (Exception e) {
            xmppServiceListener.onOmemoOutgoingMessageResult(false,id);
            e.printStackTrace();
        }

        //send
        if (encrypted != null) {
            try {
                chat.sendMessage(encrypted.asMessage(recipientJid));
                xmppServiceListener.onOmemoOutgoingMessageResult(true,id);
            } catch (SmackException | InterruptedException e) {
                xmppServiceListener.onOmemoOutgoingMessageResult(false,id);
                logger.log(Level.WARNING, "Could not send message", e);
            }
        }
    }

    private String generateRandomString() {
        StringBuffer randStr = new StringBuffer();
        for (int i = 0; i < 16; i++) {
            int number = getRandomNumber();
            char ch = CHAR_LIST.charAt(number);
            randStr.append(ch);
        }
        return randStr.toString();
    }

    private int getRandomNumber() {
        int randomInt = 0;
        SecureRandom randomGenerator = new SecureRandom();
        randomInt = randomGenerator.nextInt(CHAR_LIST.length());
        if (randomInt - 1 == -1) {
            return randomInt;
        } else {
            return randomInt - 1;
        }
    }

    private void fileProcessor(int cipherMode, String key, File inputFile, File outputFile) {
        try {
            Key secretKey = new SecretKeySpec(key.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(cipherMode, secretKey);

            FileInputStream inputStream = new FileInputStream(inputFile);
            byte[] inputBytes = new byte[(int) inputFile.length()];
            inputStream.read(inputBytes);

            byte[] outputBytes = cipher.doFinal(inputBytes);

            FileOutputStream outputStream = new FileOutputStream(outputFile);
            outputStream.write(outputBytes);

            inputStream.close();
            outputStream.close();

        } catch (NoSuchPaddingException | NoSuchAlgorithmException
                | InvalidKeyException | BadPaddingException
                | IllegalBlockSizeException | IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void decryptFile(String fileURI, String key) {
        File inputFile = new File(fileURI);
        File decryptedFile = new File(fileURI.replace(".assac", ""));
        fileProcessor(Cipher.DECRYPT_MODE, key, inputFile, decryptedFile);
    }

    @Override
    public void sendFile(String fileURI) {
        HttpFileUploadManager manager = HttpFileUploadManager.getInstanceFor(connection);
        manager.setTlsContext(UnsafeSSLContext.INSTANCE.getContext());
        try {
            String key = generateRandomString();
            File inputFile = new File(fileURI);
            File encryptedFile = new File(fileURI + ".assac");
            fileProcessor(Cipher.ENCRYPT_MODE, key, inputFile, encryptedFile);
            //URL fileURL = manager.uploadFile(new File(fileURI));
            URL fileURL = manager.uploadFile(encryptedFile);
            encryptedFile.delete();
            this.xmppServiceListener.onFileReceived(fileURL.toString(), fileURI, key);
        } catch (SmackException | InterruptedException | IOException | XMPPException e) {
            logger.log(Level.WARNING, "Could not upload and send file", e);
        }
    }

    @Override
    public void enablePushNotifications(String pushJid, String node, String secret) {
        PushNotificationsManager pushNotificationsManager = PushNotificationsManager.getInstanceFor(connection);
        HashMap<String, String> publishOptions = new HashMap<String, String>();
        publishOptions.put("secret", secret);
        publishOptions.put("endpoint", "https://assac.phone.gs:5281/push_appserver/v1/push");

        try {
            logger.log(Level.INFO, "Now we are going to try enabling push notifications");
            //pushNotificationsManager.enable(JidCreate.entityBareFrom(pushJid), node, publishOptions);
            pushNotificationsManager.enable(JidCreate.from("assac.phone.gs"), node, publishOptions);
        } catch (XmppStringprepException | SmackException.NotConnectedException | InterruptedException | XMPPException.XMPPErrorException | SmackException.NoResponseException e) {
            logger.log(Level.WARNING, "Could not enable push notifications", e);
        }
    }

    @Override
    public void setupOmemo() {

    }

    @Override
    public void presence(String to, String type) {
        try {
            connection.sendStanza(new Presence(Presence.Type.fromString(type), type, 1, Presence.Mode.fromString(type)));
        } catch (SmackException.NotConnectedException | InterruptedException e) {
            logger.log(Level.WARNING, "Could not send presence", e);
        }
    }

    @Override
    public void removeRoster(String to) {
        Roster roster = Roster.getInstanceFor(connection);
        RosterEntry rosterEntry = null;
        try {
            rosterEntry = roster.getEntry(JidCreate.entityBareFrom(to));
        } catch (XmppStringprepException e) {
            e.printStackTrace();
        }
        if (rosterEntry != null) {
            try {
                roster.removeEntry(rosterEntry);
            } catch (SmackException.NotLoggedInException | InterruptedException | SmackException.NotConnectedException | XMPPException.XMPPErrorException | SmackException.NoResponseException e) {
                logger.log(Level.WARNING, "Could not remove roster entry: " + to);
            }
        }
    }

    @Override
    public void disconnect() {
        connection.disconnect();
        xmppServiceListener.onDisconnect(null);
    }

    @Override
    public void fetchRoster() {
        try {
            roster.reload();
        } catch (SmackException.NotLoggedInException | InterruptedException | SmackException.NotConnectedException e) {
            logger.log(Level.WARNING, "Could not fetch roster", e);
        }
    }

    @Override
    public void processStanza(Stanza packet) throws SmackException.NotConnectedException, InterruptedException, SmackException.NotLoggedInException {

    }

    public class StanzaPacket extends org.jivesoftware.smack.packet.Stanza {
        private String xmlString;

        public StanzaPacket(String xmlString) {
            super();
            this.xmlString = xmlString;
        }

        @Override
        public String toString() {
            return null;
        }

        //         @Override
        public XmlStringBuilder toXML() {
            XmlStringBuilder xml = new XmlStringBuilder();
            xml.append(this.xmlString);
            return xml;
        }

        @Override
        public CharSequence toXML(String enclosingNamespace) {
            return null;
        }
    }

    @Override
    public void sendStanza(String stanza) {
        StanzaPacket packet = new StanzaPacket(stanza);
        try {
            connection.sendStanza(packet);
        } catch (SmackException | InterruptedException e) {
            logger.log(Level.WARNING, "Could not send stanza", e);
        }
    }

    @Override
    public void chatCreated(Chat chat, boolean createdLocally) {
        chat.addMessageListener(this);
    }

    //    @Override
    public void processPacket(Stanza packet) throws SmackException.NotConnectedException {
        if (packet instanceof IQ) {
            this.xmppServiceListener.onIQ((IQ) packet);
        } else if (packet instanceof Presence) {
            this.xmppServiceListener.onPresence((Presence) packet);
        } else {
            logger.log(Level.WARNING, "Got a Stanza, of unknown subclass");
        }
    }

    @Override
    public void connected(XMPPConnection connection) {
        this.xmppServiceListener.onConnnect("a", password);
    }

    @Override
    public void authenticated(XMPPConnection connection, boolean resumed) {
        this.xmppServiceListener.onLogin(connection.getUser().toString(), password);
    }

    @Override
    public void processMessage(Chat chat, Message message) {
        // this.xmppServiceListener.onMessage(message);
    }

    @Override
    public void onRosterLoaded(Roster roster) {
        this.xmppServiceListener.onRosterReceived(roster);
    }

    @Override
    public void onRosterLoadingFailed(Exception exception) {

    }

    @Override
    public void connectionClosedOnError(Exception e) {
        this.xmppServiceListener.onDisconnect(e);
    }

    @Override
    public void connectionClosed() {
        logger.log(Level.INFO, "Connection was closed.");
    }

    //    @Override
    public void reconnectionSuccessful() {
        logger.log(Level.INFO, "Did reconnect");
    }

    //    @Override
    public void reconnectingIn(int seconds) {
        logger.log(Level.INFO, "Reconnecting in {0} seconds", seconds);
    }

    //    @Override
    public void reconnectionFailed(Exception e) {
        logger.log(Level.WARNING, "Could not reconnect", e);

    }

    public void sendNotification(String text, String from, boolean isFile) {

        android.support.v4.app.NotificationCompat.Builder builder = new android.support.v4.app.NotificationCompat.Builder(reactApplicationContext);
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setContentTitle("You've got a new message");
        builder.setContentText(isFile ? from + " sent you a file " : from + ": " + text);
        builder.setOngoing(false);
        builder.setAutoCancel(true);

        final Activity activity = reactApplicationContext.getCurrentActivity();

        if (activity != null) {
//        //Needs to change the XmppServiceSmackImpl.class somehow to MainActivity.class so when pressing the notification, the app will open
            PendingIntent contentIntent = PendingIntent.getActivity(reactApplicationContext, 0,
                    new Intent(reactApplicationContext, activity.getClass()), PendingIntent.FLAG_UPDATE_CURRENT);

            builder.setContentIntent(contentIntent);

            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
                }
            });
        }

        NotificationManager notificationManager = (NotificationManager) reactApplicationContext.getSystemService(NOTIFICATION_SERVICE);

        Notification notification = builder.build();
        notification.defaults |= Notification.DEFAULT_SOUND;
        long[] vibrate = {0, 100, 200, 300};
        notification.vibrate = vibrate;

        notificationManager.notify(1, notification);
    }

}
