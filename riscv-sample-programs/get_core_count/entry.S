#include "../common.h"

_start:
# get core count
# custom csr for core count on 0xCC0
csrr x1, 0xCC0

j .