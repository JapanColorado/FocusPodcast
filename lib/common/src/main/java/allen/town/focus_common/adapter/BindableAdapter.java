package allen.town.focus_common.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

public abstract class BindableAdapter<T> extends BaseAdapter {
    private final Context context;
    private final LayoutInflater inflater;

    public abstract void bindView(T t, int i, View view);

    public abstract T getItem(int i);

    public abstract View newView(LayoutInflater layoutInflater, int i, ViewGroup viewGroup);

    public BindableAdapter(Context context2) {
        this.context = context2;
        this.inflater = LayoutInflater.from(context2);
    }

    public Context getContext() {
        return this.context;
    }

    public final View getView(int i, View view, ViewGroup viewGroup) {
        if (view == null && (view = newView(this.inflater, i, viewGroup)) == null) {
            throw new IllegalStateException("newView result must not be null.");
        }
        bindView(getItem(i), i, view);
        return view;
    }

    public final View getDropDownView(int i, View view, ViewGroup viewGroup) {
        if (view == null && (view = newDropDownView(this.inflater, i, viewGroup)) == null) {
            throw new IllegalStateException("newDropDownView result must not be null.");
        }
        bindDropDownView(getItem(i), i, view);
        return view;
    }

    public View newDropDownView(LayoutInflater layoutInflater, int i, ViewGroup viewGroup) {
        return newView(layoutInflater, i, viewGroup);
    }

    public void bindDropDownView(T t, int i, View view) {
        bindView(t, i, view);
    }
}
