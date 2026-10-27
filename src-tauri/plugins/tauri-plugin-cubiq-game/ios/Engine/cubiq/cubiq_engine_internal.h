// Cubiq Launcher – interne Haken zwischen Amethyst-Code und Cubiq-Engine.
// Copyright (C) 2026 Ohev Tamerin (Theredstonee) – GPL-3.0-only

#pragma once

#include "cubiq_engine.h"

/// Aus input_bridge_v3.m (nativeSetGrabbing).
void cubiq_engine_grab_changed(int grabbing);
/// Aus main_hook.m (hooked_exit) und dem PLLogOutputView-Ersatz; nur der erste Aufruf zählt.
void cubiq_engine_will_exit(int code);
/// 1, wenn Swift einen Absturz-Dialog zeigt und die Engine auf cubiq_engine_finish_exit warten soll.
int cubiq_engine_wants_exit_ack(void);
/// Eine Log-Zeile weiterreichen (bereits ohne Session-ID).
void cubiq_engine_log_line(const char *line);
/// Erstes Bild nach dem Start.
void cubiq_engine_first_frame(void);
