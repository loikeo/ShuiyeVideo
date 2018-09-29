package com.shuiyes.video.qq;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.shuiyes.video.R;
import com.shuiyes.video.base.BasePlayActivity;
import com.shuiyes.video.bean.ListVideo;
import com.shuiyes.video.bean.PlayVideo;
import com.shuiyes.video.util.HttpUtils;
import com.shuiyes.video.util.Utils;

import org.json.JSONArray;
import org.json.JSONObject;

public class QQVActivity extends BasePlayActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBatName = "腾讯视频";

        mClarityView.setOnClickListener(this);
        mSelectView.setOnClickListener(this);
        mNextView.setOnClickListener(this);

        playVideo();
    }
    public String mHost;
    public String mFvkey;
    public String mFn_pre;

    @Override
    protected void playVideo() {
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {

                    mHandler.sendEmptyMessage(MSG_FETCH_VIDEOINFO);
                    String html = HttpUtils.open(mIntentUrl);

                    if (TextUtils.isEmpty(html)) {
                        fault("请重试");
                        return;
                    }

                    Utils.setFile("qq.html", html);

                    String key = "<link rel=\"canonical\" href=\"";
                    if (html.contains(key)) {
                        int len = html.indexOf(key);
                        String tmp = html.substring(len + key.length());
                        len = tmp.indexOf("\"");
                        mIntentUrl = tmp.substring(0, len);

                        Log.e(TAG, "rurl=" + mIntentUrl);
                    }

                    key = "<title>";
                    if (html.contains(key)) {
                        int len = html.indexOf(key);
                        String tmp = html.substring(len + key.length());
                        len = tmp.indexOf("</title>");
                        String title = tmp.substring(0, len);

                        mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TITLE, title));
                    }

                    key = "/";
                    int index = mIntentUrl.lastIndexOf(key);
                    if (mIntentUrl.indexOf(".html") != -1) {
                        mVid = mIntentUrl.substring(index + key.length(), mIntentUrl.indexOf(".html"));
                    } else {
                        mVid = mIntentUrl.substring(index + key.length());
                    }
                    Log.e(TAG, "play mVid=" + mVid);

                    mHandler.sendEmptyMessage(MSG_FETCH_TOKEN);
                    String video = null;
                    for (String platform : QQUtils.PLATFORMS) {
                        html = QQUtils.fetchVideo(platform, mVid);

                        if (TextUtils.isEmpty(html)) {
                            fault("请重试");
                            return;
                        }

                        Utils.setFile("qq", html);

                        video = QQUtils.formatJson(html);

                        JSONObject obj = new JSONObject(video);
                        if (obj.has("msg")) {
                            String msg = obj.getString("msg");
                            if (!"cannot play outside".equals(msg) || QQUtils.PLATFORMS[QQUtils.PLATFORMS.length - 1].equals(platform)) {
                                fault(msg);
                                return;
                            } else {
                                continue;
                            }
                        }
                    }


                    JSONObject obj = new JSONObject(video);
                    JSONObject vl = obj.getJSONObject("vl");
                    JSONArray vis = vl.getJSONArray("vi");
                    JSONObject vi = (JSONObject) vis.get(0);

                    String title = vi.getString("ti");
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TITLE, title));

                    mFvkey = vi.getString("fvkey");

                    mFn_pre = vi.getString("lnk");

                    JSONArray uis = vi.getJSONObject("ul").getJSONArray("ui");
                    JSONObject ui = (JSONObject) uis.get(0);
                    mHost = ui.getString("url");

                    JSONObject cl = vi.getJSONObject("cl");
                    int fc_cnt = cl.getInt("fc");

                    String filename = vi.getString("fn");

                    String magic_str = "";
                    String video_type = "";
                    int seg_cnt = fc_cnt;
                    if (seg_cnt == 0) {
                        seg_cnt = 1;
                    } else {
                        String[] fns = filename.split("\\.");
                        mFn_pre = fns[0];
                        magic_str = fns[1];
                        video_type = fns[2];
                    }

                    mSectionList.clear();
                    for (int part = 1; part < seg_cnt + 1; part++) {

                        String part_format_id = null;
                        if (fc_cnt == 0) {
                            String[] keyids = cl.getString("keyid").split("\\.");
                            part_format_id = keyids[keyids.length - 1];
                        } else {
                            JSONArray cis = cl.getJSONArray("ci");
                            JSONObject ci = (JSONObject) cis.get(part - 1);
                            part_format_id = ci.getString("keyid").split("\\.")[1];
                            filename = String.format("%s.%s.%s.%s", mFn_pre, magic_str, part, video_type);
                        }

                        mSectionList.add(new ListVideo(part_format_id, mVid, filename));
                        if(part > 1){
                            continue;
                        }

                        mSectionIndex = 0;
                        playSection(part_format_id, mVid, filename);
                    }

                } catch (Exception e) {
                    fault(e);
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_clarity:
                break;
        }
    }

    private void playSection(String part_format_id, String vid, String filename) throws Exception {
        mHandler.sendEmptyMessage(MSG_FETCH_VIDEO);
        String part_info = QQUtils.fetchUrl(part_format_id, vid, filename);
        Utils.setFile("qq", part_info);
        String urlInfo = QQUtils.formatJson(part_info);

        JSONObject obj = new JSONObject(urlInfo);

        if (obj.has("msg")) {
            if("not pay".equals(obj.getString("msg"))){
                fault("VIP 章节暂不支持试看");
            }else{
                fault(obj.getString("msg"));
            }
            return;
        }

        String url = "";
        if (obj.has("key")) {
            String vkey = obj.getString("key");
            url = String.format("%s%s?vkey=%s", mHost, filename, vkey);
        } else {
            String vkey = mFvkey;
            url = String.format("%s%s?vkey=%s", mHost, mFn_pre+".mp4", vkey);
        }

        mCurrentPosition = 0;
        mHandler.sendMessage(mHandler.obtainMessage(MSG_CACHE_URL, url));
    }

    @Override
    protected void playNextSection(final int index) {
        if(mStateView.getText().length() == 0){
            mStateView.setText("缓存第"+(index+1)+"章节...");
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                ListVideo v = mSectionList.get(index);
                try {
                    playSection(v.getId(), v.getTitle(), v.getUrl());
                } catch (Exception e) {
                    fault(e);
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    protected void cacheVideo(PlayVideo video) {
    }

}
