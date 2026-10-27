// Cubiq-Ersatz für Amethysts UIKit+hook.h: nur UIWindow.mainWindow wird gebraucht.
// SPDX-License-Identifier: GPL-3.0-only

#pragma once

#import <UIKit/UIKit.h>

@interface UIWindow (CubiqMainWindow)
+ (UIWindow *)mainWindow;
@end
