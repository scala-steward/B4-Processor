ENTRY(_start)

MEMORY{
	ROM (rx) : ORIGIN = 0x40000000, LENGTH = 4k
	RAM (rx) : ORIGIN = 0x80000000, LENGTH = 4k
}

SECTIONS{
	.text :
	{
		*(.text.init)
		*(.text*)
	} > ROM
	.data : { *(.data) } > ROM
}