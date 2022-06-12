_start:
addi x1, x0, 10

addi x2, x0, 1
slli x2, x2, 31

sd x1,0(x2)
ld x3,0(x2)

END:
j END