ENTRY(_start)

MEMORY{
	ROM (rx) : ORIGIN = 0x20000000, LENGTH = 16k
}

SECTIONS{
	.text :
	{
	    . = ALIGN(8);
	    PROVIDE(__TEXT_START = .);
		*(.text .text*)
	    PROVIDE(__TEXT_END = .);
	} > ROM
	.data : {
		. = ALIGN(8);
	    PROVIDE(__DATA_START = .);
	    *(.data .data*)
	    PROVIDE(__DATA_END = .);
	} > ROM

	PROVIDE(__UART_START = 0x10013000);
	PROVIDE(__GPIO_START = 0x10012000);
	PROVIDE(__RAM_START = 0x80000000);
}