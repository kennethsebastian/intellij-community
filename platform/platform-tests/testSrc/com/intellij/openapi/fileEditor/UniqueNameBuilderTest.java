package com.intellij.openapi.fileEditor;

import com.intellij.openapi.fileEditor.impl.UniqueNameBuilder;
import junit.framework.TestCase;

/**
 * @author yole
 */
public class UniqueNameBuilderTest extends TestCase {
  public void testSimple() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<String>("", "/", 100);
    builder.addPath("A", "/Users/yole/idea/foo/bar.java");
    builder.addPath("B", "/Users/yole/idea/baz/bar.java");
    assertEquals("foo/bar.java", builder.getShortPath("A"));
  }

  public void testTwoLevel() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<String>("", "/", 100);
    builder.addPath("A", "/Users/yole/idea/foo/buy/index.html");
    builder.addPath("B", "/Users/yole/idea/bar/buy/index.html");
    assertEquals("foo/buy/index.html", builder.getShortPath("A"));
  }

  public void testSeparator() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<String>("", "\\", 100);
    builder.addPath("A", "/Users/yole/idea/foo/buy/index.html");
    builder.addPath("B", "/Users/yole/idea/bar/buy/index.html");
    assertEquals("foo\\buy\\index.html", builder.getShortPath("A"));
  }

  public void testRoot() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<String>("/Users/yole/idea", "/", 100);
    builder.addPath("A", "/Users/yole/idea/build/scripts/layouts.gant");
    builder.addPath("B", "/Users/yole/idea/community/build/scripts/layouts.gant");
    assertEquals("build/scripts/layouts.gant", builder.getShortPath("A"));
    assertEquals("community/build/scripts/layouts.gant", builder.getShortPath("B"));
  }

  public void testShortenNames() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<String>("/Users/yole/idea", "/", 25);
    builder.addPath("A", "/Users/yole/idea/build/scripts/layouts.gant");
    builder.addPath("B", "/Users/yole/idea/community/build/scripts/layouts.gant");
    assertEquals("build/s\u2026/layouts.gant", builder.getShortPath("A"));
    assertEquals("community/b\u2026/s\u2026/layouts.gant", builder.getShortPath("B"));
  }

  public void testShortenNamesUnique() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<String>("/Users/yole/idea", "/", 25);
    builder.addPath("A", "/Users/yole/idea/pycharm/download/index.html");
    builder.addPath("B", "/Users/yole/idea/pycharm/documentation/index.html");
    builder.addPath("C", "/Users/yole/idea/fabrique/download/index.html");
    assertEquals("pycharm/dow\u2026/index.html", builder.getShortPath("A"));
  }
}
