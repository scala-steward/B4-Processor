TOOL_PREFIX := riscv64-unknown-elf-

CC := $(TOOL_PREFIX)gcc
OBJCOPY := $(TOOL_PREFIX)objcopy
OBJDUMP := $(TOOL_PREFIX)objdump
CFLAGS += -nodefaultlibs -nostdlib -march=rv64i -mabi=lp64 -no-pie -static

CFLAGS += -T ../linker.ld

data_hex = $(addsuffix .hex,$(addprefix  $(PROGRAMNAME).data_,0 1 2 3 4 5 6 7))

.PHONY:clean
all: $(PROGRAMNAME).text.hex $(data_hex) $(PROGRAMNAME).o $(PROGRAMNAME).dump


$(PROGRAMNAME).o: $(SOURCES)
	@echo $(CC) $(CFLAGS)
	$(CC) $(CFLAGS) $^ -o $@

$(PROGRAMNAME).text.binary: $(PROGRAMNAME).o
	$(OBJCOPY) -O binary -j .text $< $@

$(PROGRAMNAME).data.binary: $(PROGRAMNAME).o
	$(OBJCOPY) -O binary -j .data $< $@

$(PROGRAMNAME).text.hex: $(PROGRAMNAME).text.binary
	od -An -t x1 $< -w1 -v | tr -d " " > $@


$(PROGRAMNAME).data_0.hex: $(PROGRAMNAME).data.binary
	od -An -t x1 $< -w1 -v | tr -d " " | awk NR-1%8==0 > $@

$(PROGRAMNAME).data_1.hex: $(PROGRAMNAME).data.binary
	od -An -t x1 $< -w1 -v | tr -d " " | awk NR-1%8==1 > $@

$(PROGRAMNAME).data_2.hex: $(PROGRAMNAME).data.binary
	od -An -t x1 $< -w1 -v | tr -d " " | awk NR-1%8==2 > $@

$(PROGRAMNAME).data_3.hex: $(PROGRAMNAME).data.binary
	od -An -t x1 $< -w1 -v | tr -d " " | awk NR-1%8==3 > $@

$(PROGRAMNAME).data_4.hex: $(PROGRAMNAME).data.binary
	od -An -t x1 $< -w1 -v | tr -d " " | awk NR-1%8==4 > $@

$(PROGRAMNAME).data_5.hex: $(PROGRAMNAME).data.binary
	od -An -t x1 $< -w1 -v | tr -d " " | awk NR-1%8==5 > $@

$(PROGRAMNAME).data_6.hex: $(PROGRAMNAME).data.binary
	od -An -t x1 $< -w1 -v | tr -d " " | awk NR-1%8==6 > $@

$(PROGRAMNAME).data_7.hex: $(PROGRAMNAME).data.binary
	od -An -t x1 $< -w1 -v | tr -d " " | awk NR-1%8==7 > $@


$(PROGRAMNAME).dump: $(PROGRAMNAME).o
	$(OBJDUMP) -d $< -M no-aliases -M numeric > $@

clean:
	$(RM) *.hex *.binary *.o *.dump