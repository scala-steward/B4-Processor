PROGRAMNAME := loader_uart
SOURCES := entry.S loader_binary.o

TOOL_PREFIX := riscv64-unknown-elf-
export CC := $(TOOL_PREFIX)gcc
export OBJCOPY := $(TOOL_PREFIX)objcopy
export OBJDUMP := $(TOOL_PREFIX)objdump
export CFLAGS := -nodefaultlibs -nostdlib -march=rv64i -mabi=lp64 -no-pie -static -g

CFLAGS += -T ./linker.ld

.PHONY:clean
all: $(PROGRAMNAME).hex $(PROGRAMNAME).o $(PROGRAMNAME).dump $(PROGRAMNAME).text64.hex


$(PROGRAMNAME).o: $(SOURCES)
	$(CC) $(CFLAGS) $^ -o $@

$(PROGRAMNAME).binary: $(PROGRAMNAME).o
	$(OBJCOPY) -O binary  $< $@

$(PROGRAMNAME).hex: $(PROGRAMNAME).binary
	od -An -t x1 $< -w1 -v | tr -d " " > $@

$(PROGRAMNAME).text64.hex: $(PROGRAMNAME).binary
	od -An -t x8 $< -w8 -v | tr -d " " > $@

$(PROGRAMNAME).dump: $(PROGRAMNAME).o
	$(OBJDUMP) -D $< -M no-aliases -M numeric > $@

clean:
	$(RM) *.hex *.binary $(PROGRAM_NAME).o *.dump