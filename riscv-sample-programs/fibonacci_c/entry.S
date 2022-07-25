.section .text.init
.global _start
_start:
    li sp, 1
    slli sp, sp, 31
    addi sp, sp, 512

    la a1, loop_count
    lw a0, 0(a1)
    call main

    mv x3,a0

    j .

.section .data
loop_count:  .byte 0x07