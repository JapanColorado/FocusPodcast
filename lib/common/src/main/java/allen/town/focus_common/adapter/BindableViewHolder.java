package allen.town.focus_common.adapter;

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;


public abstract class BindableViewHolder<T> extends RecyclerView.ViewHolder {
    public abstract void bindTo(T t);

    public BindableViewHolder(View view) {
        super(view);
    }
}
