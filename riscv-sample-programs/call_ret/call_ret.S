.global _start
_start:
    li x5,1
    call foo
    li x7,3
    j end

foo:
    li x6,2
    ret

end:
    j .