import sys

# ANSI Escape Sequences
RESET = "\033[0m"
BOLD = "\033[1m"

# Colors
RED = "\033[31m"
GREEN = "\033[32m"
YELLOW = "\033[33m"
BLUE = "\033[34m"
MAGENTA = "\033[35m"
CYAN = "\033[36m"

def is_color_supported() -> bool:
    """Checks if the current terminal/stdout supports ANSI color codes."""
    # Returns True if running in an interactive TTY, False if piped or redirected
    return sys.stdout.isatty()

def color_text(text: str, color: str, bold: bool = False) -> str:
    """Wraps text with ANSI escape codes safely based on terminal compatibility."""
    if not is_color_supported():
        return text
    style = BOLD + color if bold else color
    return f"{style}{text}{RESET}"

def print_cyan(text: str, bold: bool = False):
    print(color_text(text, CYAN, bold))

def print_green(text: str, bold: bool = False):
    print(color_text(text, GREEN, bold))

def print_red(text: str, bold: bool = False):
    print(color_text(text, RED, bold))

def print_yellow(text: str, bold: bool = False):
    print(color_text(text, YELLOW, bold))

def print_magenta(text: str, bold: bool = False):
    print(color_text(text, MAGENTA, bold))

def print_blue(text: str, bold: bool = False):
    print(color_text(text, BLUE, bold))

_banner_printed = False

def print_mantis_banner():
    global _banner_printed
    if _banner_printed:
        return
    _banner_printed = True
    banner = """
        -              -.                  
        :-            :-                   
         -:           =.                   
          =.         :-                    
          :=         =.                    
           -.        :     ...             
   .::..  .:::--------: .:-==-             
   -====-:..-+++++++=..-======.            
   -=======: -+++++= :=======- :-::..      
   .-======= :+++++=..-=====-..=++++==-.   
    .:----:..=++++++=:..:::..:=+++++++++=: 
           .=++++++++=.   .-=+++++++++++++=
            .:=+++++-       .:-=+++++++++++
   .....       :=+=.           .:-+++++++++
  -=====---::.  .:             .:..:=++++++
 -============--:.             .==. ..-=+++
:=================-:           .==- -=:.:=+
===-:================:.         -==: =+=:..
===. .-===---:::::::---:.       -==- :+++=-
==-   .::...::::::::::....      -===: =++++
==:    .-==++++++++++++==--:.   :===- :++++
==:    =+++++++++++++++++++++=-:..:--  -+++
==:   .=++++ ++++++++++++++++++++=:..  .+++
==:    =++=-  =+++++++++++++++++++++-:..-++
==:    -+++.   .:=+++++++++++++++++++++++++
    """
    print(color_text(banner, GREEN))
    print(color_text(" MANTIS | Distributed Log Analysis | v1.0.0 \n", GREEN, bold=True))
