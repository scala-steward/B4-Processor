ENTRY(_start)

MEMORY{
	ROM (rx) : ORIGIN = 0x40000000, LENGTH = 1k
	RAM (rx) : ORIGIN = 0x80000000, LENGTH = 1k
}

SECTIONS{
	.text :
	{
		*(.text.init)
		*(.text*)
	} > ROM
	.data : { *(.data) } > ROM
}