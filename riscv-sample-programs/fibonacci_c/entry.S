.extern __BINARY_END_

.section .text.init
.global _start
_start:
    la sp, __BINARY_END_
    mv a0,tp
    addi sp,sp,1024
LOOP:
    beq a0,zero,PROGRAM
    addi sp,sp,1024
    addi a0,a0,-1
PROGRAM:

    la a1, loop_count
    lw a0, 0(a1)
    call main

    mv x3,a0

    j .

.section .data
loop_count:  .byte 0x07