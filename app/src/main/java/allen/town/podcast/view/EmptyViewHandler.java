package allen.town.podcast.view;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.Gravity;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ListAdapter;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import allen.town.podcast.R;

public class EmptyViewHandler {
    private boolean layoutAdded = false;
    private ListAdapter listAdapter;
    private RecyclerView.Adapter<?> recyclerAdapter;

    private final View emptyView;
    private final TextView tvTitle;
    private final TextView tvMessage;
    private final ImageView ivIcon;
    private final LinearLayout actionsContainer;

    public EmptyViewHandler(Context context) {
        emptyView = View.inflate(context, R.layout.empty_view_layout, null);
        tvTitle = emptyView.findViewById(R.id.emptyViewTitle);
        tvMessage = emptyView.findViewById(R.id.emptyViewMessage);
        ivIcon = emptyView.findViewById(R.id.emptyViewIcon);
        actionsContainer = emptyView.findViewById(R.id.emptyViewActions);
    }

    public void setTitle(int title) {
        tvTitle.setText(title);
    }

    public void setTitle(CharSequence title) {
        tvTitle.setText(title);
    }

    public void setMessage(int message) {
        tvMessage.setText(message);
    }

    public void setMessage(String message) {
        tvMessage.setText(message);
    }

    public void setIcon(@DrawableRes int icon) {
        ivIcon.setImageResource(icon);
        ivIcon.setVisibility(View.VISIBLE);
    }

    /**
     * Adds a button below the message. Actions stack vertically in the order they were added.
     */
    public void addAction(@StringRes int label, @DrawableRes int icon, View.OnClickListener listener) {
        MaterialButton button = (MaterialButton) LayoutInflater.from(emptyView.getContext())
                .inflate(R.layout.empty_view_action, actionsContainer, false);
        button.setText(label);
        button.setIconResource(icon);
        button.setOnClickListener(listener);
        actionsContainer.addView(button);
        actionsContainer.setVisibility(View.VISIBLE);
    }

    /** Removes every button added through {@link #addAction}. */
    public void clearActions() {
        actionsContainer.removeAllViews();
        actionsContainer.setVisibility(View.GONE);
    }

    public void hide() {
        emptyView.setVisibility(View.GONE);
    }

    public void attachToListView(AbsListView listView) {
        if (layoutAdded) {
            throw new IllegalStateException("Can not attach EmptyView multiple times");
        }
        addToParentView(listView);
        layoutAdded = true;
        listView.setEmptyView(emptyView);
        updateAdapter(listView.getAdapter());
    }

    public void attachToRecyclerView(RecyclerView recyclerView) {
        if (layoutAdded) {
            throw new IllegalStateException("Can not attach EmptyView multiple times");
        }
        addToParentView(recyclerView);
        layoutAdded = true;
        updateAdapter(recyclerView.getAdapter());
    }

    /**
     * Places the empty view inside a container the caller owns and shows or hides itself
     * (for screens where walking up from the list would land in the wrong parent). The caller
     * decides when the container is visible; the empty view itself stays visible.
     */
    public void attachTo(ViewGroup container) {
        if (layoutAdded) {
            throw new IllegalStateException("Can not attach EmptyView multiple times");
        }
        container.addView(emptyView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        layoutAdded = true;
        emptyView.setVisibility(View.VISIBLE);
    }

    private void addToParentView(View view) {
        ViewGroup parent = ((ViewGroup) view.getParent());
        while (parent != null) {
            if (parent instanceof RelativeLayout) {
                parent.addView(emptyView);
                RelativeLayout.LayoutParams layoutParams =
                        (RelativeLayout.LayoutParams) emptyView.getLayoutParams();
                layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
                emptyView.setLayoutParams(layoutParams);
                break;
            } else if (parent instanceof FrameLayout) {
                parent.addView(emptyView);
                FrameLayout.LayoutParams layoutParams =
                        (FrameLayout.LayoutParams) emptyView.getLayoutParams();
                layoutParams.gravity = Gravity.CENTER;
                emptyView.setLayoutParams(layoutParams);
                break;
            }
            parent = (ViewGroup) parent.getParent();
        }
    }

    public void updateAdapter(RecyclerView.Adapter<?> adapter) {
        if (this.recyclerAdapter != null) {
            this.recyclerAdapter.unregisterAdapterDataObserver(adapterObserver);
        }
        this.recyclerAdapter = adapter;
        if (adapter != null) {
            adapter.registerAdapterDataObserver(adapterObserver);
        }
        updateVisibility();
    }

    private void updateAdapter(ListAdapter adapter) {
        if (this.listAdapter != null) {
            this.listAdapter.unregisterDataSetObserver(listAdapterObserver);
        }
        this.listAdapter = adapter;
        if (adapter != null) {
            adapter.registerDataSetObserver(listAdapterObserver);
        }
        updateVisibility();
    }

    private final SimpleAdapterDataObserver adapterObserver = new SimpleAdapterDataObserver() {
        @Override
        public void anythingChanged() {
            updateVisibility();
        }
    };

    private final DataSetObserver listAdapterObserver = new DataSetObserver() {
        public void onChanged() {
            updateVisibility();
        }
    };

    public void updateVisibility() {
        boolean empty;
        if (recyclerAdapter != null) {
            empty = recyclerAdapter.getItemCount() == 0;
        } else if (listAdapter != null) {
            empty = listAdapter.isEmpty();
        } else {
            empty = true;
        }
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }
}
