export CC := riscv64-unknown-elf-gcc
export OBJCOPY := riscv64-elf-objcopy
export CFLAGS := -nodefaultlibs -nostdlib -march=rv64i -mabi=lp64 -no-pie -static

SUBDIRS := fibonacci branch

.PHONY: all, branch, fibonacci
all: $(SUBDIRS)

$(SUBDIRS):
	$(MAKE) -C $@

SUBDIRS_CLEAN = $(addsuffix .clean, $(SUBDIRS))
$(SUBDIRS_CLEAN):
	$(MAKE) -C $(basename $@) clean

clean: $(SUBDIRS_CLEAN)