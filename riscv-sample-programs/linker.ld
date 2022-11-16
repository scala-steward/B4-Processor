ENTRY(_start)

MEMORY{
	RAM (rwx) : ORIGIN = 0x40000000, LENGTH = 16M
}

SECTIONS{
	.text :
	{
	    . = ALIGN(8);
		*(.text.init)
		*(.text*)
	} > RAM
	.data : {
		. = ALIGN(8);
	    *(.data)
	} > RAM
	. = ALIGN(32) + 1024;
	__STACK_START_ = .;
}