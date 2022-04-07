package com.example.audiolibrary.androidlibrary.widgets;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

public class HeaderRecyclerView extends RecyclerView {
    public HeaderRecyclerView(Context context) {
        super(context);
    }

    public HeaderRecyclerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public HeaderRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void setLayoutManager(LayoutManager layout) {
        super.setLayoutManager(layout);
        Adapter a = getAdapter();
        while (a instanceof WrapperRecyclerAdapter) {
            if (a instanceof HeaderRecyclerAdapter)
                ((HeaderRecyclerAdapter) a).updateGridHeaderFooter(layout);
            a = ((WrapperRecyclerAdapter) a).getWrappedAdapter();
        }
    }
}
