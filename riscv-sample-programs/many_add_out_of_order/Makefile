CFLAGS += -T ../linker.ld

PROGRAMNAME := many_add_out_of_order

.PHONY:clean
all: $(PROGRAMNAME).8.hex $(PROGRAMNAME).32.hex $(PROGRAMNAME).o

$(PROGRAMNAME).o: $(PROGRAMNAME).S
	@echo $(CC) $(CFLAGS)
	$(CC) $(CFLAGS) $< -o $@

$(PROGRAMNAME).binary: $(PROGRAMNAME).o
	$(OBJCOPY) -O binary -j .text $< $@

$(PROGRAMNAME).8.hex: $(PROGRAMNAME).binary
	od -An -t x1 $< -w1 -v | tr -d " " > $@

$(PROGRAMNAME).32.hex: $(PROGRAMNAME).binary
	od -An -t x4 $< -w4 -v | tr -d " " > $@

clean:
	$(RM) *.hex *.binary *.o