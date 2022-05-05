export CC := riscv64-elf-gcc
export OBJCOPY := riscv64-elf-objcopy
export CFLAGS := -nodefaultlibs -nostdlib -march=rv64i -mabi=lp64 -no-pie -static

SUBDIRS := branch

.PHONY: all, branch
all: $(SUBDIRS)

$(SUBDIRS):
	$(MAKE) -C $@

SUBDIRS_CLEAN = $(addsuffix .clean, $(SUBDIRS))
$(SUBDIRS_CLEAN):
	$(MAKE) -C $(basename $@) clean

clean: $(SUBDIRS_CLEAN)