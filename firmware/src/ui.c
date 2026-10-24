#include "t60/ui.h"

void t60_ui_init(t60_ui *ui) {
    if (ui != 0) {
        ui->screen = T60_SCREEN_CLOCK;
    }
}

void t60_ui_dispatch(t60_ui *ui, t60_event event) {
    if (ui == 0) {
        return;
    }

    switch (event) {
        case T60_EVENT_OPEN_SETTINGS:
            if (ui->screen == T60_SCREEN_CLOCK) {
                ui->screen = T60_SCREEN_SETTINGS;
            }
            break;
        case T60_EVENT_OPEN_ABOUT:
            if (ui->screen == T60_SCREEN_SETTINGS) {
                ui->screen = T60_SCREEN_ABOUT;
            }
            break;
        case T60_EVENT_BACK:
            if (ui->screen == T60_SCREEN_ABOUT) {
                ui->screen = T60_SCREEN_SETTINGS;
            } else {
                ui->screen = T60_SCREEN_CLOCK;
            }
            break;
        case T60_EVENT_NONE:
        default:
            break;
    }
}

const char *t60_ui_screen_name(t60_screen screen) {
    switch (screen) {
        case T60_SCREEN_CLOCK: return "Clock";
        case T60_SCREEN_SETTINGS: return "Settings";
        case T60_SCREEN_ABOUT: return "About";
        default: return "Unknown";
    }
}
