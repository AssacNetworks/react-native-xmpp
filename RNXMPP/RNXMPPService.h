//
//  RNXMPPService.h
//  RNXMPP
//
//  Created by Pavlo Aksonov on 24.09.15.
//  Copyright © 2015 Pavlo Aksonov. All rights reserved.
//

#import <Foundation/Foundation.h>

#import "XMPPFramework/XMPP.h"
//#import "XMPP.h"
#import "XMPPFramework/XMPPReconnect.h"
#import "XMPPFramework/XMPPDateTimeProfiles.h"
#import "XMPPFramework/NSDate+XMPPDateTimeProfiles.h"
#import "XMPPFramework/XMPPMUC.h"
#import "XMPPFramework/XMPPRoom.h"
#import "XMPPFramework/XMPPRoster.h"
#import "XMPPFramework/XMPPRosterMemoryStorage.h"
#import "RNXMPPConstants.h"
#import "XMPPFramework/XMPPStreamManagement.h"
#import "XMPPFramework/XMPPStreamManagementMemoryStorage.h"
#import "OMEMOStorage.h"

@protocol RNXMPPServiceDelegate <NSObject>

-(void)onError:(NSError *)error;
-(void)onMessage:(XMPPMessage *)message;
-(void)onPresence:(XMPPPresence *)presence;
-(void)onIQ:(XMPPIQ *)iq;
-(void)onRosterReceived:(NSArray *)list;
-(void)onDisconnect:(NSError *)error;
-(void)onConnnect:(NSString *)username password:(NSString *)password;
-(void)onOmemoInitResult:(BOOL)isSuccessfulInit;
-(void)onLogin:(NSString *)username password:(NSString *)password;
-(void)onLoginError:(NSError *)error;

@end

@interface RNXMPPService : NSObject
{
    XMPPStream *xmppStream;
    XMPPRoster *xmppRoster;
    XMPPRosterMemoryStorage *xmppRosterStorage;
    XMPPReconnect *xmppReconnect;
    OMEMOModule *omemoModule;
    OMEMOStorage *omemoStorage;
    XMPPStreamManagement *xmppStreamManagement;
    id<XMPPStreamManagementStorage> xmppStreamManagentStorage;
    XMPPMUC *xmppMUC;
    NSArray *trustedHosts;
    NSString *username;
    NSString *password;
    AuthMethod authMethod;
    BOOL customCertEvaluation;
    BOOL isXmppConnected;
}

@property (nonatomic, strong, readonly) XMPPStream *xmppStream;
@property (nonatomic, strong, readonly) XMPPReconnect *xmppReconnect;
@property (nonatomic, weak) id<RNXMPPServiceDelegate> delegate;

+(RNXMPPService *) sharedInstance;
- (void)trustHosts:(NSArray *)hosts;
- (BOOL)connect:(NSString *)myJID withPassword:(NSString *)myPassword auth:(AuthMethod)auth hostname:(NSString *)hostname port:(int)port;
- (void)disconnect;
- (void)disconnectAfterSending;
- (void)sendMessage:(NSString *)text to:(NSString *)username thread:(NSString *)thread;
- (void)sendPresence:(NSString *)to type:(NSString *)type;
- (void)removeRoster:(NSString *)to;
-(void)fetchRoster;
-(void)sendStanza:(NSString *)stanza;

@end

