_start:
    addi x1, x0, 1
    slli x1, x1, 31

    li x2, 10
    li x4, 20

    sd x2, 0(x1)

    mv x2,x1
    addi x2, x2, 20
    addi x2, x2, 20
    addi x2, x2, 20
    addi x2, x2, 20
    addi x2, x2, -20
    addi x2, x2, -20
    addi x2, x2, -20
    addi x2, x2, -20

    ld x3, 0(x2)
    sd x4, 0(x1)

    j .