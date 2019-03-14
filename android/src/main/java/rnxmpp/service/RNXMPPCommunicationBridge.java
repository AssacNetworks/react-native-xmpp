package rnxmpp.service;

import android.support.annotation.Nullable;

import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactNativeHost;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.RCTNativeAppEventEmitter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.RosterGroup;
import org.jivesoftware.smackx.omemo.OmemoMessage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;

import rnxmpp.utils.Parser;

/**
 * Created by Kristian Frølund on 7/19/16.
 * Copyright (c) 2016. Teletronics. All rights reserved
 */

public class RNXMPPCommunicationBridge implements XmppServiceListener {

    public static final String RNXMPP_ERROR = "RNXMPPError";
    public static final String RNXMPP_LOGIN_ERROR = "RNXMPPLoginError";
    public static final String RNXMPP_MESSAGE = "RNXMPPMessage";
    public static final String RNXMPP_NOTIFICATION_OPENED = "RNXMPPNotificationOpened";
    public static final String RNXMPP_ROSTER = "RNXMPPRoster";
    public static final String RNXMPP_IQ = "RNXMPPIQ";
    public static final String RNXMPP_PRESENCE = "RNXMPPPresence";
    public static final String RNXMPP_CONNECT = "RNXMPPConnect";
    public static final String RNXMPP_DISCONNECT = "RNXMPPDisconnect";
    public static final String RNXMPP_LOGIN = "RNXMPPLogin";
    public static final String RNXMPP_FILE = "RNXMPPFile";
    public static final String RNXMPP_OMEMO_INIT_RESULT = "RNXMPPOmemoInitResult";
    public static final String RNXMPP_RECEPIT = "RNXMPPRecepit";
    private static final String TEMP_MESSAGE_FILE = ".assactempmesssage";
    public static final String RNXMPP_OMEMO_OUTGOING_MESSAGE_RESULT = "RNXMPPOmemoOutgoingMessageResult";

    ReactContext reactContext;

    public RNXMPPCommunicationBridge(ReactContext reactContext) {
        this.reactContext = reactContext;
    }

    @Override
    public void onFileReceived(String fileUrl, String filePath, String key) {
        WritableMap presenceMap = Arguments.createMap();
        presenceMap.putString("fileUrl", fileUrl);
        presenceMap.putString("filePath", filePath);
        presenceMap.putString("key", key);
        sendEvent(reactContext, RNXMPP_FILE, presenceMap);
    }

    @Override
    public void onError(Exception e) {
        sendEvent(reactContext, RNXMPP_ERROR, e.getLocalizedMessage());
    }

    @Override
    public void onLoginError(String errorMessage) {
        sendEvent(reactContext, RNXMPP_LOGIN_ERROR, errorMessage);
    }

    @Override
    public void onLoginError(Exception e) {
        this.onLoginError(e.getLocalizedMessage());
    }

    @Override
    public void onMessage(Message message) {
        WritableMap params = Arguments.createMap();
        params.putString("thread", message.getThread());
        params.putString("subject", message.getSubject());
        params.putString("body", message.getBody());
        params.putString("from", message.getFrom().toString());
        params.putString("src", message.toString());
        sendEvent(reactContext, RNXMPP_MESSAGE, params);
    }

    @Override
    public void onOmemoMessage(Stanza stanza, OmemoMessage.Received decryptedMessage) {
        WritableMap params = Arguments.createMap();
        params.putString("body", decryptedMessage.getBody());
        params.putString("from", stanza.getFrom().toString());
        params.putString("src", stanza.toString());
        sendEvent(reactContext, RNXMPP_MESSAGE, params);
    }

    @Override
    public void onNotificationOpened(String from) {
        WritableMap params = Arguments.createMap();
        params.putString("from", from);
        sendEvent(reactContext, RNXMPP_NOTIFICATION_OPENED, params);

    }

    @Override 
    public void onMessageReceipt(String receiptId) {
        WritableMap params = Arguments.createMap();
       params.putString("recepitId", receiptId);
        sendEvent(reactContext, RNXMPP_RECEPIT, params);
    }

    @Override
    public void onRosterReceived(Roster roster) {
        WritableArray rosterResponse = Arguments.createArray();
        for (RosterEntry rosterEntry : roster.getEntries()) {
            WritableMap rosterProps = Arguments.createMap();
            rosterProps.putString("username", rosterEntry.getUser());
            rosterProps.putString("displayName", rosterEntry.getName());
            WritableArray groupArray = Arguments.createArray();
            for (RosterGroup rosterGroup : rosterEntry.getGroups()) {
                groupArray.pushString(rosterGroup.getName());
            }
            rosterProps.putArray("groups", groupArray);
            rosterProps.putString("subscription", rosterEntry.getType().toString());
            rosterResponse.pushMap(rosterProps);
        }
        sendEvent(reactContext, RNXMPP_ROSTER, rosterResponse);
    }

    @Override
    public void onIQ(IQ iq) {
        sendEvent(reactContext, RNXMPP_IQ, Parser.parse(iq.toString()));
    }

    @Override
    public void onPresence(Presence presence) {
        WritableMap presenceMap = Arguments.createMap();
        presenceMap.putString("type", presence.getType().toString());
        presenceMap.putString("from", presence.getFrom().toString());
        presenceMap.putString("status", presence.getStatus());
        presenceMap.putString("mode", presence.getMode().toString());
        sendEvent(reactContext, RNXMPP_PRESENCE, presenceMap);
    }

    @Override
    public void onConnnect(String username, String password) {
        WritableMap params = Arguments.createMap();
        params.putString("username", username);
        params.putString("password", password);
        sendEvent(reactContext, RNXMPP_CONNECT, params);
    }

    @Override
    public void onOmemoInitResult(boolean isSuccessfulInit) {
        WritableMap params = Arguments.createMap();
        params.putString("isSuccssesfulInit", isSuccessfulInit ? "true" : "false");
        sendEvent(reactContext, RNXMPP_OMEMO_INIT_RESULT, params);
    }

    @Override
    public void onDisconnect(Exception e) {
        if (e != null) {
            sendEvent(reactContext, RNXMPP_DISCONNECT, e.getLocalizedMessage());
        } else {
            sendEvent(reactContext, RNXMPP_DISCONNECT, null);
        }
    }

    @Override
    public void onLogin(String username, String password) {
        WritableMap params = Arguments.createMap();
        params.putString("username", username);
        params.putString("password", password);
        sendEvent(reactContext, RNXMPP_LOGIN, params);
    }

    @Override
    public void onOmemoOutgoingMessageResult(boolean isSent, String id) {
        WritableMap params = Arguments.createMap();
        params.putString("isSent", isSent ? "true" : "false");
        params.putString("_id", id);
        sendEvent(reactContext, RNXMPP_OMEMO_OUTGOING_MESSAGE_RESULT, params);
    }

    void sendEvent(ReactContext reactContext, String eventName, @Nullable Object params) {
//        try
//        {
            reactContext
                    .getJSModule(RCTNativeAppEventEmitter.class)
                    .emit(eventName, params);
//        }
//        catch (Exception e)
//        {
//            try {
//                createMessageFile(params);
//            } catch (IOException e1) {
//                e1.printStackTrace();
//            }
//        }

    }

//    private void createMessageFile(@Nullable Object message) throws IOException {
//        File tempFile = new File(reactContext.getCacheDir().getPath() + "/" + new Date().toString() + TEMP_MESSAGE_FILE);
//
//        ObjectMapper mapper = new ObjectMapper();
//
//        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
//
//        String messageAsJson = mapper.writeValueAsString(message);
//
//        FileWriter writer = null;
//        try {
//            writer = new FileWriter(tempFile);
//
//            writer.write(messageAsJson);
//
//            writer.close();
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
}
