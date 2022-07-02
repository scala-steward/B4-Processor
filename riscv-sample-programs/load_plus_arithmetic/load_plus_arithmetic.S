_start:
    addi x1, x0, 1
    slli x1, x1, 31

    addi x2, x0, 10

    addi x3, x0, -16

    sd x2,0(x1)
    ld x5,0(x1)

    add x3, x2, x3
    add x2, x2, x5

    j .