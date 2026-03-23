package com.xapps.media.xmusic.fragment;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.recyclerview.widget.RecyclerView.ItemDecoration;
import androidx.recyclerview.widget.RecyclerView.State;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.xapps.media.xmusic.R;
import com.xapps.media.xmusic.activity.MainActivity;
import com.xapps.media.xmusic.common.SettingsItem;
import com.xapps.media.xmusic.data.DataManager;
import com.xapps.media.xmusic.databinding.*;
import com.xapps.media.xmusic.utils.XUtils;

import java.util.List;

public abstract class BasePrefsFragment extends BaseFragment {

    private MainActivity activity;
    private ItemsListAdapter adapter;
    private FragmentBasePrefsBinding binding;
    protected abstract List<SettingsItem> provideItems();

    private void initialize() {
        binding.recyclerView.addItemDecoration(new SpacingDecoration(getActivity()));
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        adapter = new ItemsListAdapter(this, getActivity(), provideItems());
        binding.recyclerView.setAdapter(adapter);
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentBasePrefsBinding.inflate(inflater, container, false);
        activity = (MainActivity) getActivity();
        initialize();
        return binding.getRoot();
    }

    static class ItemsListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final Drawable top;
        private final Drawable middle;
        private final Drawable bottom;
        private final Drawable single;
        private final List<SettingsItem> data;
        private BasePrefsFragment frag;

        public ItemsListAdapter(BasePrefsFragment fragment, Context context, List<SettingsItem> data) {
            setHasStableIds(true);
            frag = fragment;
            this.single = ContextCompat.getDrawable(context, R.drawable.rv_ripple_single);
            this.top = ContextCompat.getDrawable(context, R.drawable.rv_ripple_top);
            this.middle = ContextCompat.getDrawable(context, R.drawable.rv_ripple);
            this.bottom = ContextCompat.getDrawable(context, R.drawable.rv_ripple_bottom);
            this.data = data;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater i = LayoutInflater.from(parent.getContext());
            if (viewType == SettingsItem.TYPE_HEADER) {
                return new HeaderVH(PrefsHeaderLayoutBinding.inflate(i, parent, false));
            }
            if (viewType == SettingsItem.TYPE_SWITCH) {
                return new SwitchVH(PrefsSwitchLayoutBinding.inflate(i, parent, false));
            }

            return new RedirectVH(PrefsRedirectLayoutBinding.inflate(i, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            SettingsItem item = data.get(position);
            if (isGroupItem(position)) {
                boolean hasPrev = hasPreviousGroupItem(position);
                boolean hasNext = hasNextGroupItem(position);

                Drawable bg;
                if (!hasPrev && !hasNext) {
                    bg = single.getConstantState().newDrawable().mutate();
                } else if (!hasPrev) {
                    bg = top.getConstantState().newDrawable().mutate();
                } else if (!hasNext) {
                    bg = bottom.getConstantState().newDrawable().mutate();
                } else {
                    bg = middle.getConstantState().newDrawable().mutate();
                }

                holder.itemView.setBackground(bg);
            } else {
                holder.itemView.setBackground(null);
            }

            if (holder instanceof HeaderVH) {
                ((HeaderVH) holder).bind(item);
            } else if (holder instanceof SwitchVH) {
                ((SwitchVH) holder).bind(frag, item);
            } else if (holder instanceof RedirectVH) {
                ((RedirectVH) holder).bind(frag, item);
            }
        }

        @Override
        public int getItemCount() {
            return data == null ? 0 : data.size();
        }
        
        @Override
        public int getItemViewType(int position) {
            return data.get(position).type;
        }
        
        @Override
        public long getItemId(int position) {
            return (data.get(position).title + data.get(position).description).hashCode();
        }
        
        private boolean hasPreviousGroupItem(int position) {
            for (int i = position - 1; i >= 0; i--) {
                if (isGroupItem(i)) return true;
                if (getItemViewType(i) == SettingsItem.TYPE_HEADER) break;
            }
            return false;
        }

        private boolean hasNextGroupItem(int position) {
            for (int i = position + 1; i < getItemCount(); i++) {
                if (isGroupItem(i)) return true;
                if (getItemViewType(i) == SettingsItem.TYPE_HEADER) break;
            }
            return false;
        }
        
        private boolean isGroupItem(int position) {
            int type = getItemViewType(position);
            return type == SettingsItem.TYPE_SWITCH || type == SettingsItem.TYPE_NAV;
        }
        
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        PrefsHeaderLayoutBinding binding;
        HeaderVH(PrefsHeaderLayoutBinding b) {
            super(b.getRoot());
            binding = b;
        }
        void bind(SettingsItem item) {
            binding.header.setText(item.title);
        }
    }
    
    static class RedirectVH extends RecyclerView.ViewHolder {
        PrefsRedirectLayoutBinding binding;
        RedirectVH(PrefsRedirectLayoutBinding b) {
            super(b.getRoot());
            binding = b;
        }
        void bind(BasePrefsFragment host, SettingsItem item) {
            binding.title.setText(item.title);
            binding.title.setTypeface(binding.title.getTypeface(), Typeface.BOLD);
            binding.description.setText(item.description);
            binding.item.setOnClickListener(v -> {
                host.onNavigate(item);
            });
        }
    }
    
    static class SwitchVH extends RecyclerView.ViewHolder {
        PrefsSwitchLayoutBinding binding;

        SwitchVH(PrefsSwitchLayoutBinding b) {
            super(b.getRoot());
            binding = b;
        }

        void bind(BasePrefsFragment host, SettingsItem item) {
            binding.prefSwitch.setChecked(DataManager.sp.getBoolean(item.id, false));
            binding.prefSwitch.setOnCheckedChangeListener(null);
            binding.prefTitle.setText(item.title);
            binding.prefTitle.setTypeface(binding.prefTitle.getTypeface(), Typeface.BOLD);
            binding.prefDescription.setText(item.description);
            binding.prefItem.setOnClickListener(v -> {
                binding.prefSwitch.setChecked(!binding.prefSwitch.isChecked());
            });
            binding.prefSwitch.setOnCheckedChangeListener((v, isChecked) -> {
                host.onSwitchChanged(item, isChecked);
            });
        }
    }
    
    protected void onNavigate(SettingsItem item) {}
    
    protected void onSwitchChanged(SettingsItem item, boolean value) {
        switch (item.id) {
            case "stable_colors" :
                DataManager.setStableColors(value);
            break;
            default :
            break;
        }
    }

    public static class SpacingDecoration extends ItemDecoration {

        private final int topSpacing;
        private final int bottomSpacing;
        private final int spacing;

        public SpacingDecoration(Context context) {
            topSpacing = XUtils.convertToPx(context, 12f);
            bottomSpacing = XUtils.convertToPx(context, 12f);
            spacing = XUtils.convertToPx(context, 2f);
        }

        @Override
        public void getItemOffsets(
                @NonNull Rect outRect,
                @NonNull View view,
                @NonNull RecyclerView parent,
                @NonNull State state
        ) {
            int position = parent.getChildAdapterPosition(view);
            if (position == RecyclerView.NO_POSITION) return;

            if (position == 0) {
                outRect.set(0, topSpacing, 0, spacing);
            } else if (position == state.getItemCount() - 1) {
                outRect.set(0, 0, 0, bottomSpacing);
            } else {
                outRect.set(0, 0, 0, spacing);
            }
        }
    }

    public FragmentBasePrefsBinding getBinding() {
        return binding;
    }
}