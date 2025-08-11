package com.google.udmi.util;

import static com.google.udmi.util.ProperPrinter.OutputFormat.COMPRESSED;
import static com.google.udmi.util.ProperPrinter.OutputFormat.VERBOSE;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import java.io.IOException;

class ProperPrinter extends DefaultPrettyPrinter {

  static final PrettyPrinter INDENT_PRINTER = new ProperPrinter(VERBOSE);
  static final PrettyPrinter NO_INDENT_PRINTER = new ProperPrinter(COMPRESSED);

  private final OutputFormat indent;
  private final boolean isCompressed;

  ProperPrinter(OutputFormat indent) {
    super();
    this.indent = indent;
    isCompressed = indent == COMPRESSED;

    switch (indent) {
      case COMPRESSED -> {
        Indenter indenter = new DefaultIndenter("", "");
        indentObjectsWith(indenter);
        indentArraysWith(indenter);
      }
      case VERBOSE_ARRAY_ON_NEW_LINE -> {
        indentArraysWith(new DefaultIndenter("  ", DefaultIndenter.SYS_LF));
      }
      case VERBOSE -> {
        // No action is needed. We rely on the default behavior from the
        // superclass for standard pretty-printing.
      }
    }
  }

  @Override
  public void writeObjectFieldValueSeparator(JsonGenerator generator) throws IOException {
    generator.writeRaw(isCompressed ? ":" : ": ");
  }

  @Override
  public DefaultPrettyPrinter createInstance() {
    return new ProperPrinter(this.indent);
  }

  enum OutputFormat {
    VERBOSE,
    VERBOSE_ARRAY_ON_NEW_LINE,
    COMPRESSED
  }
}
