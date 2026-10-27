// Cubiq (not from Amethyst/SDL): SDLInputConnection is package-private; the Cubiq engine commits
// typed text (touch keyboard, overlay) through this bridge. Part of the Cubiq Launcher (GPL-3.0-or-later).
package org.libsdl.app;

public final class CubiqSdlText {
    private CubiqSdlText() {}

    /** Text as SDL_EVENT_TEXT_INPUT (only delivered while the game has text input active). */
    public static void commit(String text) {
        SDLInputConnection.nativeCommitText(text, 1);
    }
}
