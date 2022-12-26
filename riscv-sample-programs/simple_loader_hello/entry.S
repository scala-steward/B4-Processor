.extern __STACK_START_

.section .text.init
.global _start
_start:
    beqz tp,_write
_write:
    la a0, __GPIO_START
    li a1,0xF
    sb a1,0(a0)
    la a0, __UART_START
    la a4, hello
NEXT_CHAR:
    lb a5,0(a4)
    beqz a5, END
check_full:
    lw a7, 0x8(a0)
    andi a7,a7,8
    beqz a7, write_char
    call wait
    j check_full
write_char:
    sb a5,4(a0)
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
hello: .string "Hello World\r\nFrom B4Processor!\r\n"
