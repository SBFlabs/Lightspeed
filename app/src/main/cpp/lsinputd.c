#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <signal.h>
#include <time.h>
#include <poll.h>
#include <sys/ioctl.h>
#include <sys/time.h>
#include <linux/input.h>
#include <linux/uinput.h>

#define BITS_PER_LONG (sizeof(unsigned long) * 8)

enum ForwardMode {
    MODE_FORWARD = 0,
    MODE_SWALLOW = 1
};

static volatile sig_atomic_t g_stop_requested = 0;
static struct timespec g_start_ts;

static void handle_signal(int sig) {
    (void)sig;
    g_stop_requested = 1;
}

static long get_ms_since_start(void) {
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    long sec = now.tv_sec - g_start_ts.tv_sec;
    long nsec = now.tv_nsec - g_start_ts.tv_nsec;
    return sec * 1000 + nsec / 1000000;
}

static time_t get_monotonic_sec(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec;
}

static void send_power_down(int uinput_fd) {
    struct input_event ev_down, ev_syn;
    struct timeval tv;
    gettimeofday(&tv, NULL);

    memset(&ev_down, 0, sizeof(ev_down));
    ev_down.time = tv;
    ev_down.type = EV_KEY;
    ev_down.code = KEY_POWER;
    ev_down.value = 1;

    memset(&ev_syn, 0, sizeof(ev_syn));
    ev_syn.time = tv;
    ev_syn.type = EV_SYN;
    ev_syn.code = SYN_REPORT;
    ev_syn.value = 0;

    if (write(uinput_fd, &ev_down, sizeof(ev_down)) != (ssize_t)sizeof(ev_down)) {
        fprintf(stderr, "write ev_down failed in send_power_down: %s\n", strerror(errno));
    }
    if (write(uinput_fd, &ev_syn, sizeof(ev_syn)) != (ssize_t)sizeof(ev_syn)) {
        fprintf(stderr, "write ev_syn failed in send_power_down: %s\n", strerror(errno));
    }
}

static void send_power_up(int uinput_fd) {
    struct input_event ev_up, ev_syn;
    struct timeval tv;
    gettimeofday(&tv, NULL);

    memset(&ev_up, 0, sizeof(ev_up));
    ev_up.time = tv;
    ev_up.type = EV_KEY;
    ev_up.code = KEY_POWER;
    ev_up.value = 0;

    memset(&ev_syn, 0, sizeof(ev_syn));
    ev_syn.time = tv;
    ev_syn.type = EV_SYN;
    ev_syn.code = SYN_REPORT;
    ev_syn.value = 0;

    if (write(uinput_fd, &ev_up, sizeof(ev_up)) != (ssize_t)sizeof(ev_up)) {
        fprintf(stderr, "write ev_up failed in send_power_up: %s\n", strerror(errno));
    }
    if (write(uinput_fd, &ev_syn, sizeof(ev_syn)) != (ssize_t)sizeof(ev_syn)) {
        fprintf(stderr, "write ev_syn failed in send_power_up: %s\n", strerror(errno));
    }
}

static void send_power_tap(int uinput_fd) {
    struct input_event ev_down, ev_syn_down, ev_up, ev_syn_up;
    struct timeval tv;
    gettimeofday(&tv, NULL);

    memset(&ev_down, 0, sizeof(ev_down));
    ev_down.time = tv;
    ev_down.type = EV_KEY;
    ev_down.code = KEY_POWER;
    ev_down.value = 1;

    memset(&ev_syn_down, 0, sizeof(ev_syn_down));
    ev_syn_down.time = tv;
    ev_syn_down.type = EV_SYN;
    ev_syn_down.code = SYN_REPORT;
    ev_syn_down.value = 0;

    if (write(uinput_fd, &ev_down, sizeof(ev_down)) != (ssize_t)sizeof(ev_down)) {
        fprintf(stderr, "write ev_down failed in send_power_tap: %s\n", strerror(errno));
    }
    if (write(uinput_fd, &ev_syn_down, sizeof(ev_syn_down)) != (ssize_t)sizeof(ev_syn_down)) {
        fprintf(stderr, "write ev_syn_down failed in send_power_tap: %s\n", strerror(errno));
    }

    usleep(40000); // ~40 ms delay

    gettimeofday(&tv, NULL);
    memset(&ev_up, 0, sizeof(ev_up));
    ev_up.time = tv;
    ev_up.type = EV_KEY;
    ev_up.code = KEY_POWER;
    ev_up.value = 0;

    memset(&ev_syn_up, 0, sizeof(ev_syn_up));
    ev_syn_up.time = tv;
    ev_syn_up.type = EV_SYN;
    ev_syn_up.code = SYN_REPORT;
    ev_syn_up.value = 0;

    if (write(uinput_fd, &ev_up, sizeof(ev_up)) != (ssize_t)sizeof(ev_up)) {
        fprintf(stderr, "write ev_up failed in send_power_tap: %s\n", strerror(errno));
    }
    if (write(uinput_fd, &ev_syn_up, sizeof(ev_syn_up)) != (ssize_t)sizeof(ev_syn_up)) {
        fprintf(stderr, "write ev_syn_up failed in send_power_tap: %s\n", strerror(errno));
    }
}

int main(int argc, char **argv) {
    clock_gettime(CLOCK_MONOTONIC, &g_start_ts);

    if (argc < 3) {
        fprintf(stderr, "Usage: %s <input device path | auto> <max seconds>\n", argv[0]);
        printf("LSINPUTD_EXIT reason=invalid usage\n");
        fflush(stdout);
        return 1;
    }

    const char *src_path_arg = argv[1];
    int max_seconds = atoi(argv[2]);

    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = handle_signal;
    sigaction(SIGINT, &sa, NULL);
    sigaction(SIGTERM, &sa, NULL);

    int src_fd = -1;
    char dev_name[256] = "Unknown";
    char src_path_buf[128] = "";

    if (strcmp(src_path_arg, "auto") == 0) {
        int best_fd = -1;
        char best_path[128] = "";
        char best_name[256] = "";
        int best_score = -1;

        for (int i = 0; i <= 63; i++) {
            char path[64];
            snprintf(path, sizeof(path), "/dev/input/event%d", i);

            int fd = open(path, O_RDONLY);
            if (fd < 0) continue;

            char name[256] = "Unknown";
            if (ioctl(fd, EVIOCGNAME(sizeof(name)), name) < 0) {
                name[0] = '\0';
            }

            unsigned long ev_bits[(EV_MAX + BITS_PER_LONG - 1) / BITS_PER_LONG];
            memset(ev_bits, 0, sizeof(ev_bits));
            if (ioctl(fd, EVIOCGBIT(0, sizeof(ev_bits)), ev_bits) < 0) {
                close(fd);
                continue;
            }

            int has_ev_abs = (ev_bits[EV_ABS / BITS_PER_LONG] & (1UL << (EV_ABS % BITS_PER_LONG))) != 0;
            if (has_ev_abs) {
                close(fd);
                continue;
            }

            unsigned long key_bits[(KEY_MAX + BITS_PER_LONG - 1) / BITS_PER_LONG];
            memset(key_bits, 0, sizeof(key_bits));
            if (ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(key_bits)), key_bits) < 0) {
                close(fd);
                continue;
            }

            int has_key_power = (key_bits[KEY_POWER / BITS_PER_LONG] & (1UL << (KEY_POWER % BITS_PER_LONG))) != 0;
            if (!has_key_power) {
                close(fd);
                continue;
            }

            char lower_name[256];
            size_t nlen = strlen(name);
            for (size_t k = 0; k < nlen && k < sizeof(lower_name) - 1; k++) {
                lower_name[k] = (char)tolower((unsigned char)name[k]);
            }
            lower_name[nlen < sizeof(lower_name) - 1 ? nlen : sizeof(lower_name) - 1] = '\0';

            if (strncmp(lower_name, "lsinputd", 8) == 0 ||
                strstr(lower_name, "uinput") || strstr(lower_name, "virtual") ||
                strstr(lower_name, "touch") || strstr(lower_name, "goodix") ||
                strstr(lower_name, "fts") || strstr(lower_name, "tpd")) {
                close(fd);
                continue;
            }

            struct input_id dev_id_check;
            if (ioctl(fd, EVIOCGID, &dev_id_check) == 0 && dev_id_check.bustype == BUS_VIRTUAL) {
                close(fd);
                continue;
            }

            int has_vol_keys = ((key_bits[KEY_VOLUMEUP / BITS_PER_LONG] & (1UL << (KEY_VOLUMEUP % BITS_PER_LONG))) != 0) ||
                               ((key_bits[KEY_VOLUMEDOWN / BITS_PER_LONG] & (1UL << (KEY_VOLUMEDOWN % BITS_PER_LONG))) != 0);

            int is_pref = (strstr(lower_name, "pmic") != NULL ||
                           strstr(lower_name, "pwrkey") != NULL ||
                           strstr(lower_name, "power") != NULL ||
                           strstr(lower_name, "gpio-keys") != NULL ||
                           strstr(lower_name, "qpnp_pon") != NULL ||
                           strstr(lower_name, "pon") != NULL);

            int score = (is_pref ? 10 : 0) + (!has_vol_keys ? 5 : 0);

            if (score > best_score) {
                if (best_fd >= 0) close(best_fd);
                best_fd = fd;
                snprintf(best_path, sizeof(best_path), "%s", path);
                snprintf(best_name, sizeof(best_name), "%s", name[0] ? name : "Unknown");
                best_score = score;
            } else {
                close(fd);
            }
        }

        if (best_fd < 0) {
            printf("LSINPUTD_EXIT reason=no power device found\n");
            fflush(stdout);
            return 1;
        }

        src_fd = best_fd;
        snprintf(src_path_buf, sizeof(src_path_buf), "%s", best_path);
        snprintf(dev_name, sizeof(dev_name), "%s", best_name);
        printf("LSINPUTD_DEV %s %s\n", src_path_buf, dev_name);
        fflush(stdout);
    } else {
        snprintf(src_path_buf, sizeof(src_path_buf), "%s", src_path_arg);
        src_fd = open(src_path_buf, O_RDONLY);
        if (src_fd < 0) {
            fprintf(stderr, "open(%s, O_RDONLY) failed: %s\n", src_path_buf, strerror(errno));
            printf("LSINPUTD_EXIT reason=open source device failed\n");
            fflush(stdout);
            return 1;
        }

        if (ioctl(src_fd, EVIOCGNAME(sizeof(dev_name)), dev_name) < 0) {
            fprintf(stderr, "EVIOCGNAME failed: %s\n", strerror(errno));
        }
    }

    struct input_id dev_id;
    memset(&dev_id, 0, sizeof(dev_id));
    if (ioctl(src_fd, EVIOCGID, &dev_id) < 0) {
        fprintf(stderr, "EVIOCGID failed: %s\n", strerror(errno));
        close(src_fd);
        printf("LSINPUTD_EXIT reason=EVIOCGID failed\n");
        fflush(stdout);
        return 1;
    }

    unsigned long key_bits[(KEY_MAX + BITS_PER_LONG - 1) / BITS_PER_LONG];
    memset(key_bits, 0, sizeof(key_bits));
    if (ioctl(src_fd, EVIOCGBIT(EV_KEY, sizeof(key_bits)), key_bits) < 0) {
        fprintf(stderr, "EVIOCGBIT(EV_KEY) failed: %s\n", strerror(errno));
        close(src_fd);
        printf("LSINPUTD_EXIT reason=EVIOCGBIT EV_KEY failed\n");
        fflush(stdout);
        return 1;
    }

    int uinput_fd = open("/dev/uinput", O_WRONLY);
    if (uinput_fd < 0) {
        fprintf(stderr, "open(/dev/uinput, O_WRONLY) failed: %s\n", strerror(errno));
        close(src_fd);
        printf("LSINPUTD_EXIT reason=open uinput failed\n");
        fflush(stdout);
        return 1;
    }

    if (ioctl(uinput_fd, UI_SET_EVBIT, EV_KEY) < 0) {
        fprintf(stderr, "UI_SET_EVBIT(EV_KEY) failed: %s\n", strerror(errno));
        close(uinput_fd);
        close(src_fd);
        printf("LSINPUTD_EXIT reason=UI_SET_EVBIT EV_KEY failed\n");
        fflush(stdout);
        return 1;
    }

    if (ioctl(uinput_fd, UI_SET_EVBIT, EV_SYN) < 0) {
        fprintf(stderr, "UI_SET_EVBIT(EV_SYN) failed: %s\n", strerror(errno));
        close(uinput_fd);
        close(src_fd);
        printf("LSINPUTD_EXIT reason=UI_SET_EVBIT EV_SYN failed\n");
        fflush(stdout);
        return 1;
    }

    for (int k = 0; k <= KEY_MAX; k++) {
        if ((key_bits[k / BITS_PER_LONG] & (1UL << (k % BITS_PER_LONG))) != 0) {
            if (ioctl(uinput_fd, UI_SET_KEYBIT, k) < 0) {
                fprintf(stderr, "UI_SET_KEYBIT(%d) failed: %s\n", k, strerror(errno));
                close(uinput_fd);
                close(src_fd);
                printf("LSINPUTD_EXIT reason=UI_SET_KEYBIT failed\n");
                fflush(stdout);
                return 1;
            }
        }
    }

    struct uinput_setup usetup;
    memset(&usetup, 0, sizeof(usetup));
    usetup.id.bustype = dev_id.bustype;
    usetup.id.vendor = dev_id.vendor;
    usetup.id.product = dev_id.product;
    usetup.id.version = dev_id.version;
    snprintf(usetup.name, sizeof(usetup.name), "lsinputd-fwd");

    if (ioctl(uinput_fd, UI_DEV_SETUP, &usetup) < 0) {
        fprintf(stderr, "UI_DEV_SETUP failed: %s\n", strerror(errno));
        close(uinput_fd);
        close(src_fd);
        printf("LSINPUTD_EXIT reason=UI_DEV_SETUP failed\n");
        fflush(stdout);
        return 1;
    }

    if (ioctl(uinput_fd, UI_DEV_CREATE) < 0) {
        fprintf(stderr, "UI_DEV_CREATE failed: %s\n", strerror(errno));
        close(uinput_fd);
        close(src_fd);
        printf("LSINPUTD_EXIT reason=UI_DEV_CREATE failed\n");
        fflush(stdout);
        return 1;
    }

    if (ioctl(src_fd, EVIOCGRAB, 1) < 0) {
        fprintf(stderr, "EVIOCGRAB(1) failed: %s\n", strerror(errno));
        ioctl(uinput_fd, UI_DEV_DESTROY);
        close(uinput_fd);
        close(src_fd);
        printf("LSINPUTD_EXIT reason=EVIOCGRAB 1 failed\n");
        fflush(stdout);
        return 1;
    }

    int flags = fcntl(STDIN_FILENO, F_GETFL, 0);
    if (flags >= 0) {
        fcntl(STDIN_FILENO, F_SETFL, flags | O_NONBLOCK);
    }

    enum ForwardMode mode = MODE_FORWARD;
    time_t start_time = get_monotonic_sec();
    int power_held = 0;
    time_t power_press_time = 0;
    const char *exit_reason = NULL;
    int current_frame_forwarded = 0;
    int uinput_power_is_down = 0;

    char cmd_line[256];
    size_t cmd_len = 0;

    struct pollfd fds[2];
    fds[0].fd = src_fd;
    fds[0].events = POLLIN;
    fds[1].fd = STDIN_FILENO;
    fds[1].events = POLLIN;

    while (!g_stop_requested) {
        time_t now = get_monotonic_sec();
        if (max_seconds > 0 && (now - start_time) >= max_seconds) {
            exit_reason = "max seconds elapsed";
            break;
        }
        if (power_held && (now - power_press_time) >= 10) {
            exit_reason = "KEY_POWER held 10s";
            break;
        }

        int ret = poll(fds, 2, 200);
        if (ret < 0) {
            if (errno == EINTR) {
                continue;
            }
            fprintf(stderr, "poll failed: %s\n", strerror(errno));
            exit_reason = "poll error";
            break;
        }

        if (fds[1].revents & (POLLIN | POLLHUP | POLLERR)) {
            char inbuf[128];
            ssize_t bytes_in = read(STDIN_FILENO, inbuf, sizeof(inbuf));
            if (bytes_in == 0) {
                exit_reason = "stdin closed";
                break;
            } else if (bytes_in < 0) {
                if (errno != EAGAIN && errno != EINTR) {
                    fprintf(stderr, "stdin read error: %s\n", strerror(errno));
                    exit_reason = "stdin read error";
                    break;
                }
            } else {
                int quit_requested = 0;
                for (ssize_t i = 0; i < bytes_in; i++) {
                    char ch = inbuf[i];
                    if (ch == '\n' || ch == '\r') {
                        cmd_line[cmd_len] = '\0';
                        if (cmd_len > 0) {
                            if (strcmp(cmd_line, "MODE swallow") == 0) {
                                mode = MODE_SWALLOW;
                            } else if (strcmp(cmd_line, "MODE forward") == 0) {
                                mode = MODE_FORWARD;
                            } else if (strcmp(cmd_line, "TAP") == 0) {
                                send_power_tap(uinput_fd);
                                uinput_power_is_down = 0;
                            } else if (strcmp(cmd_line, "DOWN") == 0) {
                                send_power_down(uinput_fd);
                                uinput_power_is_down = 1;
                            } else if (strcmp(cmd_line, "UP") == 0) {
                                send_power_up(uinput_fd);
                                uinput_power_is_down = 0;
                            } else if (strcmp(cmd_line, "QUIT") == 0) {
                                exit_reason = "QUIT";
                                quit_requested = 1;
                                break;
                            }
                        }
                        cmd_len = 0;
                    } else {
                        if (cmd_len < sizeof(cmd_line) - 1) {
                            cmd_line[cmd_len++] = ch;
                        }
                    }
                }
                if (quit_requested) {
                    break;
                }
            }
        }

        if (fds[0].revents & POLLIN) {
            struct input_event evs[16];
            ssize_t bytes_read = read(src_fd, evs, sizeof(evs));
            if (bytes_read < 0) {
                if (errno == EAGAIN || errno == EINTR) {
                    continue;
                }
                fprintf(stderr, "read failed: %s\n", strerror(errno));
                exit_reason = "read error";
                break;
            }

            size_t count = (size_t)bytes_read / sizeof(struct input_event);
            int write_failed = 0;
            for (size_t i = 0; i < count; i++) {
                struct input_event *ev = &evs[i];

                if (ev->type == EV_SYN && ev->code == SYN_DROPPED) {
                    current_frame_forwarded = 0;
                    if (uinput_power_is_down) {
                        send_power_up(uinput_fd);
                        uinput_power_is_down = 0;
                    }
                } else if (ev->type == EV_SYN && ev->code == SYN_REPORT) {
                    if (current_frame_forwarded > 0 || mode == MODE_FORWARD) {
                        ssize_t written = write(uinput_fd, ev, sizeof(struct input_event));
                        if (written != (ssize_t)sizeof(struct input_event)) {
                            fprintf(stderr, "write failed: %s\n", strerror(errno));
                            exit_reason = "write error";
                            write_failed = 1;
                            break;
                        }
                    }
                    current_frame_forwarded = 0;
                } else if (ev->type == EV_KEY && ev->code == KEY_POWER) {
                    if (ev->value == 1) {
                        if (!power_held) {
                            power_held = 1;
                            power_press_time = get_monotonic_sec();
                        }
                        printf("P DOWN %ld\n", get_ms_since_start());
                        fflush(stdout);
                    } else if (ev->value == 0) {
                        power_held = 0;
                        power_press_time = 0;
                        printf("P UP %ld\n", get_ms_since_start());
                        fflush(stdout);
                    }

                    if (mode == MODE_FORWARD) {
                        ssize_t written = write(uinput_fd, ev, sizeof(struct input_event));
                        if (written != (ssize_t)sizeof(struct input_event)) {
                            fprintf(stderr, "write failed: %s\n", strerror(errno));
                            exit_reason = "write error";
                            write_failed = 1;
                            break;
                        }
                        current_frame_forwarded++;
                        if (ev->value == 1 || ev->value == 2) {
                            uinput_power_is_down = 1;
                        } else if (ev->value == 0) {
                            uinput_power_is_down = 0;
                        }
                    }
                } else {
                    ssize_t written = write(uinput_fd, ev, sizeof(struct input_event));
                    if (written != (ssize_t)sizeof(struct input_event)) {
                        fprintf(stderr, "write failed: %s\n", strerror(errno));
                        exit_reason = "write error";
                        write_failed = 1;
                        break;
                    }
                    current_frame_forwarded++;
                }
            }
            if (write_failed) {
                break;
            }
        }
    }

    if (!exit_reason) {
        if (g_stop_requested) {
            exit_reason = "signal received";
        } else {
            exit_reason = "normal completion";
        }
    }

    if (uinput_power_is_down) {
        send_power_up(uinput_fd);
        uinput_power_is_down = 0;
    }

    ioctl(src_fd, EVIOCGRAB, 0);
    ioctl(uinput_fd, UI_DEV_DESTROY);
    close(uinput_fd);
    close(src_fd);

    printf("LSINPUTD_EXIT reason=%s\n", exit_reason);
    fflush(stdout);

    return (strcmp(exit_reason, "max seconds elapsed") == 0 ||
            strcmp(exit_reason, "stdin closed") == 0 ||
            strcmp(exit_reason, "QUIT") == 0 ||
            strcmp(exit_reason, "signal received") == 0 ||
            strcmp(exit_reason, "KEY_POWER held 10s") == 0 ||
            strcmp(exit_reason, "normal completion") == 0) ? 0 : 1;
}
