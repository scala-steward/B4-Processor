ENTRY(_start)

MEMORY{
	RAM (rx) : ORIGIN = 0x40000000, LENGTH = 16M
}

SECTIONS{
    . = ALIGN(8);
	.text :
	{
		*(.text.init)
		*(.text*)
	} > RAM
	. = ALIGN(8);
	.data : { *(.data) } > RAM
}