package rnxmpp.service;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smackx.omemo.OmemoMessage;

/**
 * Created by Kristian Frølund on 7/19/16.
 * Copyright (c) 2016. Teletronics. All rights reserved
 */

public interface XmppServiceListener {
    void onError(Exception e);
    void onLoginError(String errorMessage);
    void onLoginError(Exception e);
    void onFileReceived(String fileUrl, String filePath, String key);
    void onMessage(Message message);
    void onOmemoMessage(Stanza stanza, OmemoMessage.Received decryptedMessage);
    void onNotificationOpened(String from);
    void onRosterReceived(Roster roster);
    void onIQ(IQ iq);
    void onPresence(Presence presence);
    void onConnnect(String username, String password);
    void onOmemoInitResult(boolean isSuccessfulInit);
    void onDisconnect(Exception e);
    void onLogin(String username, String password);
    void onOmemoOutgoingMessageResult(boolean isSent,String id);

}
