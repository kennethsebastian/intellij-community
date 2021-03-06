package org.jetbrains.ether.dependencyView;

import org.jetbrains.asm4.Type;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 07.03.11
 * Time: 19:54
 * To change this template use File | Settings | File Templates.
 */
abstract class ProtoMember extends Proto {
  private final static byte STRING = 0;
  private final static byte NONE = 1;
  private final static byte INTEGER = 2;
  private final static byte LONG = 3;
  private final static byte FLOAT = 4;
  private final static byte DOUBLE = 5;
  private final static byte TYPE = 6;

  public final TypeRepr.AbstractType type;
  public final Object value;

  public boolean hasValue() {
    return value != null;
  }

  protected ProtoMember(final int access, final int signature, final int name, final TypeRepr.AbstractType t, final Object value) {
    super(access, signature, name);
    this.type = t;
    this.value = value;
  }

  private static Object loadTyped(final DataInput in) {
    try {
      switch (in.readByte()) {
        case STRING:
          return in.readUTF();
        case NONE:
          return null;
        case INTEGER:
          return in.readInt();
        case LONG:
          return in.readLong();
        case FLOAT:
          return in.readFloat();
        case DOUBLE:
          return in.readDouble();
        case TYPE :
          return Type.getType(in.readUTF());
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    assert (false);

    return null;
  }

  protected ProtoMember(final DependencyContext context, final DataInput in) {
    super(in);
    try {
      type = TypeRepr.externalizer(context).read(in);
      value = loadTyped(in);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void save(final DataOutput out) {
    super.save(out);
    type.save(out);

    try {
      if (value instanceof String) {
        out.writeByte(STRING);
        out.writeUTF((String)value);
      }
      else if (value instanceof Integer) {
        out.writeByte(INTEGER);
        out.writeInt(((Integer)value).intValue());
      }
      else if (value instanceof Long) {
        out.writeByte(LONG);
        out.writeLong(((Long)value).longValue());
      }
      else if (value instanceof Float) {
        out.writeByte(FLOAT);
        out.writeFloat(((Float)value).floatValue());
      }
      else if (value instanceof Double) {
        out.writeByte(DOUBLE);
        out.writeDouble(((Double)value).doubleValue());
      }
      else if (value instanceof Type) {
        out.writeByte(TYPE);
        out.writeUTF(((Type)value).getDescriptor());
      }
      else {
        out.writeByte(NONE);
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Difference difference(final Proto past) {
    final ProtoMember m = (ProtoMember)past;
    final Difference diff = super.difference(past);
    int base = diff.base();

    if (!m.type.equals(type)) {
      base |= Difference.TYPE;
    }

    switch ((value == null ? 0 : 1) + (m.value == null ? 0 : 2)) {
      case 3:
        if (!value.equals(m.value)) {
          base |= Difference.VALUE;
        }
        break;

      case 2:
        base |= Difference.VALUE;
        break;

      case 1:
        base |= Difference.VALUE;
        break;

      case 0:
        break;
    }

    final int newBase = base;

    return new Difference() {
      @Override
      public int base() {
        return newBase;
      }

      @Override
      public boolean no() {
        return newBase == NONE && diff.no();
      }

      @Override
      public int addedModifiers() {
        return diff.addedModifiers();
      }

      @Override
      public int removedModifiers() {
        return diff.removedModifiers();
      }

      @Override
      public boolean packageLocalOn() {
        return diff.packageLocalOn();
      }

      @Override
      public boolean hadValue() {
        return ((ProtoMember)past).hasValue();
      }

      @Override
      public boolean weakedAccess() {
        return diff.weakedAccess();
      }
    };
  }

  public void toStream(final DependencyContext context, final PrintStream stream) {
    super.toStream(context, stream);
    stream.print("          Type       : ");
    stream.println(type.getDescr(context));

    stream.print("          Value      : ");
    stream.println(value == null ? "<null>" : value.toString());
  }
}
