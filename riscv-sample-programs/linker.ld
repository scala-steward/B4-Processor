ENTRY(_start)

MEMORY{
	ROM (rx) : ORIGIN = 0x40000000, LENGTH = 1M
	RAM (rx) : ORIGIN = 0x80000000, LENGTH = 16M
}

SECTIONS{
	.text :
	{
		*(.text.init)
		*(.text*)
	} > ROM
	.data : { *(.data) } > RAM
}