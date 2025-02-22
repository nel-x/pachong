package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttpUtil;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Alist extends Spider {

    private Map<String, String> map;
    private JSONObject ext;

    private boolean isJson(String json) {
        try {
            JsonParser.parseString(json);
            return true;
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    @Override
    public void init(Context context, String extend) {
        try {
            map = new HashMap<>();
            ext = new JSONObject();
            if (extend.startsWith("http")) extend = OkHttpUtil.string(extend);
            if (isJson(extend)) parseJson(extend);
            else parseText(extend);
        } catch (Exception ignored) {
        }
    }

    private void parseJson(String extend) throws Exception {
        JSONObject object = new JSONObject(extend);
        JSONArray array = object.names();
        for (int i = 0; i < array.length(); i++) {
            String key = array.getString(i);
            ext.put(key, object.getString(key));
        }
    }

    private void parseText(String extend) throws Exception {
        String[] array = extend.split("#");
        for (String text : array) {
            String[] arr = text.split("\\$");
            if (arr.length == 1) {
                ext.put("alist", arr[0]);
            } else if (arr.length == 2) {
                ext.put(arr[0], arr[1]);
            }
        }
    }

    private String getVersion(String name) throws Exception {
        if (!map.containsKey(name)) map.put(name, OkHttpUtil.string(ext.getString(name) + "/api/public/settings").contains("v3.") ? "3" : "2");
        return map.get(name);
    }

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();
        Iterator<String> keys = this.ext.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            classes.add(new Class(key + "$/", key, "1"));
        }
        return Result.string(classes, Collections.emptyList());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int index = tid.indexOf('$');
        String name = tid.substring(0, index);
        String path = tid.substring(index + 1);
        boolean v3 = getVersion(name).equals("3");
        String url = ext.getString(name) + (v3 ? "/api/fs/list" : "/api/public/path");
        JSONObject params = new JSONObject();
        params.put("path", path);
        String response = OkHttpUtil.postJson(url, params.toString());
        JSONArray array = new JSONObject(response).getJSONObject("data").getJSONArray(v3 ? "content" : "files");
        List<Vod> list = new ArrayList<>();
        for (int i = 0; i < array.length(); ++i) {
            JSONObject o = array.getJSONObject(i);
            String pic = o.getString(v3 ? "thumb" : "thumbnail");
            boolean folder = o.getInt("type") == 1;
            if (pic.isEmpty() && folder) pic = "http://img1.3png.com/281e284a670865a71d91515866552b5f172b.png";
            Vod vod = new Vod();
            vod.setVodId(tid + (tid.charAt(tid.length() - 1) == '/' ? "" : "/") + o.getString("name"));
            vod.setVodName(o.getString("name"));
            vod.setVodPic(pic);
            vod.setVodTag(folder ? "folder" : "file");
            String size = getSize(o.getLong("size"));
            vod.setVodRemarks(folder ? size + " 文件夹" : size);
            list.add(vod);
        }
        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String tid = ids.get(0);
        int index = tid.indexOf('$');
        String name = tid.substring(0, index);
        String path = tid.substring(index + 1);
        boolean v3 = getVersion(name).equals("3");
        String url = ext.getString(name) + (v3 ? "/api/fs/get" : "/api/public/path");
        JSONObject params = new JSONObject();
        params.put("path", path);
        String response = OkHttpUtil.postJson(url, params.toString());
        JSONObject data = v3 ? new JSONObject(response).getJSONObject("data") : new JSONObject(response).getJSONObject("data").getJSONArray("files").getJSONObject(0);
        url = data.getString(v3 ? "raw_url" : "url");
        if (url.indexOf("//") == 0) url = "http:" + url;
        Vod vod = new Vod();
        vod.setVodId(tid + "/" + data.getString("name"));
        vod.setVodName(data.getString("name"));
        vod.setVodPic(data.getString(v3 ? "thumb" : "thumbnail"));
        vod.setVodTag(data.getInt("type") == 1 ? "folder" : "file");
        vod.setVodPlayFrom("播放");
        vod.setVodPlayUrl(data.getString("name") + "$" + url);
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return Result.get().url(id).string();
    }

    private String getSize(double size) {
        if (size == 0) return "";
        if (size > 1024 * 1024 * 1024 * 1024.0) {
            size /= (1024 * 1024 * 1024 * 1024.0);
            return String.format(Locale.getDefault(), "%.2f%s", size, "TB");
        } else if (size > 1024 * 1024 * 1024.0) {
            size /= (1024 * 1024 * 1024.0);
            return String.format(Locale.getDefault(), "%.2f%s", size, "GB");
        } else if (size > 1024 * 1024.0) {
            size /= (1024 * 1024.0);
            return String.format(Locale.getDefault(), "%.2f%s", size, "MB");
        } else {
            size /= 1024.0;
            return String.format(Locale.getDefault(), "%.2f%s", size, "KB");
        }
    }
}