.section .text.init
.global _start
_start:
    la sp, __STACK_START_
    call main

    mv x3,a0

    j .