all: programs

.PHONY: programs test processor

programs:
	nix -L build -o programs

test:
	nix -L flake check

test-artifacts:
	nix -L build '.#checks.x86_64-linux.all'

processor:
	nix -L build '.#processor' -o processor

#B4Processor.v:
#	sbt "runMain b4processor.B4Processor"
#
#ip/B4Processor_1_0/src/B4Processor.v: B4Processor.v
#	cp $< $@

clean:
	rm programs
