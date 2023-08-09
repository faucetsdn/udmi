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

  private final boolean isCompressed;

  private ProperPrinter(OutputFormat indent) {
    super();
    isCompressed = indent == COMPRESSED;
    if (isCompressed) {
      Indenter indenter = new DefaultIndenter("", "");
      indentObjectsWith(indenter);
      indentArraysWith(indenter);
    }
  }

  @Override
  public void writeObjectFieldValueSeparator(JsonGenerator generator) throws IOException {
    generator.writeRaw(isCompressed ? ":" : ": ");
  }

  enum OutputFormat {
    VERBOSE,
    COMPRESSED
  }
}
