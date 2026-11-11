#include "guest_pages.h"
#include <stdio.h>
int main(void) {
    char report[2048];
    int status = gpl_run_selftests(report, sizeof report);
    fputs(report, stdout);
    return status;
}
