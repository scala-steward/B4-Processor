TOOL_PREFIX := riscv64-unknown-elf-
export CC := $(TOOL_PREFIX)gcc
export OBJCOPY := $(TOOL_PREFIX)objcopy
export OBJDUMP := $(TOOL_PREFIX)objdump
export CFLAGS := -nodefaultlibs -nostdlib -march=rv64i -mabi=lp64 -no-pie -static

SUBDIRS := fibonacci \
			branch \
			call_ret \
			many_add \
			many_add_out_of_order \
			load_store \
			fibonacci_c \
			many_load_store \
			load_plus_arithmetic

all: $(SUBDIRS)

$(SUBDIRS):
	$(MAKE) -C $@

SUBDIRS_CLEAN = $(addsuffix .clean, $(SUBDIRS))
$(SUBDIRS_CLEAN):
	$(MAKE) -C $(basename $@) clean


.PHONY: all clean $(SUBDIRS) $(SUBDIRS_CLEAN)

clean: $(SUBDIRS_CLEAN)
