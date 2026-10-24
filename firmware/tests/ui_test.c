#include "t60/ui.h"
#include <assert.h>
#include <string.h>

int main(void) {
    t60_ui ui;
    t60_ui_init(&ui);
    assert(ui.screen == T60_SCREEN_CLOCK);
    assert(strcmp(t60_ui_screen_name(ui.screen), "Clock") == 0);

    t60_ui_dispatch(&ui, T60_EVENT_OPEN_ABOUT);
    assert(ui.screen == T60_SCREEN_CLOCK); /* About only via Settings */

    t60_ui_dispatch(&ui, T60_EVENT_OPEN_SETTINGS);
    assert(ui.screen == T60_SCREEN_SETTINGS);

    t60_ui_dispatch(&ui, T60_EVENT_OPEN_ABOUT);
    assert(ui.screen == T60_SCREEN_ABOUT);

    t60_ui_dispatch(&ui, T60_EVENT_BACK);
    assert(ui.screen == T60_SCREEN_SETTINGS);

    t60_ui_dispatch(&ui, T60_EVENT_BACK);
    assert(ui.screen == T60_SCREEN_CLOCK);

    t60_ui_dispatch(&ui, T60_EVENT_NONE);
    assert(ui.screen == T60_SCREEN_CLOCK);

    t60_ui_init(0);
    t60_ui_dispatch(0, T60_EVENT_BACK);
    assert(strcmp(t60_ui_screen_name((t60_screen)999), "Unknown") == 0);
    return 0;
}
