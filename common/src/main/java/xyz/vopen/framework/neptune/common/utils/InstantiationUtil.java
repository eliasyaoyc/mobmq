package xyz.vopen.framework.neptune.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.vopen.framework.neptune.common.annoations.Internal;
import xyz.vopen.framework.neptune.common.exceptions.NeptuneException;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.*;

/** Utility class to create instances from class objects and checking failure reasons. */
@Internal
public final class InstantiationUtil {

  private static final Logger LOG = LoggerFactory.getLogger(InstantiationUtil.class);

  /** Private constructor to prevent instantiation. */
  private InstantiationUtil() {
    throw new RuntimeException();
  }

  /** A custom ObjectInputStream that can load classes using a specific ClassLoader. */
  public static class ClassLoaderObjectInputStream extends ObjectInputStream {

    protected final ClassLoader classLoader;

    public ClassLoaderObjectInputStream(InputStream in, ClassLoader classLoader)
        throws IOException {
      super(in);
      this.classLoader = classLoader;
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc)
        throws IOException, ClassNotFoundException {
      if (classLoader != null) {
        String name = desc.getName();
        try {
          return Class.forName(name, false, classLoader);
        } catch (ClassNotFoundException ex) {
          // check if class is a primitive class
          Class<?> cl = primitiveClasses.get(name);
          if (cl != null) {
            // return primitive class
            return cl;
          } else {
            // throw ClassNotFoundException
            throw ex;
          }
        }
      }

      return super.resolveClass(desc);
    }

    @Override
    protected Class<?> resolveProxyClass(String[] interfaces)
        throws IOException, ClassNotFoundException {
      if (classLoader != null) {
        ClassLoader nonPublicLoader = null;
        boolean hasNonPublicInterface = false;

        // define proxy in class loader of non-public interface(s), if any
        Class<?>[] classObjs = new Class<?>[interfaces.length];
        for (int i = 0; i < interfaces.length; i++) {
          Class<?> cl = Class.forName(interfaces[i], false, classLoader);
          if ((cl.getModifiers() & Modifier.PUBLIC) == 0) {
            if (hasNonPublicInterface) {
              if (nonPublicLoader != cl.getClassLoader()) {
                throw new IllegalAccessError("conflicting non-public interface class loaders");
              }
            } else {
              nonPublicLoader = cl.getClassLoader();
              hasNonPublicInterface = true;
            }
          }
          classObjs[i] = cl;
        }
        try {
          return Proxy.getProxyClass(
              hasNonPublicInterface ? nonPublicLoader : classLoader, classObjs);
        } catch (IllegalArgumentException e) {
          throw new ClassNotFoundException(null, e);
        }
      }

      return super.resolveProxyClass(interfaces);
    }

    // ------------------------------------------------

    private static final HashMap<String, Class<?>> primitiveClasses = new HashMap<>(9);

    static {
      primitiveClasses.put("boolean", boolean.class);
      primitiveClasses.put("byte", byte.class);
      primitiveClasses.put("char", char.class);
      primitiveClasses.put("short", short.class);
      primitiveClasses.put("int", int.class);
      primitiveClasses.put("long", long.class);
      primitiveClasses.put("float", float.class);
      primitiveClasses.put("double", double.class);
      primitiveClasses.put("void", void.class);
    }
  }

  /**
   * This is maintained as a temporary workaround for FLINK-6869.
   *
   * <p>Before 1.3, the Scala serializers did not specify the serialVersionUID. Although since 1.3
   * they are properly specified, we still have to ignore them for now as their previous
   * serialVersionUIDs will vary depending on the Scala version.
   *
   * <p>This can be removed once 1.2 is no longer supported.
   */
  private static final Set<String> scalaSerializerClassnames = new HashSet<>();

  static {
    scalaSerializerClassnames.add("org.apache.flink.api.scala.typeutils.TraversableSerializer");
    scalaSerializerClassnames.add("org.apache.flink.api.scala.typeutils.CaseClassSerializer");
    scalaSerializerClassnames.add("org.apache.flink.api.scala.typeutils.EitherSerializer");
    scalaSerializerClassnames.add("org.apache.flink.api.scala.typeutils.EnumValueSerializer");
    scalaSerializerClassnames.add("org.apache.flink.api.scala.typeutils.OptionSerializer");
    scalaSerializerClassnames.add("org.apache.flink.api.scala.typeutils.TrySerializer");
    scalaSerializerClassnames.add("org.apache.flink.api.scala.typeutils.UnitSerializer");
  }

  private static boolean isAnonymousClass(Class clazz) {
    final String name = clazz.getName();

    // isAnonymousClass does not work for anonymous Scala classes; additionally check by class name
    if (name.contains("$anon$") || name.contains("$anonfun") || name.contains("$macro$")) {
      return true;
    }

    // calling isAnonymousClass or getSimpleName can throw InternalError for certain Scala types,
    // see https://issues.scala-lang.org/browse/SI-2034
    // until we move to JDK 9, this try-catch is necessary
    try {
      return clazz.isAnonymousClass();
    } catch (InternalError e) {
      return false;
    }
  }

  private static boolean isOldAvroSerializer(String name, long serialVersionUID) {
    // please see FLINK-11436 for details on why we need to ignore serial version UID here for the
    // AvroSerializer
    return (serialVersionUID == 1)
        && "org.apache.flink.formats.avro.typeutils.AvroSerializer".equals(name);
  }

  /**
   * Creates a new instance of the given class name and type using the provided {@link ClassLoader}.
   *
   * @param className of the class to load
   * @param targetType type of the instantiated class
   * @param classLoader to use for loading the class
   * @param <T> type of the instantiated class
   * @return Instance of the given class name
   * @throws NeptuneException if the class could not be found
   */
  public static <T> T instantiate(
      final String className, final Class<T> targetType, final ClassLoader classLoader)
      throws NeptuneException {
    final Class<? extends T> clazz;
    try {
      clazz = Class.forName(className, false, classLoader).asSubclass(targetType);
    } catch (ClassNotFoundException e) {
      throw new NeptuneException(
          String.format(
              "Could not instantiate class '%s' of type '%s'. Please make sure that this class is on your class path.",
              className, targetType.getName()),
          e);
    }

    return instantiate(clazz);
  }

  /**
   * Creates a new instance of the given class.
   *
   * @param <T> The generic type of the class.
   * @param clazz The class to instantiate.
   * @param castTo Optional parameter, specifying the class that the given class must be a subclass
   *     off. This argument is added to prevent class cast exceptions occurring later.
   * @return An instance of the given class.
   * @throws RuntimeException Thrown, if the class could not be instantiated. The exception contains
   *     a detailed message about the reason why the instantiation failed.
   */
  public static <T> T instantiate(Class<T> clazz, Class<? super T> castTo) {
    if (clazz == null) {
      throw new NullPointerException();
    }

    // check if the class is a subclass, if the check is required
    if (castTo != null && !castTo.isAssignableFrom(clazz)) {
      throw new RuntimeException(
          "The class '"
              + clazz.getName()
              + "' is not a subclass of '"
              + castTo.getName()
              + "' as is required.");
    }

    return instantiate(clazz);
  }

  /**
   * Creates a new instance of the given class.
   *
   * @param <T> The generic type of the class.
   * @param clazz The class to instantiate.
   * @return An instance of the given class.
   * @throws RuntimeException Thrown, if the class could not be instantiated. The exception contains
   *     a detailed message about the reason why the instantiation failed.
   */
  public static <T> T instantiate(Class<T> clazz) {
    if (clazz == null) {
      throw new NullPointerException();
    }

    // try to instantiate the class
    try {
      return clazz.newInstance();
    } catch (InstantiationException | IllegalAccessException iex) {
      // check for the common problem causes
      checkForInstantiation(clazz);

      // here we are, if non of the common causes was the problem. then the error was
      // most likely an exception in the constructor or field initialization
      throw new RuntimeException(
          "Could not instantiate type '"
              + clazz.getName()
              + "' due to an unspecified exception: "
              + iex.getMessage(),
          iex);
    } catch (Throwable t) {
      String message = t.getMessage();
      throw new RuntimeException(
          "Could not instantiate type '"
              + clazz.getName()
              + "' Most likely the constructor (or a member variable initialization) threw an exception"
              + (message == null ? "." : ": " + message),
          t);
    }
  }

  /**
   * Checks, whether the given class has a public nullary constructor.
   *
   * @param clazz The class to check.
   * @return True, if the class has a public nullary constructor, false if not.
   */
  public static boolean hasPublicNullaryConstructor(Class<?> clazz) {
    Constructor<?>[] constructors = clazz.getConstructors();
    for (Constructor<?> constructor : constructors) {
      if (constructor.getParameterTypes().length == 0
          && Modifier.isPublic(constructor.getModifiers())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks, whether the given class is public.
   *
   * @param clazz The class to check.
   * @return True, if the class is public, false if not.
   */
  public static boolean isPublic(Class<?> clazz) {
    return Modifier.isPublic(clazz.getModifiers());
  }

  /**
   * Checks, whether the class is a proper class, i.e. not abstract or an interface, and not a
   * primitive type.
   *
   * @param clazz The class to check.
   * @return True, if the class is a proper class, false otherwise.
   */
  public static boolean isProperClass(Class<?> clazz) {
    int mods = clazz.getModifiers();
    return !(Modifier.isAbstract(mods) || Modifier.isInterface(mods) || Modifier.isNative(mods));
  }

  /**
   * Checks, whether the class is an inner class that is not statically accessible. That is
   * especially true for anonymous inner classes.
   *
   * @param clazz The class to check.
   * @return True, if the class is a non-statically accessible inner class.
   */
  public static boolean isNonStaticInnerClass(Class<?> clazz) {
    return clazz.getEnclosingClass() != null
        && (clazz.getDeclaringClass() == null || !Modifier.isStatic(clazz.getModifiers()));
  }

  /**
   * Performs a standard check whether the class can be instantiated by {@code Class#newInstance()}.
   *
   * @param clazz The class to check.
   * @throws RuntimeException Thrown, if the class cannot be instantiated by {@code
   *     Class#newInstance()}.
   */
  public static void checkForInstantiation(Class<?> clazz) {
    final String errorMessage = checkForInstantiationError(clazz);

    if (errorMessage != null) {
      throw new RuntimeException(
          "The class '" + clazz.getName() + "' is not instantiable: " + errorMessage);
    }
  }

  public static String checkForInstantiationError(Class<?> clazz) {
    if (!isPublic(clazz)) {
      return "The class is not public.";
    } else if (clazz.isArray()) {
      return "The class is an array. An array cannot be simply instantiated, as with a parameterless constructor.";
    } else if (!isProperClass(clazz)) {
      return "The class is not a proper class. It is either abstract, an interface, or a primitive type.";
    } else if (isNonStaticInnerClass(clazz)) {
      return "The class is an inner class, but not statically accessible.";
    } else if (!hasPublicNullaryConstructor(clazz)) {
      return "The class has no (implicit) public nullary constructor, i.e. a constructor without arguments.";
    } else {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> T deserializeObject(byte[] bytes, ClassLoader cl)
      throws IOException, ClassNotFoundException {
    return deserializeObject(bytes, cl, false);
  }

  @SuppressWarnings("unchecked")
  public static <T> T deserializeObject(InputStream in, ClassLoader cl)
      throws IOException, ClassNotFoundException {
    return deserializeObject(in, cl, false);
  }

  @SuppressWarnings("unchecked")
  public static <T> T deserializeObject(byte[] bytes, ClassLoader cl, boolean isFailureTolerant)
      throws IOException, ClassNotFoundException {

    return deserializeObject(new ByteArrayInputStream(bytes), cl, isFailureTolerant);
  }

  @SuppressWarnings("unchecked")
  public static <T> T deserializeObject(InputStream in, ClassLoader cl, boolean isFailureTolerant)
      throws IOException, ClassNotFoundException {

    final ClassLoader old = Thread.currentThread().getContextClassLoader();
    // not using resource try to avoid AutoClosable's close() on the given stream
    try {
      ObjectInputStream oois =
          isFailureTolerant ? null : new InstantiationUtil.ClassLoaderObjectInputStream(in, cl);
      Thread.currentThread().setContextClassLoader(cl);
      return (T) oois.readObject();
    } finally {
      Thread.currentThread().setContextClassLoader(old);
    }
  }

  public static byte[] serializeObject(Object o) throws IOException {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos)) {
      oos.writeObject(o);
      oos.flush();
      return baos.toByteArray();
    }
  }

  public static void serializeObject(OutputStream out, Object o) throws IOException {
    ObjectOutputStream oos = new ObjectOutputStream(out);
    oos.writeObject(o);
  }

  public static boolean isSerializable(Object o) {
    try {
      serializeObject(o);
    } catch (IOException e) {
      return false;
    }

    return true;
  }

  /**
   * Clones the given serializable object using Java serialization.
   *
   * @param obj Object to clone
   * @param <T> Type of the object to clone
   * @return The cloned object
   * @throws IOException Thrown if the serialization or deserialization process fails.
   * @throws ClassNotFoundException Thrown if any of the classes referenced by the object cannot be
   *     resolved during deserialization.
   */
  public static <T extends Serializable> T clone(T obj) throws IOException, ClassNotFoundException {
    if (obj == null) {
      return null;
    } else {
      return clone(obj, obj.getClass().getClassLoader());
    }
  }

  /**
   * Clones the given serializable object using Java serialization, using the given classloader to
   * resolve the cloned classes.
   *
   * @param obj Object to clone
   * @param classLoader The classloader to resolve the classes during deserialization.
   * @param <T> Type of the object to clone
   * @return Cloned object
   * @throws IOException Thrown if the serialization or deserialization process fails.
   * @throws ClassNotFoundException Thrown if any of the classes referenced by the object cannot be
   *     resolved during deserialization.
   */
  public static <T extends Serializable> T clone(T obj, ClassLoader classLoader)
      throws IOException, ClassNotFoundException {
    if (obj == null) {
      return null;
    } else {
      final byte[] serializedObject = serializeObject(obj);
      return deserializeObject(serializedObject, classLoader);
    }
  }
}
