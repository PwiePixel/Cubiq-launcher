// Cubiq-Ersatz für Amethysts SurfaceViewController.h: der Amethyst-Code braucht nur die
// Zeichenfläche, den Laufzustand und den Absturz-Wartepunkt. Die echte Spielansicht ist
// GameViewController (Swift).
// SPDX-License-Identifier: GPL-3.0-only

#pragma once

#import <UIKit/UIKit.h>
#import "UIKit+hook.h"
#import "PLLogOutputView.h"

extern BOOL canAppendToLog;
extern dispatch_group_t fatalExitGroup;

@interface SurfaceViewController : NSObject
/// View, dessen Layer gerendert wird (gesetzt über cubiq_engine_set_surface).
+ (UIView *)surface;
+ (BOOL)isRunning;
- (void)updateGrabState;
@end
