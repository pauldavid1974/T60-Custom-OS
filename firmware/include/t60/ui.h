#ifndef T60_UI_H
#define T60_UI_H

#ifdef __cplusplus
extern "C" {
#endif

/* Portable, hardware-independent UI states. Not a target firmware image. */
typedef enum {
    T60_SCREEN_CLOCK = 0,
    T60_SCREEN_SETTINGS,
    T60_SCREEN_ABOUT
} t60_screen;

typedef enum {
    T60_EVENT_NONE = 0,
    T60_EVENT_OPEN_SETTINGS,
    T60_EVENT_OPEN_ABOUT,
    T60_EVENT_BACK
} t60_event;

typedef struct {
    t60_screen screen;
} t60_ui;

void t60_ui_init(t60_ui *ui);
void t60_ui_dispatch(t60_ui *ui, t60_event event);
const char *t60_ui_screen_name(t60_screen screen);

#ifdef __cplusplus
}
#endif

#endif
