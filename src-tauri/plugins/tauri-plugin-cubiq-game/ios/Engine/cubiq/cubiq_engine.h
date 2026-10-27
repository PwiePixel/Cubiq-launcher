// Cubiq Launcher – C-Schnittstelle der iOS-Spiel-Engine (libcubiqengine.dylib).
// Swift lädt die Bibliothek zur Laufzeit (dlopen) und holt sich diese Funktionen per dlsym.
// Copyright (C) 2026 Ohev Tamerin (Theredstonee) – GPL-3.0-only

#pragma once

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/// Version dieser Schnittstelle (Swift prüft sie).
#define CUBIQ_ENGINE_API_VERSION 1
int cubiq_engine_api_version(void);

/// Rückrufe – kommen aus beliebigen Threads.
typedef void (*cubiq_log_cb)(const char *line);
/// Vor dem Prozessende. Bei Code != 0 wartet die Engine danach auf cubiq_engine_finish_exit().
typedef void (*cubiq_exit_cb)(int code);
typedef void (*cubiq_grab_cb)(int grabbing);
/// Erstes fertiges Bild (Spiel läuft).
typedef void (*cubiq_frame_cb)(void);
void cubiq_engine_set_callbacks(cubiq_log_cb log, cubiq_exit_cb on_exit, cubiq_grab_cb grab, cubiq_frame_cb first_frame);

/// Geräteprüfung.
int cubiq_engine_jit_enabled(void);
int cubiq_engine_cs_debugged(void);
uint32_t cubiq_engine_jit_flags(void);
int cubiq_engine_debugger_attached(void);
int cubiq_engine_entitlement(const char *key);
uint64_t cubiq_engine_available_memory_mb(void);
uint64_t cubiq_engine_max_contiguous_mb(uint64_t limit_mb);
/// 1, wenn in diesem Prozess schon eine JVM gestartet wurde.
int cubiq_engine_used(void);

/// Zeichenfläche: UIView (Layer CAMetalLayer bzw. CALayer bei Zink), per __bridge übergeben.
void cubiq_engine_set_surface(void *view);
/// Physische Pixel des Views und Spielauflösung (gerade Zahlen).
void cubiq_engine_set_window_size(int physical_width, int physical_height, int window_width, int window_height);

/// Startet die JVM in einem eigenen Thread. argv[0] = <javaHome>/bin/java, envp = "KEY=VALUE".
/// Alle Zeichenketten werden kopiert. Rückgabe 0 = gestartet, sonst Fehlercode (cubiq_engine_error_name).
int cubiq_engine_launch(const char *java_home, int java_major, int xmx_mb, const char *home_dir, const char *session,
                      int argc, const char *const *argv, int envc, const char *const *envp);
const char *cubiq_engine_error_name(int code);

/// Nach dem Absturz-Dialog: lässt den wartenden Thread den Prozess beenden.
void cubiq_engine_finish_exit(void);

/// Eingabe (GLFW-Codes). Koordinaten in Spielpixeln.
void cubiq_input_key(int key, int scancode, int down, int mods);
void cubiq_input_char(uint32_t codepoint);
void cubiq_input_mouse_button(int button, int down);
void cubiq_input_mouse_move_relative(float dx, float dy);
void cubiq_input_mouse_move_absolute(float x, float y);
void cubiq_input_scroll(float dx, float dy);
int cubiq_input_is_grabbing(void);
/// App geht in den Hintergrund: Spiel pausieren (ESC, wenn im Spiel).
void cubiq_engine_pause_if_needed(void);

#ifdef __cplusplus
}
#endif
