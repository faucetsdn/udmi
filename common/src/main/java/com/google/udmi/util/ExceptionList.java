package com.google.udmi.util;

import static java.lang.String.format;

import java.util.List;

public class ExceptionList extends RuntimeException {

  private final List<Exception> list;

  public ExceptionList(List<Exception> list) {
    super(format("List of %d exceptions", list.size()));
    this.list = list;
  }

  public List<Exception> exceptions() {
    return list;
  }
}
