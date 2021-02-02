import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.full.KClasses;
import kotlin.reflect.jvm.KCallablesJvm;
import kotlin.reflect.jvm.ReflectJvmMapping;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.KotlinDetector;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @description:
 * @version: 1.0
 * @author: yuanfacheng
 * @date: 2021/1/26 16:06
 */
public class Person {
	public static void main(String[] args) {
		ApplicationContext context = new ClassPathXmlApplicationContext("person.xml");
		Object person = context.getBean("person");
		System.out.println(person);
	}

	private static final Map<Class<?>, Object> DEFAULT_TYPE_VALUES;

	static {
		Map<Class<?>, Object> values = new HashMap<>();
		values.put(boolean.class, false);
		values.put(byte.class, (byte) 0);
		values.put(short.class, (short) 0);
		values.put(int.class, 0);
		values.put(long.class, (long) 0);
		DEFAULT_TYPE_VALUES = Collections.unmodifiableMap(values);
	}

	public static <T> T instantiateClass(Constructor<T> ctor, Object... args) throws BeanInstantiationException {
		Assert.notNull(ctor, "Constructor must not be null");
		try {
			ReflectionUtils.makeAccessible(ctor);
			if (KotlinDetector.isKotlinReflectPresent() && KotlinDetector.isKotlinType(ctor.getDeclaringClass())) {
				return KotlinDelegate.instantiateClass(ctor, args);
			}
			else {
				Class<?>[] parameterTypes = ctor.getParameterTypes();
				Assert.isTrue(args.length <= parameterTypes.length, "Can't specify more arguments than constructor parameters");
				Object[] argsWithDefaultValues = new Object[args.length];
				for (int i = 0 ; i < args.length; i++) {
					if (args[i] == null) {
						Class<?> parameterType = parameterTypes[i];
						argsWithDefaultValues[i] = (parameterType.isPrimitive() ? DEFAULT_TYPE_VALUES.get(parameterType) : null);
					}
					else {
						argsWithDefaultValues[i] = args[i];
					}
				}
				return ctor.newInstance(argsWithDefaultValues);
			}
		}
		catch (InstantiationException ex) {
			throw new BeanInstantiationException(ctor, "Is it an abstract class?", ex);
		}
		catch (IllegalAccessException ex) {
			throw new BeanInstantiationException(ctor, "Is the constructor accessible?", ex);
		}
		catch (IllegalArgumentException ex) {
			throw new BeanInstantiationException(ctor, "Illegal arguments for constructor", ex);
		}
		catch (InvocationTargetException ex) {
			throw new BeanInstantiationException(ctor, "Constructor threw exception", ex.getTargetException());
		}
	}

	private static class KotlinDelegate {

		/**
		 * Retrieve the Java constructor corresponding to the Kotlin primary constructor, if any.
		 * @param clazz the {@link Class} of the Kotlin class
		 * @see <a href="https://kotlinlang.org/docs/reference/classes.html#constructors">
		 * https://kotlinlang.org/docs/reference/classes.html#constructors</a>
		 */
		@Nullable
		public static <T> Constructor<T> findPrimaryConstructor(Class<T> clazz) {
			try {
				KFunction<T> primaryCtor = KClasses.getPrimaryConstructor(JvmClassMappingKt.getKotlinClass(clazz));
				if (primaryCtor == null) {
					return null;
				}
				Constructor<T> constructor = ReflectJvmMapping.getJavaConstructor(primaryCtor);
				if (constructor == null) {
					throw new IllegalStateException(
							"Failed to find Java constructor for Kotlin primary constructor: " + clazz.getName());
				}
				return constructor;
			}
			catch (UnsupportedOperationException ex) {
				return null;
			}
		}

		/**
		 * Instantiate a Kotlin class using the provided constructor.
		 * @param ctor the constructor of the Kotlin class to instantiate
		 * @param args the constructor arguments to apply
		 * (use {@code null} for unspecified parameter if needed)
		 */
		public static <T> T instantiateClass(Constructor<T> ctor, Object... args)
				throws IllegalAccessException, InvocationTargetException, InstantiationException {

			KFunction<T> kotlinConstructor = ReflectJvmMapping.getKotlinFunction(ctor);
			if (kotlinConstructor == null) {
				return ctor.newInstance(args);
			}

			if ((!Modifier.isPublic(ctor.getModifiers()) || !Modifier.isPublic(ctor.getDeclaringClass().getModifiers()))) {
				KCallablesJvm.setAccessible(kotlinConstructor, true);
			}

			List<KParameter> parameters = kotlinConstructor.getParameters();
			Map<KParameter, Object> argParameters = new HashMap<>(parameters.size());
			Assert.isTrue(args.length <= parameters.size(),
					"Number of provided arguments should be less of equals than number of constructor parameters");
			for (int i = 0 ; i < args.length ; i++) {
				if (!(parameters.get(i).isOptional() && args[i] == null)) {
					argParameters.put(parameters.get(i), args[i]);
				}
			}
			return kotlinConstructor.callBy(argParameters);
		}

	}

}
