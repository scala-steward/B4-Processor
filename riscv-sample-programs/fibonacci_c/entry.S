.extern __STACK_START_

.section .text.init
.global _start
_start:
    la sp, __STACK_START_

    la a1, loop_count
    lw a0, 0(a1)
    call main

    mv x3,a0

    j .

.section .data
loop_count:  .byte 0x07