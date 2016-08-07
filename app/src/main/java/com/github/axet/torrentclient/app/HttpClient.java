package com.github.axet.torrentclient.app;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;
import android.webkit.WebResourceResponse;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpCookie;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpHost;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.CookieStore;
import cz.msebera.android.httpclient.client.config.RequestConfig;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.client.methods.AbstractExecutionAwareRequest;
import cz.msebera.android.httpclient.client.methods.CloseableHttpResponse;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.client.methods.HttpRequestBase;
import cz.msebera.android.httpclient.client.protocol.HttpClientContext;
import cz.msebera.android.httpclient.cookie.Cookie;
import cz.msebera.android.httpclient.entity.ContentType;
import cz.msebera.android.httpclient.impl.client.BasicCookieStore;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.HttpClientBuilder;
import cz.msebera.android.httpclient.impl.client.LaxRedirectStrategy;
import cz.msebera.android.httpclient.impl.cookie.BasicClientCookie;
import cz.msebera.android.httpclient.message.BasicNameValuePair;
import cz.msebera.android.httpclient.util.EntityUtils;

// cz.msebera.android.httpclient recommended by apache
//
// https://hc.apache.org/httpcomponents-client-4.5.x/android-port.html

public class HttpClient {
    public static String USER_AGENT = "Mozilla/5.0 (Linux; Android 6.0.1; Nexus 5 Build/MOB30Y) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.81 Mobile Safari/537.36";

    CloseableHttpClient httpclient;
    HttpClientContext httpClientContext = HttpClientContext.create();
    AbstractExecutionAwareRequest request;
    RequestConfig config;

    public static HttpCookie from(Cookie c) {
        HttpCookie cookie = new HttpCookie(c.getName(), c.getValue());
        cookie.setDomain(c.getDomain());
        cookie.setPath(c.getPath());
        cookie.setSecure(c.isSecure());
        return cookie;
    }

    public static BasicClientCookie from(HttpCookie m) {
        BasicClientCookie b = new BasicClientCookie(m.getName(), m.getValue());
        b.setDomain(m.getDomain());
        b.setPath(m.getPath());
        b.setSecure(m.getSecure());
        return b;
    }

    public static List<NameValuePair> from(Map<String, String> map) {
        List<NameValuePair> nvps = new ArrayList<>();
        for (String key : map.keySet()) {
            String value = map.get(key);
            nvps.add(new BasicNameValuePair(key, value));
        }
        return nvps;
    }

    public static String encode(Map<String, String> map) {
        try {
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(from(map));
            InputStream is = entity.getContent();
            return IOUtils.toString(is, Charset.defaultCharset());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class DownloadResponse extends WebResourceResponse {
        public boolean downloaded;
        public String userAgent;
        public String contentDisposition;
        public long contentLength;
        byte[] buf;

        public static DownloadResponse create(CloseableHttpResponse response) throws IOException {
            HttpEntity entity = response.getEntity();
            ContentType contentType = ContentType.getOrDefault(entity);
            if (!directDownload(contentType.getMimeType())) {
                byte[] buf = IOUtils.toByteArray(entity.getContent());

                String encoding;
                Charset enc = contentType.getCharset();
                if (enc == null) {
                    Document doc = Jsoup.parse(new String(buf, Charset.defaultCharset()));
                    Element e = doc.select("meta[http-equiv=Content-Type]").first();
                    if (e != null) {
                        String content = e.attr("content");
                        contentType = ContentType.parse(content);
                        enc = contentType.getCharset();
                    } else {
                        e = doc.select("meta[charset]").first();
                        if (e != null) {
                            String content = e.attr("content");
                            contentType = ContentType.parse(content);
                            enc = contentType.getCharset();
                        }
                    }
                }
                encoding = Charsets.toCharset(enc).name();
                return new DownloadResponse(contentType.getMimeType(), encoding, buf);
            } else {
                Header ct = response.getFirstHeader("Content-Disposition");
                String ctValue = null;
                if (ct != null)
                    ctValue = ct.getValue();
                return new DownloadResponse(null, ctValue, contentType.getMimeType(), entity.getContentLength());
            }
        }

        static boolean directDownload(String mimetype) {
            String[] types = new String[]{"application/x-bittorrent", "audio", "video"};
            for (String t : types) {
                if (mimetype.startsWith(t))
                    return true;
            }
            return false;
        }

        public DownloadResponse(String userAgent, String contentDisposition, String mimetype, long contentLength) {
            super(mimetype, null, null);
            this.userAgent = userAgent;
            this.contentDisposition = contentDisposition;
            this.contentLength = contentLength;
            downloaded = false;
        }

        public DownloadResponse(String mimeType, String encoding, InputStream data) {
            super(mimeType, encoding, data);
            downloaded = true;
        }

        public DownloadResponse(String mimeType, String encoding, byte[] data) {
            super(mimeType, encoding, new ByteArrayInputStream(data));
            downloaded = true;
            this.buf = data;
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public DownloadResponse(String mimeType, String encoding, int statusCode, String reasonPhrase, Map<String, String> responseHeaders, InputStream data) {
            super(mimeType, encoding, statusCode, reasonPhrase, responseHeaders, data);
        }

        public String getHtml() {
            try {
                return new String(buf, getEncoding());
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        public void setHtml(String html) {
            try {
                buf = html.getBytes(getEncoding());
                setData(new ByteArrayInputStream(buf));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public HttpClient() {
        RequestConfig.Builder requestBuilder = RequestConfig.custom();
        requestBuilder = requestBuilder.setConnectTimeout(MainApplication.CONNECTION_TIMEOUT);
        requestBuilder = requestBuilder.setConnectionRequestTimeout(MainApplication.CONNECTION_TIMEOUT);

        this.httpclient = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestBuilder.build())
                .setRedirectStrategy(new LaxRedirectStrategy())
                .build();
    }

    public HttpClient(String cookies) {
        this();

        if (cookies != null && !cookies.isEmpty())
            addCookies(cookies);
    }

    public void setProxy(String host, int port, String scheme) {
        HttpHost proxy = new HttpHost(host, port, scheme);
        config = RequestConfig.custom().setProxy(proxy).build();
    }

    public void clearProxy() {
        config = null;
    }

    public void addCookies(String url, String cookies) {
        Uri u = Uri.parse(url);
        CookieStore s = getCookieStore();
        List<HttpCookie> cc = HttpCookie.parse(cookies);
        for (HttpCookie c : cc) {
            BasicClientCookie m = from(c);
            if (m.getDomain() == null) {
                m.setDomain(u.getAuthority());
            }
            removeCookie(m);
            s.addCookie(m);
        }
    }

    public void addCookies(String cookies) {
        CookieStore s = getCookieStore();
        List<HttpCookie> cc = HttpCookie.parse(cookies);
        for (HttpCookie c : cc) {
            BasicClientCookie m = from(c);
            removeCookie(m);
            s.addCookie(m);
        }
    }

    public void removeCookie(HttpCookie m) {
        removeCookie(from(m));
    }

    public void removeCookie(BasicClientCookie m) {
        try {
            CookieStore s = getCookieStore();
            BasicClientCookie rm = (BasicClientCookie) m.clone();
            rm.setExpiryDate(new Date(0));
            s.addCookie(rm);
        } catch (CloneNotSupportedException e) {
        }
    }

    public HttpCookie getCookie(int i) {
        CookieStore s = getCookieStore();
        Cookie c = s.getCookies().get(i);
        return from(c);
    }

    public void addCookie(HttpCookie c) {
        CookieStore s = getCookieStore();
        s.addCookie(from(c));
    }

    public int getCount() {
        CookieStore s = getCookieStore();
        return s.getCookies().size();
    }

    public String getCookies() {
        String str = "";

        CookieStore s = getCookieStore();
        List<Cookie> list = s.getCookies();
        for (int i = 0; i < list.size(); i++) {
            Cookie c = list.get(i);

            StringBuilder result = new StringBuilder()
                    .append(c.getName())
                    .append("=")
                    .append("\"")
                    .append(c.getValue())
                    .append("\"");
            appendAttribute(result, "path", c.getPath());
            appendAttribute(result, "domain", c.getDomain());

            if (!str.isEmpty())
                str += ", ";
            str += result.toString();
        }
        return str;
    }

    private void appendAttribute(StringBuilder builder, String name, String value) {
        if (value != null && builder != null) {
            builder.append(";");
            builder.append(name);
            builder.append("=\"");
            builder.append(value);
            builder.append("\"");
        }
    }

    public void clearCookies() {
        httpClientContext.setCookieStore(new BasicCookieStore());
    }

    public CookieStore getCookieStore() {
        CookieStore apacheStore = httpClientContext.getCookieStore();
        if (apacheStore == null) {
            apacheStore = new BasicCookieStore();
            httpClientContext.setCookieStore(apacheStore);
        }
        return apacheStore;
    }

    public void setCookieStore(CookieStore store) {
        httpClientContext.setCookieStore(store);
    }

    public AbstractExecutionAwareRequest getRequest() {
        return request;
    }

    public void abort() {
        request.abort();
        request = null;
    }

    CloseableHttpResponse execute(HttpRequestBase request) throws IOException {
        this.request = request;
        return httpclient.execute(request, httpClientContext);
    }

    public String get(String url) {
        try {
            HttpGet httpGet = new HttpGet(url);
            if (config != null)
                httpGet.setConfig(config);
            CloseableHttpResponse response = execute(httpGet);
            HttpEntity entity = response.getEntity();
            ContentType contentType = ContentType.getOrDefault(entity);
            String encoding = Charsets.toCharset(contentType.getCharset()).name();
            String html = IOUtils.toString(entity.getContent(), encoding);
            EntityUtils.consume(entity);
            response.close();
            return html;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            request = null;
        }
    }

    public byte[] getBytes(String url) {
        try {
            HttpGet httpGet = new HttpGet(url);
            if (config != null)
                httpGet.setConfig(config);
            CloseableHttpResponse response = execute(httpGet);
            HttpEntity entity = response.getEntity();
            byte[] buf = IOUtils.toByteArray(entity.getContent());
            EntityUtils.consume(entity);
            response.close();
            return buf;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            request = null;
        }
    }

    public DownloadResponse getResponse(String base, String url) {
        try {
            HttpGet httpGet = new HttpGet(url);
            if (config != null)
                httpGet.setConfig(config);
            if (base != null) {
                httpGet.addHeader("Referer", base);
                Uri u = Uri.parse(base);
                httpGet.addHeader("Origin", new Uri.Builder().scheme(u.getScheme()).authority(u.getAuthority()).toString());
                httpGet.addHeader("User-Agent", USER_AGENT);
            }
            CloseableHttpResponse response = execute(httpGet);
            HttpEntity entity = response.getEntity();
            DownloadResponse w = DownloadResponse.create(response);
            EntityUtils.consume(entity);
            response.close();
            return w;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            request = null;
        }
    }

    public String post(String url, String[][] map) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < map.length; i++) {
            m.put(map[i][0], map[i][1]);
        }
        return post(url, m);
    }

    public String post(String url, Map<String, String> map) {
        return post(url, from(map));
    }

    public String post(String url, List<NameValuePair> nvps) {
        try {
            HttpPost httpPost = new HttpPost(url);
            if (config != null)
                httpPost.setConfig(config);
            httpPost.setEntity(new UrlEncodedFormEntity(nvps));
            CloseableHttpResponse response = execute(httpPost);
            HttpEntity entity = response.getEntity();
            ContentType contentType = ContentType.getOrDefault(entity);
            String html = IOUtils.toString(entity.getContent(), contentType.getCharset());
            EntityUtils.consume(entity);
            response.close();
            return html;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            request = null;
        }
    }

    public DownloadResponse postResponse(String base, String url, Map<String, String> map) {
        return postResponse(base, url, from(map));
    }

    public DownloadResponse postResponse(String base, String url, List<NameValuePair> nvps) {
        try {
            HttpPost httpPost = new HttpPost(url);
            if (config != null)
                httpPost.setConfig(config);
            if (base != null) {
                httpPost.addHeader("Referer", base);
                Uri u = Uri.parse(base);
                httpPost.addHeader("Origin", new Uri.Builder().scheme(u.getScheme()).authority(u.getAuthority()).toString());
                httpPost.addHeader("User-Agent", USER_AGENT);
            }
            httpPost.setEntity(new UrlEncodedFormEntity(nvps));
            CloseableHttpResponse response = execute(httpPost);
            HttpEntity entity = response.getEntity();
            DownloadResponse w = DownloadResponse.create(response);
            EntityUtils.consume(entity);
            response.close();
            return w;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            request = null;
        }
    }
}
