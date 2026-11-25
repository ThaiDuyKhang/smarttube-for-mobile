package com.liskovsoft.smartyoutubetv2.tv.ui.mod.fragments;

import android.content.res.Configuration;
import android.view.View;

import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.VerticalGridPresenter;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.GridFragmentHelper;

/**
 * Mobile build: the grids are laid out for the screen width. Lay them out again after the screen rotation.
 */
final class GridRelayoutHelper {
    private int mOrientation = Configuration.ORIENTATION_UNDEFINED;

    /**
     * Remember the orientation the grid is created for
     */
    void init(View view) {
        if (view != null) {
            mOrientation = view.getResources().getConfiguration().orientation;
        }
    }

    /**
     * @return true if the grid should be laid out again
     */
    boolean isOrientationChanged(View view, Configuration newConfig) {
        if (view == null || newConfig == null || !Utils.isMobileBuild(view.getContext())) {
            return false;
        }

        boolean changed = mOrientation != Configuration.ORIENTATION_UNDEFINED && newConfig.orientation != mOrientation;
        mOrientation = newConfig.orientation;

        return changed;
    }

    /**
     * Apply the new number of columns and re-create the cards (their size depends on the screen width)
     *
     * @param numColumns new number of columns or -1 to keep the current one
     */
    static void relayout(VerticalGridPresenter presenter, VerticalGridPresenter.ViewHolder viewHolder, ObjectAdapter adapter, int numColumns) {
        if (presenter == null || viewHolder == null || adapter == null) {
            return;
        }

        GridFragmentHelper.invalidate();

        VerticalGridView gridView = viewHolder.getGridView();
        int position = gridView.getSelectedPosition();

        if (numColumns > 0) {
            presenter.setNumberOfColumns(numColumns);
            gridView.setNumColumns(numColumns);
        }

        // Drop the old cards (the card presenters calculate the size when the card is created)
        gridView.setAdapter(null);
        gridView.getRecycledViewPool().clear();
        presenter.onBindViewHolder(viewHolder, adapter);

        if (position != RecyclerView.NO_POSITION && position < adapter.size()) {
            gridView.setSelectedPosition(position);
        }
    }
}
