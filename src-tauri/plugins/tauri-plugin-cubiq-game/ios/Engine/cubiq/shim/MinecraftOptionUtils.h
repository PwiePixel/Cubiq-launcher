// Cubiq-Ersatz für Amethysts MinecraftOptionUtils.h (options.txt verwaltet der Cubiq-Kern).
// SPDX-License-Identifier: GPL-3.0-only

#pragma once

#import <Foundation/Foundation.h>

@interface MinecraftOptionUtils : NSObject
+ (instancetype)sharedInstance;
- (void)updateMCGuiScale;
@end
