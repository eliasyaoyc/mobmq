package xyz.vopen.framework.neptune.common.utils.tuple;

/**
 * A tuple with 2 fields. Tuples are strongly typed; each field may be of a separate type. The
 * fields of the tuple can be accessed directly as public fields (f0, f1, ...) or via their position
 * through the {@link #getField(int)} method. The tuple field positions start at zero.
 *
 * <p>Tuples are mutable types, meaning that their fields can be re-assigned. This allows functions
 * that work with Tuples to reuse objects in order to reduce pressure on the garbage collector.
 *
 * <p>Warning: If you subclass Tuple2, then be sure to either
 *
 * <ul>
 *   <li>not add any new fields, or
 *   <li>make it a POJO, and always declare the element type of your DataStreams/DataSets to your
 *       descendant type. (That is, if you have a "class Foo extends Tuple2", then don't use
 *       instances of Foo in a DataStream&lt;Tuple2&gt; / DataSet&lt;Tuple2&gt;, but declare it as
 *       DataStream&lt;Foo&gt; / DataSet&lt;Foo&gt;.)
 * </ul>
 *
 * @see Tuple
 * @param <T0> The type of field 0
 * @param <T1> The type of field 1
 */
public class Tuple2<T0, T1> extends Tuple {

  private static final long serialVersionUID = 1L;

  /** Field 0 of the tuple. */
  public T0 f0;
  /** Field 1 of the tuple. */
  public T1 f1;

  /** Creates a new tuple where all fields are null. */
  public Tuple2() {}

  /**
   * Creates a new tuple and assigns the given values to the tuple's fields.
   *
   * @param value0 The value for field 0
   * @param value1 The value for field 1
   */
  public Tuple2(T0 value0, T1 value1) {
    this.f0 = value0;
    this.f1 = value1;
  }

  @Override
  public int getArity() {
    return 2;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getField(int pos) {
    switch (pos) {
      case 0:
        return (T) this.f0;
      case 1:
        return (T) this.f1;
      default:
        throw new IndexOutOfBoundsException(String.valueOf(pos));
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> void setField(T value, int pos) {
    switch (pos) {
      case 0:
        this.f0 = (T0) value;
        break;
      case 1:
        this.f1 = (T1) value;
        break;
      default:
        throw new IndexOutOfBoundsException(String.valueOf(pos));
    }
  }

  /**
   * Sets new values to all fields of the tuple.
   *
   * @param value0 The value for field 0
   * @param value1 The value for field 1
   */
  public void setFields(T0 value0, T1 value1) {
    this.f0 = value0;
    this.f1 = value1;
  }

  /**
   * Returns a shallow copy of the tuple with swapped values.
   *
   * @return shallow copy of the tuple with swapped values
   */
  public Tuple2<T1, T0> swap() {
    return new Tuple2<T1, T0>(f1, f0);
  }

  // -------------------------------------------------------------------------------------------------
  // standard utilities
  // -------------------------------------------------------------------------------------------------

  /**
   * Deep equality for tuples by calling equals() on the tuple members.
   *
   * @param o the object checked for equality
   * @return true if this is equal to o.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Tuple2)) {
      return false;
    }
    @SuppressWarnings("rawtypes")
    Tuple2 tuple = (Tuple2) o;
    if (f0 != null ? !f0.equals(tuple.f0) : tuple.f0 != null) {
      return false;
    }
    if (f1 != null ? !f1.equals(tuple.f1) : tuple.f1 != null) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    int result = f0 != null ? f0.hashCode() : 0;
    result = 31 * result + (f1 != null ? f1.hashCode() : 0);
    return result;
  }

  /**
   * Shallow tuple copy.
   *
   * @return A new Tuple with the same fields as this.
   */
  @Override
  @SuppressWarnings("unchecked")
  public Tuple2<T0, T1> copy() {
    return new Tuple2<>(this.f0, this.f1);
  }

  /**
   * Creates a new tuple and assigns the given values to the tuple's fields. This is more convenient
   * than using the constructor, because the compiler can infer the generic type arguments
   * implicitly. For example: {@code Tuple3.of(n, x, s)} instead of {@code new Tuple3<Integer,
   * Double, String>(n, x, s)}
   */
  public static <T0, T1> Tuple2<T0, T1> of(T0 value0, T1 value1) {
    return new Tuple2<>(value0, value1);
  }
}