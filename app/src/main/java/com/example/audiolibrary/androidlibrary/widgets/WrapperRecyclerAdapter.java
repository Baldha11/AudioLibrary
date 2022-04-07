package com.example.audiolibrary.androidlibrary.widgets;

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

public interface WrapperRecyclerAdapter<T extends RecyclerView.ViewHolder> {

    class ViewHolder extends RecyclerView.ViewHolder {
        public WrapperRecyclerAdapter adapter;

        public ViewHolder(View itemView) {
            super(itemView);
        }

        public int getAdapterPosition(RecyclerView.Adapter a) { // position may vary depends on who is calling
            int pos = getAdapterPosition();
            if (adapter != null && adapter != a) {
                RecyclerView.Adapter child = (RecyclerView.Adapter) adapter;
                while (child instanceof WrapperRecyclerAdapter) {
                    WrapperRecyclerAdapter parent = (WrapperRecyclerAdapter) child;
                    child = parent.getWrappedAdapter(); // child
                    if (child == a) {
                        pos = parent.getWrappedPosition(pos);
                    }
                }
            }
            return pos;
        }
    }

    RecyclerView.Adapter<T> getWrappedAdapter();

    int getWrappedPosition(int pos);

}
