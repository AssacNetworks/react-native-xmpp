package rnxmpp.service;

import com.facebook.react.bridge.ReadableArray;

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
import org.jivesoftware.smackx.httpfileupload.HttpFileUploadManager;
import org.jxmpp.stringprep.XmppStringprepException;
import org.jxmpp.jid.impl.JidCreate;

import android.os.AsyncTask;

import java.io.IOException;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import 	android.util.Log;

import rnxmpp.ssl.UnsafeSSLContext;


/**
 * Created by Kristian Frølund on 7/19/16.
 * Copyright (c) 2016. Teletronics. All rights reserved
 */

public class XmppServiceSmackImpl implements XmppService,ChatManagerListener, StanzaListener, ConnectionListener, ChatMessageListener, RosterLoadedListener {
    XmppServiceListener xmppServiceListener;
    Logger logger = Logger.getLogger(XmppServiceSmackImpl.class.getName());

    XMPPTCPConnection connection;
    Roster roster;
    List<String> trustedHosts = new ArrayList<>();
    String password;
//    FileTransferManager manager;

    public XmppServiceSmackImpl(XmppServiceListener xmppServiceListener) {
        this.xmppServiceListener = xmppServiceListener;
    }

    @Override
    public void trustHosts(ReadableArray trustedHosts) {
        for(int i = 0; i < trustedHosts.size(); i++){
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
        if (serviceNameParts.length>1){
                confBuilder.setResource(serviceNameParts[1]);
        } else {
            confBuilder.setResource(Long.toHexString(Double.doubleToLongBits(Math.random())));
        }
        }
         catch (XmppStringprepException e) {
            e.printStackTrace();
        }
        if (hostname != null){
            confBuilder.setHost(hostname);
        }
        if (port != null){
            confBuilder.setPort(port);
        }
        if (trustedHosts.contains(hostname) || (hostname == null && trustedHosts.contains(serviceName))){
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
        //         transfer.recieveFile(new File("/storage/emulated/0/" + request.getFileName()));
        //     } catch(IOException | SmackException ex) {

        //     }
        // }
        // });
        
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    connection.connect().login();
                } catch (XMPPException | SmackException | InterruptedException | IOException e) {
                    logger.log(Level.SEVERE, "Could not login for user " + jidParts[0], e);
                    if (e instanceof SASLErrorException){
                        XmppServiceSmackImpl.this.xmppServiceListener.onLoginError(((SASLErrorException) e).getSASLFailure().toString());
                    }else{
                        XmppServiceSmackImpl.this.xmppServiceListener.onError(e);
                    }

                }
                return null;
            }

            @Override
            protected void onPostExecute(Void dummy) {

            }
        }.execute();
    }

    @Override
    public void message(String text, String to, String thread) {
        String chatIdentifier = (thread == null ? to : thread);

        ChatManager chatManager = ChatManager.getInstanceFor(connection);
        Chat chat = chatManager.getThreadChat(chatIdentifier);
        if (chat == null) {
            try {
            if (thread == null){
                    chat = chatManager.createChat(JidCreate.entityBareFrom(to), this);
            }else{
                chat = chatManager.createChat(JidCreate.entityBareFrom(to), thread, this);
            }
            } catch (XmppStringprepException e) {
                e.printStackTrace();
            }
        }
        try {
            chat.sendMessage(text);
        } catch (SmackException | InterruptedException e) {
            logger.log(Level.WARNING, "Could not send message", e);
        }
    }

    @Override
    public void sendFile(String fileURI) {
        HttpFileUploadManager manager = HttpFileUploadManager.getInstanceFor(connection);
        try {
            URL fileURL = manager.uploadFile(new File(fileURI));
            this.xmppServiceListener.onFileReceived(fileURL.toString(), fileURI);
        } catch (SmackException | InterruptedException | IOException | XMPPException e) {
            logger.log(Level.WARNING, "Could not upload and send file", e);
        }
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
        if (rosterEntry != null){
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
        if (packet instanceof IQ){
            this.xmppServiceListener.onIQ((IQ) packet);
        }else if (packet instanceof Presence){
            this.xmppServiceListener.onPresence((Presence) packet);
        }else{
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
        this.xmppServiceListener.onMessage(message);
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

}
