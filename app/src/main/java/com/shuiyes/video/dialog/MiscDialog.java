package com.shuiyes.video.dialog;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;

import com.shuiyes.video.bean.PlayVideo;
import com.shuiyes.video.widget.MiscView;
import com.zhy.view.flowlayout.FlowLayout;
import com.zhy.view.flowlayout.TagAdapter;
import com.zhy.view.flowlayout.TagView;

import java.util.List;

public class MiscDialog<T extends PlayVideo> extends FlowlayoutDialog {

    private List<T> mUrlList;

    public MiscDialog(Context context, List<T> urls) {
        super(context);
        mUrlList = urls;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mView.setAdapter(new TagAdapter<T>(mUrlList) {
            @Override
            public TagView getView(FlowLayout parent, int position, T t) {
                MiscView view = new MiscView(getContext(), t);
                view.setTextColor(Color.WHITE);
                view.setOnClickListener(mListener);
                view.setSize(view.measureWidth(), MiscView.WH);
                return view;
            }
        });
    }
}