.extern __STACK_START_

.section .text.init
.global _start
_start:
    la a1, __RAM_START
    la a2, __UART_START
    la a3, __GPIO_START

    la a4, hello

NEXT_CHAR:
    lb a5,0(a4)
    fence
    lw a7, 0xC(a2)
    fence
    sb a5,4(a2)
    fence
    addi a4,a4,1
    j NEXT_CHAR
END:
    j .


.global wait
wait:
    li a3,100000
_WTMP:
    beq a3,zero,_WEND
    addi a3,a3,-1
    j _WTMP
_WEND:
    ret

.section .data
hello: .string "abcdefghijklmnopqrstuvwxyz"
