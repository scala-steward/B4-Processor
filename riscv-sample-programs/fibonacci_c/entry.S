#include "../common.h"

.section .text.init
.global _start
_start:
init_threads
    la a0, _start
    csrw mtvec, a0
.L_PROGRAM:
    rdcycle t0
    rdinstret t1
    la a1, loop_count
    lw a0, 0(a1)
    call main

    rdcycle t2
    rdinstret t3
    sub t0,t2,t0
    sub t1,t3,t1

    mv x3,a0

    j .

.section .data
.align 8
loop_count:  .byte 100