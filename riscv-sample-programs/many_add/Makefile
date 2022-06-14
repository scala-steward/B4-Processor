CFLAGS += -T ../linker.ld

PROGRAMNAME := many_add

.PHONY:clean
all: $(PROGRAMNAME).8.hex $(PROGRAMNAME).32.hex $(PROGRAMNAME).o $(PROGRAMNAME).dump

$(PROGRAMNAME).o: $(PROGRAMNAME).S
	@echo $(CC) $(CFLAGS)
	$(CC) $(CFLAGS) $< -o $@

$(PROGRAMNAME).binary: $(PROGRAMNAME).o
	$(OBJCOPY) -O binary -j .text $< $@

$(PROGRAMNAME).8.hex: $(PROGRAMNAME).binary
	od -An -t x1 $< -w1 -v | tr -d " " > $@

$(PROGRAMNAME).32.hex: $(PROGRAMNAME).binary
	od -An -t x4 $< -w4 -v | tr -d " " > $@

$(PROGRAMNAME).dump: $(PROGRAMNAME).o
	$(OBJDUMP) -d $< -M no-aliases -M numeric > $@

clean:
	$(RM) *.hex *.binary *.o *.dump