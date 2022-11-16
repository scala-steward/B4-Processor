all: ip/B4Processor_1_0/src/B4Processor.v

B4Processor.v:
	sbt "runMain b4processor.B4Processor"

ip/B4Processor_1_0/src/B4Processor.v: B4Processor.v
	cp $< $@

clean:
	rm ip/B4Processor_1_0/src/B4Processor.v
	rm B4Processor.v