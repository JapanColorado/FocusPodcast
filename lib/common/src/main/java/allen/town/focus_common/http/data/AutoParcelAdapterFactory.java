package allen.town.focus_common.http.data;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;

import kotlin.text.Typography;

public final class AutoParcelAdapterFactory implements TypeAdapterFactory {
    @Override // com.google.gson.TypeAdapterFactory
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
        Class<? super T> rawType = typeToken.getRawType();
        if (!rawType.isAnnotationPresent(AutoGson.class)) {
            return null;
        }
        String name = rawType.getPackage().getName();
        String str = name + ".AutoParcel_" + rawType.getName().substring(name.length() + 1).replace(Typography.dollar, '_');
        try {
            return (TypeAdapter<T>) gson.getAdapter(Class.forName(str));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not load AutoValue type " + str, e);
        }
    }
}
