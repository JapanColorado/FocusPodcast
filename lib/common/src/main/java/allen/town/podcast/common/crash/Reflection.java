package allen.town.podcast.common.crash;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Reflection helpers for the crash handler. Every lookup here targets private platform internals
 * that may simply not exist on a given ROM, so failure is an expected outcome rather than an
 * error: each method logs and answers with its documented "not available" value (null / false),
 * and the callers in {@link CustomCrashHandler} branch on that.
 */
public abstract class Reflection {
    private static final String THIS_FILE = "Reflection";
    @SuppressWarnings("unchecked")
    public static <T> T getStaticFieldValue(final Class<?> klass, final String name) {
        if (null != klass && null != name) {
            try {
                final Field field = getField(klass, name);
                if (null != field) {
                    field.setAccessible(true);
                    return (T) field.get(klass);
                }
            } catch (final Throwable t) {
                // Field missing on this ROM: answer null, see the class comment.
                Log.w(THIS_FILE, "get field " + name + " of " + klass + " error", t);
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getFieldValue(final Object obj, final String name) {
        if (null != obj && null != name) {
            try {
                final Field field = getField(obj.getClass(), name);
                if (null != field) {
                    field.setAccessible(true);
                    return (T) field.get(obj);
                }
            } catch (final Throwable t) {
                // Field missing on this ROM: answer null, see the class comment.
                Log.w(THIS_FILE, "get field " + name + " of " + obj + " error", t);
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getFieldValue(final Object obj, final Class<?> type) {
        if (null != obj && null != type) {
            try {
                final Field field = getField(obj.getClass(), type);
                if (null != field) {
                    field.setAccessible(true);
                    return (T) field.get(obj);
                }
            } catch (final Throwable t) {
                // Field missing on this ROM: answer null, see the class comment.
                Log.w(THIS_FILE, "get field with type " + type + " of " + obj + " error", t);
            }
        }

        return null;
    }

    public static boolean setFieldValue(final Object obj, final String name, final Object value) {
        if (null != obj && null != name) {
            try {
                final Field field = getField(obj.getClass(), name);
                if (null != field) {
                    field.setAccessible(true);
                    field.set(obj, value);
                    return true;
                }
            } catch (final Throwable t) {
                // Field missing or final on this ROM: answer false, see the class comment.
                Log.w(THIS_FILE, "set field " + name + " of " + obj + " error", t);
            }
        }

        return false;
    }


    @SuppressWarnings("unchecked")
    public static <T> T invokeMethod(final Object obj, final String name) {
        return invokeMethod(obj, name, new Class[0], new Object[0]);
    }

    @SuppressWarnings("unchecked")
    public static <T> T invokeMethod(final Object obj, final String name, final Class[] types, final Object[] args) {
        if (null != obj && null != name && null != types && null != args && types.length == args.length) {
            try {
                final Method method = getMethod(obj.getClass(), name, types);
                if (null != method) {
                    method.setAccessible(true);
                    return (T) method.invoke(obj, args);
                }
            } catch (final Throwable e) {
                // Method missing or threw on this ROM: answer null, see the class comment.
                Log.w(THIS_FILE, "Invoke " + name + "(" + Arrays.toString(types) + ") of " + obj + " error", e);
            }
        }

        return null;
    }

    public static Field getField(final Class<?> klass, final String name) {
        try {
            return klass.getDeclaredField(name);
        } catch (final NoSuchFieldException e) {
            // Not declared here: keep walking up the hierarchy, null once we run out of parents.
            final Class<?> parent = klass.getSuperclass();
            if (null == parent) {
                return null;
            }
            return getField(parent, name);
        }
    }

    public static Field getField(final Class<?> klass, final Class<?> type) {
        final Field[] fields = klass.getDeclaredFields();
        if (fields.length <= 0) {
            final Class<?> parent = klass.getSuperclass();
            if (null == parent) {
                return null;
            }
            return getField(parent, type);
        }

        for (final Field field : fields) {
            if (field.getType() == type) {
                return field;
            }
        }

        return null;
    }

    private static Method getMethod(final Class<?> klass, final String name, final Class<?>[] types) {
        try {
            return klass.getDeclaredMethod(name, types);
        } catch (final NoSuchMethodException e) {
            // Not declared here: keep walking up the hierarchy, null once we run out of parents.
            final Class<?> parent = klass.getSuperclass();
            if (null == parent) {
                return null;
            }
            return getMethod(parent, name, types);
        }
    }

    private Reflection() {
    }

}
