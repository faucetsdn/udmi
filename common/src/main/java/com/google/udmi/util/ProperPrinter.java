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

  private ProperPrinter(OutputFormat indent) {
    super();
    if (indent == OutputFormat.COMPRESSED) {
      Indenter indenter = new DefaultIndenter("", "");
      indentObjectsWith(indenter);
      indentArraysWith(indenter);
    }
  }

  @Override
  public void writeObjectFieldValueSeparator(JsonGenerator generator) throws IOException {
    generator.writeRaw(": ");
  }

  enum OutputFormat {
    VERBOSE,
    COMPRESSED
  }
}
