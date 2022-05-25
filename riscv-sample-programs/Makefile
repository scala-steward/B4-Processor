export CC := riscv64-unknown-elf-gcc
export OBJCOPY := riscv64-elf-objcopy
export CFLAGS := -nodefaultlibs -nostdlib -march=rv64i -mabi=lp64 -no-pie -static

SUBDIRS := fibonacci branch call_ret many_add many_add_out_of_order

all: $(SUBDIRS)

$(SUBDIRS):
	$(MAKE) -C $@

SUBDIRS_CLEAN = $(addsuffix .clean, $(SUBDIRS))
$(SUBDIRS_CLEAN).clean:
	$(MAKE) -C $(basename $@) clean

clean: $(SUBDIRS_CLEAN)

.PHONY: all clean $(SUBDIRS) $(SUBDIRS_CLEAN)
