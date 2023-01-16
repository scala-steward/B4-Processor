.section .text.init
.global _start
_start:
    csrrs tp,mhartid,zero
    bnez tp,_start
    rdcycle a0
    rdinstret a1
    li a5,10
_loop:
    addi a5,a5,-1
    bnez a5,_loop
LABEL:
    rdcycle a2
    rdinstret a3
    sub a0,a2,a0
    sub a1,a3,a1

    li a7, 10

    la a0, TRAP
    csrw mtvec, a0
    csrr a0, time
    j END

TRAP:
    li a7, 20
    la a0, EPC
    csrw mepc, a0
    mret
    j END

EPC:
    li a7, 30

END:
    j .