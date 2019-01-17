//
//  OMEMOStorage.h
//  RNXMPP
//
//  Created by Shabtai Dvir on 17/12/2018.
//  Copyright © 2018 Pavlo Aksonov. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "XMPPFramework/OMEMOModule.h"

NS_ASSUME_NONNULL_BEGIN
@interface OMEMOStorage : NSObject <OMEMOStorageDelegate>

//@property (nonatomic, strong, readonly) OMEMOBundle *myBundle;
//@property (nonatomic, weak, readonly) OMEMOModule *omemoModule;

- (instancetype) initWithMyBundle:(OMEMOBundle*)myBundle;

+ (OMEMOBundle*) testBundle:(OMEMOModuleNamespace)ns;

@end
NS_ASSUME_NONNULL_END

/* OMEMOStorage_h */
