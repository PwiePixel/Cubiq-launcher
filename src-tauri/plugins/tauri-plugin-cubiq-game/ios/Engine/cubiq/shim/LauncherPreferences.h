// Cubiq-Ersatz für Amethysts LauncherPreferences.h: feste Standardwerte statt Einstellungsdatei.
// SPDX-License-Identifier: GPL-3.0-only

#pragma once

#import <UIKit/UIKit.h>

id getPrefObject(NSString *key);
BOOL getPrefBool(NSString *key);
float getPrefFloat(NSString *key);
NSInteger getPrefInt(NSString *key);

BOOL getEntitlementValue(NSString *key);
