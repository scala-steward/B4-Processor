ENTRY(_start)

MEMORY{
	ROM (rx) : ORIGIN = 0, LENGTH = 4k
}

SECTIONS{
	.text :
	{
		*(.text.init)
		*(.text*)
	} > ROM
	.data : { *(.data) } > ROM
}