.section .text.init
.global _start
_start:
    li sp, 1
    slli sp, sp, 31
    addi sp, sp, 1024

    call main

    j .