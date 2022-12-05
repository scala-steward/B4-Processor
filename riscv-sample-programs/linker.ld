ENTRY(_start)

MEMORY{
	RAM (rwx) : ORIGIN = 0x40000000, LENGTH = 16M
}

SECTIONS{
	.text :
	{
	    . = ALIGN(8);
	    PROVIDE(__TEXT_START_ = .);
		*(.text.init)
		*(.text*)
	    PROVIDE(__TEXT_END_ = .);

	} > RAM
	.data : {
		. = ALIGN(8);
		PROVIDE(__DATA_START_ = .);
	    *(.data)
	    PROVIDE(__DATA_END_ = .);
	} > RAM
	. = ALIGN(8);
	PROVIDE(__BINARY_END_ =.);
}