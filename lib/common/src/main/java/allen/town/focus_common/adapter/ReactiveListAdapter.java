package allen.town.focus_common.adapter;

import android.content.Context;
import android.view.LayoutInflater;

import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;


public abstract class ReactiveListAdapter<T, VH extends BindableViewHolder<T>> extends RecyclerView.Adapter<VH> {
    private List<T> items = Collections.emptyList();
    private LayoutInflater layoutInflater;

    protected ReactiveListAdapter(Context context) {
        this.layoutInflater = LayoutInflater.from(context);
    }


    public LayoutInflater getLayoutInflater() {
        return this.layoutInflater;
    }

    public void call(List<T> list) {
        this.items = list;
        notifyDataSetChanged();
    }

    public void onBindViewHolder(VH vh, int i) {
        vh.bindTo(this.items.get(i));
    }

    @Override // android.support.v7.widget.RecyclerView.Adapter
    public int getItemCount() {
        return this.items.size();
    }
}
