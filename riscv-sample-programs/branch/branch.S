.section .text.init
.global _start
_start:
    li x1,2
    li x2,3
    add x3,x2,a0
    bne x3,x0,LABEL
    li x4,10
    beq zero,zero,END
LABEL:
    li x4,20
END:
    j .