_start:
    addi x1, x0, 1
    slli x1, x1, 31

    li x2, 10

    sd x2,0(x1)
    ld x3,0(x1)
    ld x4,0(x1)
    ld x5,0(x1)
    ld x6,0(x1)

    li x2, 11

    sd x2,0(x1)
    sd x2,8(x1)
    sd x2,16(x1)
    sd x2,24(x1)
    sd x2,32(x1)

    ld x2,0(x1)
    ld x3,8(x1)
    ld x4,16(x1)
    ld x5,24(x1)
    ld x6,32(x1)

    li x2,-1

    sd x2,0(x1)
    sw x2,8(x1)
    sh x2,16(x1)
    sb x2,24(x1)

    ld x3,0(x1)
    lw x4,8(x1)
    lh x5,16(x1)
    lb x6,24(x1)

    j .