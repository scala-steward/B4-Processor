_start:
addi x1, x0, 1
slli x1, x1, 31

addi x2, x0, 10

sd x2,0(x1)
ld x3,0(x1)

END:
j END