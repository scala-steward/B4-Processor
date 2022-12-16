ENTRY(_start)

MEMORY{
	ROM (rx) : ORIGIN = 0x20000000, LENGTH = 32k
	RAM (rwx): ORIGIN = 0x80000000 + 1M, LENGTH = 1k
}

SECTIONS{
	.text :
	{
	    . = ALIGN(8);
	    PROVIDE(__TEXT_START = .);
		*(.text.init)
		*(.text*)
	    PROVIDE(__TEXT_END = .);
	} > ROM
	.data : {
		. = ALIGN(8);
	    PROVIDE(__DATA_START = .);
	    *(.data)
	    PROVIDE(__DATA_END = .);
	} > ROM
	.bss : {
	    . = ALIGN(8);
	    PROVIDE(__BSS_START = .);
	    PROVIDE(__RAM_TMP_START = .);
	    *(.bss .bss*)
	    PROVIDE(__BSS_END = .);
	}

	PROVIDE(__UART_START = 0x10013000);
	PROVIDE(__GPIO_START = 0x10012000);
	PROVIDE(__RAM_START = 0x80000000);
}