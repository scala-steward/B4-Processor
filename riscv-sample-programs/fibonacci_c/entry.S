.section .text.init
.global _start
_start:
    li sp, 1
    slli sp, sp, 31
    addi sp, sp, 512

    call main

    mv x3,a0

    j .