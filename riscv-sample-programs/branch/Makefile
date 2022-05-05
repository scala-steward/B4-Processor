CFLAGS += -T ../linker.ld

.PHONY:clean
all: branch.8.hex branch.32.hex branch.o

branch.o: branch.S
	@echo $(CC) $(CFLAGS)
	$(CC) $(CFLAGS) $< -o $@

branch.binary: branch.o
	$(OBJCOPY) -O binary -j .text $< $@

branch.8.hex: branch.binary
	od -An -t x1 $< -w1 -v | tr -d " " > $@

branch.32.hex: branch.binary
	od -An -t x4 $< -w4 -v | tr -d " " > $@

clean:
	$(RM) *.hex *.binary *.o